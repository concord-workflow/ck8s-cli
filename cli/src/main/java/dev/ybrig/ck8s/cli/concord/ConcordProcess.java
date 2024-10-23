package dev.ybrig.ck8s.cli.concord;

import java.util.*;
import java.util.concurrent.ExecutorService;

public abstract class ConcordProcess {

    protected final UUID instanceId;

    public ConcordProcess(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public abstract void streamLogs(ExecutorService executor);

    public abstract void waitEnded(long waitTimeout);
}
