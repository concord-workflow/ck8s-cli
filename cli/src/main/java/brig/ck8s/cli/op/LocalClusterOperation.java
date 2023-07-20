package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.actions.ExecuteScriptAction;
import brig.ck8s.cli.cfg.CliConfigurationProvider;
import brig.ck8s.cli.common.Ck8sFlowBuilder;
import brig.ck8s.cli.common.Ck8sPath;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.concord.ConcordProcess;
import brig.ck8s.cli.executor.RemoteFlowExecutor;

import java.nio.file.Path;
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

        Path payloadLocation = new Ck8sFlowBuilder(ck8s, cliApp.getTargetRootPath())
                .build("local");

        if (!cliApp.isTestMode()) {
            RemoteFlowExecutor flowExecutor = new RemoteFlowExecutor(CliConfigurationProvider.getConcordProfile(profile), false);

            ExecutorService executor = Executors.newCachedThreadPool();

            ConcordProcess process = flowExecutor.execute(Ck8sPayload.builder()
                    .location(payloadLocation)
                    .flow("cert-manager-local")
                    .build());
            if (process != null) {
                process.streamLogs(executor);
            }

            process = flowExecutor.execute(Ck8sPayload.builder()
                    .location(payloadLocation)
                    .flow("polaris")
                    .build());
            if (process != null) {
                process.streamLogs(executor);
            }
        }

        scriptAction.perform(cliOperationContext, "assertLocalCluster");

        return 0;
    }
}
