package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.actions.ExecuteScriptAction;
import brig.ck8s.cli.common.Ck8sFlowBuilder;
import brig.ck8s.cli.common.Ck8sPath;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.concord.ConcordProcess;
import brig.ck8s.cli.executor.remote.RemoteFlowExecutor;
import com.walmartlabs.concord.ApiException;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static brig.ck8s.cli.cfg.CliConfigurationProvider.getConcordProfile;

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
            ExecutorService executor = Executors.newCachedThreadPool();
            RemoteFlowExecutor flowExecutor = new RemoteFlowExecutor(getConcordProfile(profile), false);
            executeFlow(payloadLocation, flowExecutor, executor, "cert-manager-local");
            executeFlow(payloadLocation, flowExecutor, executor, "polaris");
        }

        scriptAction.perform(cliOperationContext, "assertLocalCluster");

        return 0;
    }

    private void executeFlow(
            Path payloadLocation,
            RemoteFlowExecutor flowExecutor,
            ExecutorService executor,
            String flowName)
    {
        try {
            ConcordProcess process;
            process = flowExecutor.startRemoteProcess(Ck8sPayload.builder()
                    .location(payloadLocation)
                    .flow(flowName)
                    .build());
            if (process != null) {
                process.streamLogs(executor);
            }
        }
        catch (ApiException e) {
            throw new RuntimeException("Failed to execute flow on local cluster", e);
        }
    }
}
