package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;

import java.nio.file.Path;

public class FlowExecutor {

    public enum ExecutorType {
        CONCORD_CLI,
        REMOTE
    }

    public int execute(ExecutorType type, Ck8sPayload payload, boolean verbose) {
        switch (type) {
            case REMOTE -> {
                RemoteFlowExecutor executor = new RemoteFlowExecutor(ConcordConfigurationProvider.get());
                executor.execute(payload);
                return 0;
            }
            case CONCORD_CLI -> {
                Path cliPath = Path.of(System.getProperty("user.home")).resolve("bin").resolve("concord-cli");
                ConcordCliFlowExecutor executor = new ConcordCliFlowExecutor(cliPath, verbose);
                return executor.execute(payload);
            }
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}
