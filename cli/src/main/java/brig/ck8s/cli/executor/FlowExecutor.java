package brig.ck8s.cli.executor;

import brig.ck8s.cli.cfg.CliConfigurationProvider;

import java.util.Map;

public class FlowExecutor
{

    public int execute(ExecutorType type, ExecContext execContext, String flowName, Map<String, Object> extraArgs)
    {
        switch (type) {
            case REMOTE -> {
                RemoteFlowExecutor executor = new RemoteFlowExecutor(CliConfigurationProvider.getConcordProfile(execContext.profile()));
                executor.execute(execContext, flowName, extraArgs);
                return 0;
            }
            case CONCORD_CLI -> {
                ConcordCliFlowExecutor executor = new ConcordCliFlowExecutor(execContext.verbosity());
                return executor.execute(execContext.flows(), flowName, extraArgs);
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
