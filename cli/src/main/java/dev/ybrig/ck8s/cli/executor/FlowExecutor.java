package dev.ybrig.ck8s.cli.executor;

import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;

public class FlowExecutor
{

    public int execute(ExecutorType type, ExecContext execContext, String flowName)
    {
        switch (type) {
            case REMOTE -> {
                RemoteFlowExecutor executor = new RemoteFlowExecutor(CliConfigurationProvider.getConcordProfile(execContext.profile()));
                executor.execute(execContext, flowName);
                return 0;
            }
            case CONCORD_CLI -> {
                ConcordCliFlowExecutor executor = new ConcordCliFlowExecutor(execContext.verbosity());
                return executor.execute(execContext.payload(), flowName);
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
