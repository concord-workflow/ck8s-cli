package brig.ck8s.concord;

import com.walmartlabs.concord.ApiClient;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ConcordProcess {

    private final ApiClient client;
    private final UUID instanceId;

    public ConcordProcess(ApiClient client, UUID instanceId) {
        this.client = client;
        this.instanceId = instanceId;
    }

    public ApiClient getClient() {
        return client;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public void streamLogs(ExecutorService executor) {
        Future<?> f = executor.submit(new ProcessLogStreamer(client, instanceId));
        try {
            f.get();
        } catch (Exception e) {
            throw new RuntimeException("Stream logs error: " + e.getMessage());
        }
    }
}
