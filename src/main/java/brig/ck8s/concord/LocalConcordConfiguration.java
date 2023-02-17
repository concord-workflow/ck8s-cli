package brig.ck8s.concord;

public class LocalConcordConfiguration implements ConcordConfiguration {

    private static final String BASE_URL = "http://concord.local.localhost:8001";

    @Override
    public String baseUrl() {
        return BASE_URL;
    }

    @Override
    public String apiKey() {
        return "auBy4eDWrKWsyhiDp3AQiw";
    }
}
