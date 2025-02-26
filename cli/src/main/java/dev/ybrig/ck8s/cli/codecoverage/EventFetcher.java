package dev.ybrig.ck8s.cli.codecoverage;

import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.OffsetDateTimeParam;
import com.walmartlabs.concord.client2.ProcessEventEntry;
import com.walmartlabs.concord.client2.ProcessEventsApi;

import java.util.List;
import java.util.UUID;

public class EventFetcher {

    private final ProcessEventsApi api;

    public EventFetcher(ProcessEventsApi api) {
        this.api = api;
    }

    public List<ProcessEventEntry> fetch(UUID processId, Long fromId) throws ApiException {
        OffsetDateTimeParam after = null;
        UUID eventCorrelationId = null;
        String eventPhase = null;
        var includeAll = false;
        var limit = 1000;

        return api.listProcessEvents(processId, "ELEMENT", after, fromId, eventCorrelationId, eventPhase, includeAll, limit);
    }
}
