package brig.ck8s.cli.executor;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.executor.cli.ConcordCliFlowExecutor;
import brig.ck8s.cli.executor.remote.RemoteFlowExecutor;
import brig.ck8s.cli.op.CliOperationContext;

import static brig.ck8s.cli.cfg.CliConfigurationProvider.getConcordProfile;

public enum FlowExecutorType
{
    CONCORD_CLI,
    REMOTE;

    public static FlowExecutor resolveFlowExecutor(CliOperationContext cliOperationContext)
    {
        CliApp cliedApp = cliOperationContext.cliApp();
        FlowExecutorType executorType = cliedApp.getFlowExecutorType().getType();
        
        switch (executorType) {
            case REMOTE -> {
                return new RemoteFlowExecutor(getConcordProfile(cliedApp.getProfile()), cliedApp.isTestMode());
            }
            case CONCORD_CLI -> {
                return new ConcordCliFlowExecutor(cliOperationContext.verbosity());
            }
            default -> throw new IllegalArgumentException("Unknown type: " + executorType);
        }
    }
}
