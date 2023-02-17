package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.concord.LocalConcordConfiguration;

import java.nio.file.Path;

public class FlowExecutor {

    public enum ExecutorType {
        CONCORD_CLI,
        REMOTE
    }

    public void execute(ExecutorType type, Ck8sPayload payload, boolean verbose) {
        switch (type) {
            case REMOTE -> {
                RemoteFlowExecutor executor = new RemoteFlowExecutor(new LocalConcordConfiguration());
                executor.execute(payload);
            }
            case CONCORD_CLI -> {
                Path cliPath = Path.of(System.getProperty("user.home")).resolve("bin").resolve("concord-cli");
                ConcordCliFlowExecutor executor = new ConcordCliFlowExecutor(cliPath, verbose);
                executor.execute(payload);
            }
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}
