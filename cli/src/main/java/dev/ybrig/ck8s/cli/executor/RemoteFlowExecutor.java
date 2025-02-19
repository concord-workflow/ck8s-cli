package dev.ybrig.ck8s.cli.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.impl.HttpEntity;
import com.walmartlabs.concord.client2.impl.MultipartRequestBodyHandler;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.VersionProvider;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.concord.RemoteConcordProcess;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RemoteFlowExecutor {

    private final ApiClient apiClient;
    private final long responseTimeout;
    private final boolean dryRunMode;

    public RemoteFlowExecutor(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, 30, 30, false);
    }

    public RemoteFlowExecutor(String baseUrl, String apiKey, long connectTimeout, long responseTimeout, boolean dryRunMode) {
        this.apiClient = createClient(baseUrl, apiKey, connectTimeout);
        this.responseTimeout = responseTimeout;
        this.dryRunMode = dryRunMode;
    }

    private static ApiClient createClient(String baseUrl, String apiKey, long connectTimeout) {
        if (apiKey == null) {
            throw new RuntimeException("Can't create concord client for: api key is empty");
        }

        return new DefaultApiClientFactory(baseUrl, Duration.of(connectTimeout, ChronoUnit.SECONDS), false)
                .create(ApiClientConfiguration.builder().apiKey(apiKey).build());
    }

    private Map<String, Object> toMap(Ck8sPayload payload) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (dryRunMode) {
            LogUtils.info("dryRunMode: enabled");
        }

        archive(payload.ck8sFlows().location(), result);

        if (dryRunMode) {
            result.put(Constants.Request.DRY_RUN_MODE_KEY, true);
        }

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

        var requestId = UUID.randomUUID();

        var response = ClientUtils.withRetry(3, 15, () -> {
            HttpRequest request = apiClient.requestBuilder()
                    .timeout(Duration.of(responseTimeout, ChronoUnit.SECONDS))
                    .uri(URI.create(apiClient.getBaseUri() + "/api/ck8s/v2/process/debug/" + requestId))
                    .header("Content-Type", entity.contentType().toString())
                    .headers("User-Agent", "ck8s-cli (" + VersionProvider.get() + ") " + requestId)
                    .method("POST", HttpRequest.BodyPublishers.ofInputStream(() -> {
                        try {
                            return entity.getContent();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }))
                    .build();

            HttpResponse<String> httpResponse;
            try {
                httpResponse = apiClient.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            } catch (HttpConnectTimeoutException e) {
                throw new RuntimeException(requestIdPrefix(requestId) + "Connect timeout: " + apiClient.getBaseUri());
            } catch (HttpTimeoutException e) {
                throw new RuntimeException(requestIdPrefix(requestId) + "Timeout: " + apiClient.getBaseUri());
            } catch (ConnectException e) {
                throw new RuntimeException(requestIdPrefix(requestId) + "Can't connect to " + apiClient.getBaseUri() + (e.getMessage() != null ? ". Error: " + e.getMessage() : ""));
            } catch (Exception e) {
                throw new RuntimeException(requestIdPrefix(requestId) + "Error sending request: " + e.getMessage());
            }

            int code = httpResponse.statusCode();
            if (code < 200 || code >= 300) {
                throw apiException(httpResponse);
            }
            return httpResponse;
        });

        try {
            var body = response.body();
            var contentType = response.headers().firstValue("Content-Type").orElse("application/json");
            if (isJsonMime(contentType)) {
                StartProcessResponse startProcessResponse = apiClient.getObjectMapper().readValue(body, StartProcessResponse.class);
                return new RemoteConcordProcess(apiClient, startProcessResponse.getInstanceId());
            } else {
                throw new ApiException(requestIdPrefix(requestId) + "Content type \"" + contentType + "\" is not supported", response.statusCode(), response.headers(), body);
            }
        } catch (Exception e) {
            throw new RuntimeException(requestIdPrefix(requestId) + "Error parsing response: " + e.getMessage());
        }
    }

    private static String requestIdPrefix(UUID requestId) {
        return "[RequestID: " + requestId + "]: ";
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

    private ApiException apiException(HttpResponse<String> response) {
        String body = response.body();

        String message = null;
        try {
            message = formatExceptionMessage(response, body);
        } catch (JsonProcessingException e) {
            return new ApiException(message, e, response.statusCode(), response.headers());
        }
        return new ApiException(response.statusCode(), message, response.headers(), body);
    }

    @SuppressWarnings("unchecked")
    private String formatExceptionMessage(HttpResponse<?> response, String body) throws JsonProcessingException {
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

    private static boolean isJsonMime(String mime) {
        String jsonMime = "(?i)^(application/json|[^;/ \t]+/[^;/ \t]+[+]json)[ \t]*(;.*)?$";
        return mime != null && (mime.matches(jsonMime) || mime.equals("*/*"));
    }
}
