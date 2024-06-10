package dev.ybrig.ck8s.cli.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.impl.HttpEntity;
import com.walmartlabs.concord.client2.impl.MultipartRequestBodyHandler;
import com.walmartlabs.concord.client2.impl.ResponseBodyHandler;
import com.walmartlabs.concord.common.IOUtils;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.immutables.value.Value;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
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
        result.put("org", MapUtils.assertString(payload.arguments(), "clusterRequest.organization.name"));
        if (payload.project() != null) {
            result.put("project", payload.project());
        }

        return result;
    }

    public ConcordProcess execute(String clientClusterAlias, Ck8sPayload payload, String flowName, List<String> activeProfiles) {
        try {
            ConcordProcess process = startProcess(clientClusterAlias, payload, flowName, activeProfiles);
            LogUtils.info("process: {}", String.format("%s/#/process/%s/log", apiClient.getBaseUrl(), process.instanceId()));
            return process;
        } catch (Exception e) {
            throw new RuntimeException("Error starting concord process: " + e.getMessage());
        }
    }

    private ConcordProcess startProcess(String clientClusterAlias, Ck8sPayload ck8sPayload, String flowName, List<String> activeProfiles) throws ApiException {
        Map<String, Object> payload = toMap(ck8sPayload);

        payload.put("clientClusterAlias", clientClusterAlias);
        payload.put("flow", flowName);
        if (activeProfiles != null && !activeProfiles.isEmpty()) {
            payload.put("activeProfiles", activeProfiles.toArray(new String[0]));
        }

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
            throw apiException(response);
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
            input.put("ck8sFlowsArchive", out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ApiException apiException(HttpResponse<InputStream> response) {
        String body = null;
        try (InputStream is = response.body()) {
            if (is != null) {
                body = new String(is.readAllBytes());
            }
        } catch (Exception e) {
            return new ApiException(null, e, response.statusCode(), response.headers());
        }

        String message = null;
        try {
            message = formatExceptionMessage(response, body);
        } catch (JsonProcessingException e) {
            return new ApiException(message, e, response.statusCode(), response.headers());
        }
        return new ApiException(response.statusCode(), message, response.headers(), body);
    }

    @SuppressWarnings("unchecked")
    private String formatExceptionMessage(HttpResponse<InputStream> response, String body) throws JsonProcessingException {
        if (body == null || body.isEmpty()) {
            return response.statusCode() + " [no body]";
        }

        String msg = body;

        String type = response.headers().firstValue("Content-Type").orElse(null);

        Map<String, Object> vErrs = null;
        if ("application/vnd.siesta-validation-errors-v1+json".equals(type)) {
            List<Object> l = (List<Object>) apiClient.getObjectMapper().readValue(body, List.class);
            if (!l.isEmpty()) {
                vErrs = (Map<String,Object>) l.get(0);
            }
        } else if ("application/json".equals(type)) {
            vErrs = (Map<String,Object>) apiClient.getObjectMapper().readValue(body, Map.class);
        } else {
            msg = "Server response: " + body;
        }

        if (vErrs != null) {
            if (vErrs.containsKey("message")) {
                msg = (String) vErrs.get("message");
            }
            if (vErrs.containsKey("details")) {
                msg = msg + " Details: " + vErrs.get("details");
            }
        }

        return msg;
    }
}
