package brig.ck8s.cli.executor.remote;

import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.concord.ConcordProcess;
import brig.ck8s.cli.executor.FlowExecutor;
import brig.ck8s.cli.model.ConcordProfile;
import brig.ck8s.cli.utils.LogUtils;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RemoteFlowExecutor
        implements FlowExecutor
{

    private final ConcordProfile concordCfg;
    private final ApiClient apiClient;
    private final boolean testMode;

    public RemoteFlowExecutor(ConcordProfile concordCfg, boolean testMode)
    {
        this.concordCfg = concordCfg;
        this.apiClient = createClient(concordCfg);
        this.testMode = testMode;
    }

    private static ApiClient createClient(ConcordProfile cfg)
    {
        if (cfg.apiKey() == null) {
            throw new RuntimeException("Can't create concord client for '" + cfg.alias() + "': api key is empty");
        }

        return new ConcordApiClient(cfg.baseUrl())
                .setVerifyingSsl(false)
                .setApiKey(cfg.apiKey());
    }


    private static void archiveToFile(Path src, Path dest)
    {
        try {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                IOUtils.zip(zip, src);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int execute(Ck8sPayload payload)
    {
        if (testMode) {
            StringBuilder args = new StringBuilder();
            for (Map.Entry<String, Object> e : payload.args().entrySet()) {
                args.append(String.format("-F arguments.%s=%s ", e.getKey(), e.getValue()));
            }
            Path archivePath = payload.location().resolve("payload.zip");
            archiveToFile(payload.location(), archivePath);

            String curl = String.format("curl -s --http1.1 -H 'Authorization: %s' -F archive=@%s %s %s/api/v1/process",
                    concordCfg.apiKey(), archivePath, args, concordCfg.baseUrl());

            LogUtils.info("Test mode is on. Use this command to start your process:\n{}", curl);
            return 0;
        }

        try {
            ConcordProcess process = startRemoteProcess(payload);
            LogUtils.info("process: {}", String.format("%s/#/process/%s/log", concordCfg.baseUrl(), process.instanceId()));
        }
        catch (Exception e) {
            throw new RuntimeException("Error starting concord process: " + e.getMessage());
        }
        return 0;
    }

    public ConcordProcess startRemoteProcess(Ck8sPayload payload)
            throws ApiException
    {
        Map<String, Object> payloadData = toMap(
                payload,
                Map.of("concordUrl", concordCfg.baseUrl()));

        ApiResponse<StartProcessResponse> resp = ClientUtils
                .postData(apiClient, "/api/v1/process", payloadData, StartProcessResponse.class);

        int code = resp.getStatusCode();
        if (code < 200 || code >= 300) {
            if (code == 403) {
                throw new ApiException("Forbidden: " + resp.getData());
            }

            throw new ApiException("Request error: " + code);
        }

        return new ConcordProcess(apiClient, resp.getData().getInstanceId());
    }

    private static Map<String, Object> toMap(Ck8sPayload payload, Map<String, String> extraArgs)
    {
        Map<String, Object> result = new LinkedHashMap<>();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        payload.createArchive(out);
        result.put("archive", out.toByteArray());

        payload.args().forEach((name, value) -> result.put("arguments." + name, value));
        extraArgs.forEach((name, value) -> result.put("arguments." + name, value));

        result.putAll(serializeConcordProcessParams(payload.concord()));

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeConcordProcessParams(Map<String, Object> params)
    {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> e : params.entrySet()) {
            Object value = e.getValue();
            if (value instanceof List) {
                value = String.join(",", (List<String>) value);
            }
            result.put(e.getKey(), value);
        }
        return result;
    }
}
