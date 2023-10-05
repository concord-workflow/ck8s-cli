package dev.ybrig.ck8s.cli.op;

import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.actions.ExecuteScriptAction;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.Ck8sFlowBuilder;
import dev.ybrig.ck8s.cli.common.Ck8sFlows;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.ExecContext;
import dev.ybrig.ck8s.cli.executor.RemoteFlowExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalClusterOperation
        implements CliOperation
{
    @Override
    public Integer execute(CliOperationContext cliOperationContext)
    {
        CliApp cliApp = cliOperationContext.cliApp();
        Ck8sPath ck8s = cliOperationContext.ck8sPath();
        String profile = cliApp.getProfile();

        ExecuteScriptAction scriptAction = new ExecuteScriptAction(ck8s);

        scriptAction.perform(cliOperationContext, "ck8sDown");
        scriptAction.perform(cliOperationContext, "ck8sUp");

        Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8s, cliApp.getTargetRootPath(), null)
                .build("local");

        if (!cliApp.isTestMode()) {
            RemoteFlowExecutor flowExecutor = new RemoteFlowExecutor(CliConfigurationProvider.getConcordProfile(profile));

            ExecutorService executor = Executors.newCachedThreadPool();

            Ck8sPayload payload = Ck8sPayload.builder()
                    .flows(ck8sFlows)
                    .ck8sPath(ck8s)
                    .build();

            ExecContext ctx = ExecContext.builder()
                    .verbosity(cliOperationContext.verbosity())
                    .testMode(cliApp.isTestMode())
                    .build();

            ConcordProcess process = flowExecutor.execute(ctx, payload, "cert-manager-local");
            if (process != null) {
                process.streamLogs(executor);
            }

            process = flowExecutor.execute(ctx, payload, "polaris");
            if (process != null) {
                process.streamLogs(executor);
            }
        }

        scriptAction.perform(cliOperationContext, "assertLocalCluster");

        return 0;
    }
}
