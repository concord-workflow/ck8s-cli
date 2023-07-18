package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.concord.ConcordProcess;
import brig.ck8s.executor.processors.*;
import brig.ck8s.model.ConcordProfile;
import brig.ck8s.utils.LogUtils;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import javax.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class RemoteFlowExecutor
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

    private static Map<String, Object> toMap(Ck8sPayload payload)
    {
        Map<String, Object> result = new LinkedHashMap<>();

        archive(payload.location(), result);
        payload.args().forEach((name, value) -> result.put("arguments." + name, value));
        result.putAll(payload.concord());
        return result;
    }

    private static void archive(Path path, Map<String, Object> input)
    {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                IOUtils.zip(zip, path);
            }
            input.put("archive", out.toByteArray());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    @Nullable
    public ConcordProcess execute(Ck8sPayload payload)
    {
        try {
            payload = Ck8sPayload.builder().from(payload)
                    .putArgs("concordUrl", concordCfg.baseUrl())
                    .build();

            payload = new ConcordProcessors().process(payload);

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
                return null;
            }
            else {
                ConcordProcess process = startProcess(payload);
                LogUtils.info("process: {}", String.format("%s/#/process/%s/log", concordCfg.baseUrl(), process.instanceId()));
                return process;
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Error starting concord process: " + e.getMessage());
        }
    }

    private ConcordProcess startProcess(Ck8sPayload payload)
            throws ApiException
    {
        ApiResponse<StartProcessResponse> resp = ClientUtils.postData(apiClient, "/api/v1/process", toMap(payload), StartProcessResponse.class);

        int code = resp.getStatusCode();
        if (code < 200 || code >= 300) {
            if (code == 403) {
                throw new ApiException("Forbidden: " + resp.getData());
            }

            throw new ApiException("Request error: " + code);
        }

        return new ConcordProcess(apiClient, resp.getData().getInstanceId());
    }
}
