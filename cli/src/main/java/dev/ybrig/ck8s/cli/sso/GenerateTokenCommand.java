package dev.ybrig.ck8s.cli.sso;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.IOUtils;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.Mapper;
import dev.ybrig.ck8s.cli.model.CliConfiguration;
import dev.ybrig.ck8s.cli.model.ConcordProfile;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import picocli.CommandLine;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@CommandLine.Command(name = "token",
        description = {"Generate Concord access token and save it to the configuration"}
)
public class GenerateTokenCommand
        implements Callable<Integer> {

    private static final String CONCORD_KEY_NAME = "ck8s-cli";

    @CommandLine.Option(names = {"-p", "--profile"}, description = "concord instance profile name")
    String profile;

    @CommandLine.ParentCommand
    private CliApp cliApp;

    @Override
    public Integer call()
            throws Exception {
        var oidc = CliConfigurationProvider.get().oidc();
        if (oidc == null) {
            LogUtils.error("Please specify oidc configuration in ck8s-cli configuration file");
            return -1;
        }
        var oauthUrl = oidc.oauthUrl();
        if (oauthUrl == null || oauthUrl.trim().isEmpty()) {
            LogUtils.error("Please specify oidc.oauthUrl in ck8s-cli configuration file");
            return -1;
        }

        var clientId = oidc.clientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            LogUtils.error("Please specify oidc.clientId in ck8s-cli configuration file");
            return -1;
        }

        var accessToken = getOidcAccessToken(oauthUrl, clientId);
        if (accessToken == null) {
            LogUtils.error("Can't get OIDC access token");
            return -1;
        }

        var updatedProfiles = createConcordTokens(profile, accessToken);
        if (!updatedProfiles.isEmpty()) {
            var cfg = merge(CliConfigurationProvider.get(), updatedProfiles);
            CliConfigurationProvider.replace(cfg);
        }

        return 0;
    }

    private static List<ConcordProfile> createConcordTokens(String profile, String accessToken) {
        List<ConcordProfile> profiles;
        if (profile != null) {
            profiles = Collections.singletonList(CliConfigurationProvider.getConcordProfile(profile));
        } else {
            profiles = CliConfigurationProvider.get().concordProfiles();
        }

        // ignore local concord profiles
        profiles = profiles.stream().filter(i -> !i.baseUrl().contains("local")).collect(Collectors.toList());

        if (profiles.isEmpty()) {
            throw new RuntimeException("Can't find any Concord profile");
        }

        List<ConcordProfile> result = new ArrayList<>();
        for (var p : profiles) {
            LogUtils.info("Creating concord token for '{}'...", p.alias());

            try {
                var concordToken = replaceConcordToken(p.baseUrl(), accessToken);
                result.add(ConcordProfile.builder().from(p).apiKey(concordToken).build());
            } catch (Exception e) {
                LogUtils.error("Can't create '{}' concord token: {}", p.alias(), e.getMessage());
            }
        }
        return result;
    }

    private static String replaceConcordToken(String baseUrl, String accessToken)
            throws Exception {
        var existingTokens = listConcordTokens(baseUrl, accessToken);

        var ck8sCliToken = existingTokens.stream().filter(t -> CONCORD_KEY_NAME.equals(MapUtils.assertString(t, "name"))).findFirst()
                .map(t -> MapUtils.assertString(t, "id"))
                .orElse(null);
        if (ck8sCliToken != null) {
            deleteConcordToken(baseUrl, accessToken, ck8sCliToken);
        }

        return createConcordToken(baseUrl, accessToken);
    }

    private static String createConcordToken(String baseUrl, String accessToken)
            throws Exception {
        var url = baseUrl + "/api/v1/apikey";
        Map<String, Object> body = Collections.singletonMap("name", CONCORD_KEY_NAME);

        var requestBuilder = HttpRequest.newBuilder();
        var request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/json")
                .headers("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(Mapper.jsonMapper().writeAsString(body)))
                .build();
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (200 != response.statusCode()) {
            var msg = String.format("Can't create concord token. Response status code: %s, description: %s", response.statusCode(), IOUtils.toString(response.body()));
            throw new RuntimeException(msg);
        }
        return MapUtils.getString(asMap(response), "key");
    }

    private static List<Map<String, Object>> listConcordTokens(String baseUrl, String accessToken)
            throws URISyntaxException {
        var url = baseUrl + "/api/v1/apikey/";

        var requestBuilder = HttpRequest.newBuilder();
        var request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/json")
                .headers("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (ConnectException e) {
            throw new RuntimeException("Can't connect to " + baseUrl);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        if (200 != response.statusCode()) {
            var msg = String.format("Can't list concord token. Response status code: %s, description: %s", response.statusCode(), IOUtils.toString(response.body()));
            throw new RuntimeException(msg);
        }
        return asList(response);
    }

    private static void deleteConcordToken(String baseUrl, String accessToken, String tokenId)
            throws Exception {
        var url = baseUrl + "/api/v1/apikey/" + tokenId;

        var requestBuilder = HttpRequest.newBuilder();
        var request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/json")
                .headers("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static String getOidcAccessToken(String oauthUrl, String clientId)
            throws Exception {
        var codeResponse = requestDeviceVerificationCode(oauthUrl, clientId);
        var verificationUrl = MapUtils.getString(codeResponse, "verification_uri_complete");
        if (verificationUrl == null || verificationUrl.trim().isEmpty()) {
            LogUtils.error("Invalid auth server response. `verification_uri_complete` not found: {}", codeResponse);
            return null;
        }

        var deviceCode = MapUtils.getString(codeResponse, "device_code");
        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            LogUtils.error("Invalid auth server response. `device_code` not found: {}", codeResponse);
            return null;
        }

        LogUtils.info("Attempting to automatically open the SSO authorization page in your default browser.");
        LogUtils.info("If the browser does not open or you wish to use a different device to authorize this request, open the following URL:");
        LogUtils.info(verificationUrl);

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(verificationUrl));
        }

        var startedAt = System.currentTimeMillis();
        var checkInterval = 5;
        while (System.currentTimeMillis() - startedAt < TimeUnit.MINUTES.toMillis(3)) {
            var response = requestToken(oauthUrl, clientId, deviceCode);
            if (response.isEmpty()) {
                sleep(TimeUnit.SECONDS.toMillis(checkInterval));
                LogUtils.info("Waiting user approve...");
            } else {
                return MapUtils.getString(response, "access_token");
            }
        }

        return null;
    }

    private static Map<String, Object> requestDeviceVerificationCode(String oauthUrl, String clientId)
            throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        form.put("scope", "openid profile offline_access email groups");

        var url = oauthUrl + "/v1/device/authorize";

        var response = post(url, form);
        if (200 != response.statusCode()) {
            LogUtils.error("Invalid auth server response. Status code: {}", response.statusCode(), IOUtils.toString(response.body()));
            throw new RuntimeException("Invalid server response");
        }
        return asMap(response);
    }

    private static Map<String, Object> requestToken(String oauthUrl, String clientId, String deviceCode)
            throws Exception {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("device_code", deviceCode);

        var url = oauthUrl + "/v1/token";

        var response = post(url, form);
        if (200 != response.statusCode()) {
            return Collections.emptyMap();
        }
        return asMap(response);
    }

    private static HttpResponse<InputStream> post(String url, Map<String, String> form)
            throws Exception {
        var requestBuilder = HttpRequest.newBuilder();
        var request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(asFormBody(form)))
                .build();
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private static Map<String, Object> asMap(HttpResponse<InputStream> response)
            throws IOException {
        return responseMapper().readValue(response.body(), new TypeReference<>() {
        });
    }

    private static List<Map<String, Object>> asList(HttpResponse<InputStream> response) {
        try {
            return responseMapper().readValue(response.body(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Can't parse response: " + e.getMessage());
        }
    }

    private static ObjectMapper responseMapper() {
        var mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }

    private static String asFormBody(Map<String, String> parameters) {
        return parameters.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CliConfiguration merge(CliConfiguration cliConfiguration, List<ConcordProfile> updatedProfiles) {
        List<ConcordProfile> profiles = new ArrayList<>();
        for (var p : cliConfiguration.concordProfiles()) {
            var profile = updatedProfiles.stream().filter(u -> u.alias().equals(p.alias())).findFirst().orElse(p);
            profiles.add(profile);
        }

        return CliConfiguration.builder().from(cliConfiguration)
                .concordProfiles(profiles)
                .build();
    }
}
