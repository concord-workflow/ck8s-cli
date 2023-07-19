package brig.ck8s.cli.executor;

import brig.ck8s.cli.cfg.CliConfigurationProvider;
import brig.ck8s.cli.common.Ck8sPayload;
import com.walmartlabs.concord.cli.Verbosity;

public class FlowExecutor
{

    public int execute(ExecutorType type, Ck8sPayload payload, String profile, Verbosity verbosity, boolean testMode)
    {
        switch (type) {
            case REMOTE -> {
                RemoteFlowExecutor executor = new RemoteFlowExecutor(CliConfigurationProvider.getConcordProfile(profile), testMode);
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

    public enum ExecutorType
    {
        CONCORD_CLI,
        REMOTE
    }
}