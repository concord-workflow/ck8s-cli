package dev.ybrig.ck8s.cli.op;

import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.actions.ExecuteScriptAction;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.Ck8sFlowBuilder;
import dev.ybrig.ck8s.cli.common.Ck8sFlows;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.RemoteFlowExecutor;
import dev.ybrig.ck8s.cli.model.ConcordProfile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalClusterOperation
        implements CliOperation
{
    @Override
    public Integer execute(CliOperationContext cliOperationContext)
    {
        Ck8sPath ck8s = cliOperationContext.ck8sPath();

        ExecuteScriptAction scriptAction = new ExecuteScriptAction(ck8s);

        scriptAction.perform(cliOperationContext, "ck8sDown");
        scriptAction.perform(cliOperationContext, "ck8sUp");

        String clusterAlias = "local";

        CliApp cliApp = cliOperationContext.cliApp();
        Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8s, cliApp.getTargetRootPath(), null)
                .build();

        if (!cliApp.isTestMode()) {

            Ck8sPayload payload = Ck8sPayload.builder()
                    .debug(cliOperationContext.verbosity().verbose())
                    .arguments(cliApp.getExtraVars())
                    .ck8sFlows(ck8sFlows)
                    .build();

            ExecutorService executor = Executors.newCachedThreadPool();

            ConcordProfile profile = CliConfigurationProvider.getConcordProfile(cliApp.getProfile());
            RemoteFlowExecutor flowExecutor = new RemoteFlowExecutor(profile.baseUrl(), profile.apiKey());

            ConcordProcess process = flowExecutor.execute(clusterAlias, payload, "cert-manager-local");
            if (process != null) {
                process.streamLogs(executor);
            }

            process = flowExecutor.execute(clusterAlias, payload, "polaris");
            if (process != null) {
                process.streamLogs(executor);
            }
        }

        scriptAction.perform(cliOperationContext, "assertLocalCluster");

        return 0;
    }
}
