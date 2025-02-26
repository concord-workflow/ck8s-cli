package dev.ybrig.ck8s.cli.concord;

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import dev.ybrig.ck8s.cli.model.ConcordProfile;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class CliApiClientFactory {

    public static ApiClient create(ConcordProfile profile, long connectTimeout) {
        return create(profile.baseUrl(), profile.apiKey(), connectTimeout);
    }

    public static ApiClient create(String baseUrl, String apiKey, long connectTimeout) {
        if (apiKey == null) {
            throw new RuntimeException("Can't create concord client for: api key is empty");
        }

        return new DefaultApiClientFactory(baseUrl, Duration.of(connectTimeout, ChronoUnit.SECONDS), false)
                .create(ApiClientConfiguration.builder().apiKey(apiKey).build());
    }

    private CliApiClientFactory() {
    }
}
