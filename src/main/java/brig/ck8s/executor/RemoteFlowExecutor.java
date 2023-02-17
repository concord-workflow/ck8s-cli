package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.concord.ConcordConfiguration;
import brig.ck8s.concord.ConcordProcess;
import brig.ck8s.utils.LogUtils;
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
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class RemoteFlowExecutor {

    private final ConcordConfiguration concordCfg;
    private final ApiClient apiClient;

    public RemoteFlowExecutor(ConcordConfiguration concordCfg) {
        this.concordCfg = concordCfg;
        this.apiClient = createClient(concordCfg);
    }

    public ConcordProcess execute(Ck8sPayload payload) {
        try {
            payload = Ck8sPayload.builder().from(payload)
                    .putArgs("concordUrl", concordCfg.baseUrl())
                    .build();

            ConcordProcess process = startProcess(payload);
            LogUtils.info("process: {}", String.format("%s/#/process/%s/log", concordCfg.baseUrl(), process.instanceId()));
            return process;
        } catch (Exception e) {
            throw new RuntimeException("Error starting concord process: " + e.getMessage());
        }
    }

    private ConcordProcess startProcess(Ck8sPayload payload) throws ApiException {
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

    private static ApiClient createClient(ConcordConfiguration cfg) {
        return new ConcordApiClient(cfg.baseUrl())
                .setVerifyingSsl(true)
                .setApiKey(cfg.apiKey());
    }

    private static Map<String, Object> toMap(Ck8sPayload payload) {
        Map<String, Object> result = new LinkedHashMap<>();

        archive(payload.location(), result);
        payload.args().forEach((name, value) -> result.put("arguments." + name, value));
        if (payload.entryPoint() != null) {
            result.put("entryPoint", payload.entryPoint());
        }
        return result;
    }

    private static void archive(Path path, Map<String, Object> input) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                IOUtils.zip(zip, path);
            }
            input.put("archive", out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
