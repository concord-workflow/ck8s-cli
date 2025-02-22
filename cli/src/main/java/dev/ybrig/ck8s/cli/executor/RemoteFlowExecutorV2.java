package dev.ybrig.ck8s.cli.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.impl.HttpEntity;
import com.walmartlabs.concord.client2.impl.MultipartRequestBodyHandler;
import com.walmartlabs.concord.sdk.Constants;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.concord.RemoteConcordProcess;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoteFlowExecutorV2 {

    private final ApiClient apiClient;
    private final long responseTimeout;
    private final boolean dryRunMode;

    public RemoteFlowExecutorV2(String baseUrl, String apiKey, long connectTimeout, long responseTimeout, boolean dryRunMode) {
        this.apiClient = createClient(baseUrl, apiKey, connectTimeout);
        this.responseTimeout = responseTimeout;
        this.dryRunMode = dryRunMode;
    }

    public ConcordProcess execute(Map<String, Object> request,
                                  String orgName, String projectName,
                                  Map<String, Object> args, boolean debug,
                                  List<String> activeProfiles) {
        try {
            var startAt = System.currentTimeMillis();
            ConcordProcess process = startProcess(request, orgName, projectName, args, debug, activeProfiles);
            var duration = System.currentTimeMillis() - startAt;

            LogUtils.info("process: {}, duration {}", String.format("%s/#/process/%s/log", apiClient.getBaseUrl(), process.instanceId()), duration);

            return process;
        } catch (Exception e) {
            throw new RuntimeException("Error starting concord process: " + e.getMessage());
        }
    }

    private ConcordProcess startProcess(Map<String, Object> request,
                                        String orgName, String projectName,
                                        Map<String, Object> args, boolean debug,
                                        List<String> activeProfiles) throws ApiException {
        var payload = new HashMap<>(request);

        if (dryRunMode) {
            LogUtils.info("dryRunMode: enabled");
            payload.put(Constants.Request.DRY_RUN_MODE_KEY, true);
        }

        payload.put(Constants.Request.ARGUMENTS_KEY, args);
        payload.put(Constants.Request.DEBUG_KEY, debug);

        payload.put(Constants.Multipart.ORG_NAME, orgName);
        payload.put(Constants.Multipart.PROJECT_NAME, projectName);

        if (activeProfiles != null && !activeProfiles.isEmpty()) {
            payload.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles.toArray(new String[0]));
        }

        return sendRequest(payload);
    }

    private ConcordProcess sendRequest(Map<String, Object> payload) throws ApiException {
        HttpEntity entity = MultipartRequestBodyHandler.handle(apiClient.getObjectMapper(), payload);

        var requestIdGlobal = UUID.randomUUID().toString();
        var retryNum = new AtomicInteger(0);
        var response = ClientUtils.withRetry(3, 15000, () -> {
            var requestId = requestIdGlobal + "_" + retryNum.incrementAndGet();

            HttpRequest request = apiClient.requestBuilder()
                    .timeout(Duration.of(responseTimeout, ChronoUnit.SECONDS))
                    .uri(URI.create(apiClient.getBaseUri() + "/api/ck8s/v3/process/" + requestId))
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
                throw new ApiException(requestIdPrefix(requestIdGlobal) + "Content type \"" + contentType + "\" is not supported", response.statusCode(), response.headers(), body);
            }
        } catch (Exception e) {
            throw new RuntimeException(requestIdPrefix(requestIdGlobal) + "Error parsing response: " + e.getMessage());
        }
    }

    private static String requestIdPrefix(String requestId) {
        return "[RequestID: " + requestId + "]: ";
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

    private static ApiClient createClient(String baseUrl, String apiKey, long connectTimeout) {
        if (apiKey == null) {
            throw new RuntimeException("Can't create concord client for: api key is empty");
        }

        return new DefaultApiClientFactory(baseUrl, Duration.of(connectTimeout, ChronoUnit.SECONDS), false)
                .create(ApiClientConfiguration.builder().apiKey(apiKey).build());
    }

    private static boolean isJsonMime(String mime) {
        String jsonMime = "(?i)^(application/json|[^;/ \t]+/[^;/ \t]+[+]json)[ \t]*(;.*)?$";
        return mime != null && (mime.matches(jsonMime) || mime.equals("*/*"));
    }
}
