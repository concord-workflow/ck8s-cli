package dev.ybrig.ck8s.cli.concord;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CliConcordProcess extends ConcordProcess {

    public CliConcordProcess(UUID instanceId) {
        super(instanceId);
    }

    @Override
    public void streamLogs(ExecutorService executor) {
        // do nothing
    }

    @Override
    public void waitEnded(long waitTimeout) {
        // do nothing
    }
}
