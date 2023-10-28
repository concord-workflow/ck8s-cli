package dev.ybrig.ck8s.cli.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.impl.HttpEntity;
import com.walmartlabs.concord.client2.impl.MultipartRequestBodyHandler;
import com.walmartlabs.concord.client2.impl.ResponseBodyHandler;
import com.walmartlabs.concord.common.IOUtils;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public class RemoteFlowExecutor {

    private final ApiClient apiClient;

    public RemoteFlowExecutor(String baseUrl, String apiKey) {
        this.apiClient = createClient(baseUrl, apiKey);
    }

    private static ApiClient createClient(String baseUrl, String apiKey) {
        if (apiKey == null) {
            throw new RuntimeException("Can't create concord client for: api key is empty");
        }

        return new DefaultApiClientFactory(baseUrl, Duration.of(30, ChronoUnit.SECONDS), false)
                .create(ApiClientConfiguration.builder().apiKey(apiKey).build());
    }

    private static Map<String, Object> toMap(Ck8sPayload payload) {
        Map<String, Object> result = new LinkedHashMap<>();

        archive(payload.ck8sFlows().location(), result);
        result.put("arguments", payload.arguments());
        result.put("debug", payload.debug());
        return result;
    }

    public ConcordProcess execute(String clientClusterAlias, Ck8sPayload payload, String flowName) {
        try {
            ConcordProcess process = startProcess(clientClusterAlias, payload, flowName);
            LogUtils.info("process: {}", String.format("%s/#/process/%s/log", apiClient.getBaseUrl(), process.instanceId()));
            return process;
        } catch (Exception e) {
            throw new RuntimeException("Error starting concord process: " + e.getMessage());
        }
    }

    private ConcordProcess startProcess(String clientClusterAlias, Ck8sPayload ck8sPayload, String flowName) throws ApiException {
        Map<String, Object> payload = toMap(ck8sPayload);
        payload.put("clientClusterAlias", clientClusterAlias);
        payload.put("flow", flowName);

        HttpEntity entity = MultipartRequestBodyHandler.handle(apiClient.getObjectMapper(), payload);

        HttpRequest request = apiClient.requestBuilder()
                .timeout(Duration.of(30, ChronoUnit.SECONDS))
                .uri(URI.create(apiClient.getBaseUri() + "/api/ck8s/v2/process"))
                .header("Content-Type", entity.contentType().toString())
                .method("POST", HttpRequest.BodyPublishers.ofInputStream(() -> {
                    try {
                        return entity.getContent();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }))
                .build();

        HttpResponse<InputStream> response;
        try {
            response = apiClient.getHttpClient().send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Error sending request: " + e.getMessage());
        }

        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            if (code == 403) {
                String body;
                try (InputStream is = response.body()) {
                    body = new String(is.readAllBytes());
                } catch (Exception e) {
                    throw new RuntimeException("Error starting concord process: " + e.getMessage());
                }

                throw new ApiException("Forbidden: " + body);
            }

            throw new ApiException("Request error: " + code);
        }

        try {
            StartProcessResponse startProcessResponse = ResponseBodyHandler.handle(apiClient.getObjectMapper(), response, new TypeReference<StartProcessResponse>() {
            });
            return new ConcordProcess(apiClient, startProcessResponse.getInstanceId());
        } catch (Exception e) {
            throw new RuntimeException("Error parsing response: " + e.getMessage());
        }
    }

    private static void archive(Path path, Map<String, Object> input) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                IOUtils.zip(zip, path);
            }
            input.put("ck8sFlows", out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void archiveToFile(Path src, Path dest) {
        try {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                IOUtils.zip(zip, src);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
