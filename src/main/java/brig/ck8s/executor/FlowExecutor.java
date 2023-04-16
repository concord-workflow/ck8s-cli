package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;
import com.walmartlabs.concord.cli.Verbosity;

public class FlowExecutor {

    public enum ExecutorType {
        CONCORD_CLI,
        REMOTE
    }

    public int execute(ExecutorType type, Ck8sPayload payload, String profile, Verbosity verbosity, boolean testMode) {
        switch (type) {
            case REMOTE -> {
                RemoteFlowExecutor executor = new RemoteFlowExecutor(ConcordConfigurationProvider.get(profile), testMode);
                executor.execute(payload);
                return 0;
            }
            case CONCORD_CLI -> {
                ConcordCliFlowExecutor executor = new ConcordCliFlowExecutor(verbosity);
                return executor.execute(payload);
            }
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        }
    }
}
