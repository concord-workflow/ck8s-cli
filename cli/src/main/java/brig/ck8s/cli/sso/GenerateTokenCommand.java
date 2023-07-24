package brig.ck8s.cli.sso;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.cfg.CliConfigurationProvider;
import brig.ck8s.cli.common.IOUtils;
import brig.ck8s.cli.common.MapUtils;
import brig.ck8s.cli.common.Mapper;
import brig.ck8s.cli.model.CliConfiguration;
import brig.ck8s.cli.model.ConcordProfile;
import brig.ck8s.cli.utils.LogUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.awt.Desktop;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@CommandLine.Command(name = "token",
        description = {"Generate Concord access token and save it to the configuration"}
)
public class GenerateTokenCommand
        implements Callable<Integer>
{

    private static final String CONCORD_KEY_NAME = "ck8s-cli";

    @CommandLine.Option(names = {"-p", "--profile"}, description = "concord instance profile name")
    String profile;

    @CommandLine.ParentCommand
    private CliApp cliApp;

    private static List<ConcordProfile> createConcordTokens(String profile, String accessToken)
    {
        List<ConcordProfile> profiles;
        if (profile != null) {
            profiles = Collections.singletonList(CliConfigurationProvider.getConcordProfile(profile));
        }
        else {
            profiles = CliConfigurationProvider.get().concordProfiles();
        }

        // ignore local concord profiles
        profiles = profiles.stream().filter(i -> !i.baseUrl().contains("local")).collect(Collectors.toList());

        if (profiles.isEmpty()) {
            throw new RuntimeException("Can't find any Concord profile");
        }

        List<ConcordProfile> result = new ArrayList<>();
        for (ConcordProfile p : profiles) {
            LogUtils.info("Creating concord token for '{}'...", p.alias());

            try {
                String concordToken = replaceConcordToken(p.baseUrl(), accessToken);
                result.add(ConcordProfile.builder().from(p).apiKey(concordToken).build());
            }
            catch (Exception e) {
                LogUtils.error("Can't create '{}' concord token: {}", p.alias(), e.getMessage());
            }
        }
        return result;
    }

    private static String replaceConcordToken(String baseUrl, String accessToken)
            throws Exception
    {
        List<Map<String, Object>> existingTokens = listConcordTokens(baseUrl, accessToken);

        String ck8sCliToken = existingTokens.stream().filter(t -> CONCORD_KEY_NAME.equals(MapUtils.assertString(t, "name"))).findFirst()
                .map(t -> MapUtils.assertString(t, "id"))
                .orElse(null);
        if (ck8sCliToken != null) {
            deleteConcordToken(baseUrl, accessToken, ck8sCliToken);
        }

        return createConcordToken(baseUrl, accessToken);
    }

    private static String createConcordToken(String baseUrl, String accessToken)
            throws Exception
    {
        String url = baseUrl + "/api/v1/apikey";
        Map<String, Object> body = Collections.singletonMap("name", CONCORD_KEY_NAME);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        HttpRequest request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/json")
                .headers("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(Mapper.jsonMapper().writeAsString(body)))
                .build();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (200 != response.statusCode()) {
            String msg = String.format("Can't create concord token. Response status code: %s, description: %s", response.statusCode(), IOUtils.toString(response.body()));
            throw new RuntimeException(msg);
        }
        return MapUtils.getString(asMap(response), "key");
    }

    private static List<Map<String, Object>> listConcordTokens(String baseUrl, String accessToken)
            throws URISyntaxException
    {
        String url = baseUrl + "/api/v1/apikey/";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        HttpRequest request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/json")
                .headers("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (ConnectException e) {
            throw new RuntimeException("Can't connect to " + baseUrl);
        }
        catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        if (200 != response.statusCode()) {
            String msg = String.format("Can't list concord token. Response status code: %s, description: %s", response.statusCode(), IOUtils.toString(response.body()));
            throw new RuntimeException(msg);
        }
        return asList(response);
    }

    private static void deleteConcordToken(String baseUrl, String accessToken, String tokenId)
            throws Exception
    {
        String url = baseUrl + "/api/v1/apikey/" + tokenId;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        HttpRequest request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/json")
                .headers("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static String getOidcAccessToken(String oauthUrl, String clientId)
            throws Exception
    {
        Map<String, Object> codeResponse = requestDeviceVerificationCode(oauthUrl, clientId);
        String verificationUrl = MapUtils.getString(codeResponse, "verification_uri_complete");
        if (verificationUrl == null || verificationUrl.trim().isEmpty()) {
            LogUtils.error("Invalid auth server response. `verification_uri_complete` not found: {}", codeResponse);
            return null;
        }

        String deviceCode = MapUtils.getString(codeResponse, "device_code");
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

        long startedAt = System.currentTimeMillis();
        int checkInterval = 5;
        while (System.currentTimeMillis() - startedAt < TimeUnit.MINUTES.toMillis(3)) {
            Map<String, Object> response = requestToken(oauthUrl, clientId, deviceCode);
            if (response.isEmpty()) {
                sleep(TimeUnit.SECONDS.toMillis(checkInterval));
                LogUtils.info("Waiting user approve...");
            }
            else {
                return MapUtils.getString(response, "access_token");
            }
        }

        return null;
    }

    private static Map<String, Object> requestDeviceVerificationCode(String oauthUrl, String clientId)
            throws Exception
    {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        form.put("scope", "openid profile offline_access email groups");

        String url = oauthUrl + "/v1/device/authorize";

        HttpResponse<InputStream> response = post(url, form);
        if (200 != response.statusCode()) {
            LogUtils.error("Invalid auth server response. Status code: {}", response.statusCode(), IOUtils.toString(response.body()));
            throw new RuntimeException("Invalid server response");
        }
        return asMap(response);
    }

    private static Map<String, Object> requestToken(String oauthUrl, String clientId, String deviceCode)
            throws Exception
    {
        Map<String, String> form = new HashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("device_code", deviceCode);

        String url = oauthUrl + "/v1/token";

        HttpResponse<InputStream> response = post(url, form);
        if (200 != response.statusCode()) {
            return Collections.emptyMap();
        }
        return asMap(response);
    }

    private static HttpResponse<InputStream> post(String url, Map<String, String> form)
            throws Exception
    {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        HttpRequest request = requestBuilder
                .uri(new URI(url))
                .version(HttpClient.Version.HTTP_2)
                .header("Accept", "application/json")
                .headers("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(asFormBody(form)))
                .build();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private static Map<String, Object> asMap(HttpResponse<InputStream> response)
            throws IOException
    {
        return responseMapper().readValue(response.body(), new TypeReference<>()
        {
        });
    }

    private static List<Map<String, Object>> asList(HttpResponse<InputStream> response)
    {
        try {
            return responseMapper().readValue(response.body(), new TypeReference<>()
            {
            });
        }
        catch (Exception e) {
            throw new RuntimeException("Can't parse response: " + e.getMessage());
        }
    }

    private static ObjectMapper responseMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }

    private static String asFormBody(Map<String, String> parameters)
    {
        return parameters.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static void sleep(long ms)
    {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Integer call()
            throws Exception
    {
        if (cliApp.isTestMode()) {
            System.out.println("Generating OIDC token for profile: %s".formatted(profile));
            return 0;
        }

        CliConfiguration.Oidc oidc = CliConfigurationProvider.get().oidc();
        if (oidc == null) {
            LogUtils.error("Please specify oidc configuration in ck8s-cli configuration file");
            return -1;
        }
        String oauthUrl = oidc.oauthUrl();
        if (oauthUrl == null || oauthUrl.trim().isEmpty()) {
            LogUtils.error("Please specify oidc.oauthUrl in ck8s-cli configuration file");
            return -1;
        }

        String clientId = oidc.clientId();
        if (clientId == null || clientId.trim().isEmpty()) {
            LogUtils.error("Please specify oidc.clientId in ck8s-cli configuration file");
            return -1;
        }

        String accessToken = getOidcAccessToken(oauthUrl, clientId);
        if (accessToken == null) {
            LogUtils.error("Can't get OIDC access token");
            return -1;
        }

        List<ConcordProfile> updatedProfiles = createConcordTokens(profile, accessToken);
        if (!updatedProfiles.isEmpty()) {
            CliConfiguration cfg = merge(CliConfigurationProvider.get(), updatedProfiles);
            CliConfigurationProvider.replace(cfg);
        }

        return 0;
    }

    private CliConfiguration merge(CliConfiguration cliConfiguration, List<ConcordProfile> updatedProfiles)
    {
        List<ConcordProfile> profiles = new ArrayList<>();
        for (ConcordProfile p : cliConfiguration.concordProfiles()) {
            ConcordProfile profile = updatedProfiles.stream().filter(u -> u.alias().equals(p.alias())).findFirst().orElse(p);
            profiles.add(profile);
        }

        return CliConfiguration.builder().from(cliConfiguration)
                .concordProfiles(profiles)
                .build();
    }
}
