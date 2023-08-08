package dev.ybrig.ck8s.cli.selfupdate;

import dev.ybrig.ck8s.cli.common.MapUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public class GitHubLatestReleaseFinder
{

    public String find(String organization, String repository)
            throws Exception
    {
        String api = format("https://api.github.com/repos/%s/%s/releases", organization, repository);

        Builder requestBuilder = HttpRequest.newBuilder();
        HttpRequest request = requestBuilder
                .uri(new URI(api))
                .version(HttpClient.Version.HTTP_2)
                .GET()
                .build();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(Redirect.ALWAYS)
                .build();
        HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        List<Map<String, Object>> releases = mapper.readValue(response.body(), new TypeReference<>()
        {
        });
        if (releases.isEmpty()) {
            return null;
        }

        Map<String, Object> latest = releases.get(0);
        return MapUtils.getString(latest, "tag_name");
    }
}
