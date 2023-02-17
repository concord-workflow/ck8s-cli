package brig.ck8s.concord;

import com.walmartlabs.concord.ApiClient;

import java.util.UUID;

public class ConcordProcess {

    private final ApiClient client;
    private final UUID instanceId;

    public ConcordProcess(ApiClient client, UUID instanceId) {
        this.client = client;
        this.instanceId = instanceId;
    }

    public UUID instanceId() {
        return instanceId;
    }
}
