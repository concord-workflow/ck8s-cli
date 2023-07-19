package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.actions.ExecuteScriptAction;
import brig.ck8s.cli.common.Ck8sPath;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.concord.ConcordProcess;
import brig.ck8s.cli.executor.remote.RemoteFlowExecutor;
import com.walmartlabs.concord.ApiException;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static brig.ck8s.cli.cfg.CliConfigurationProvider.getConcordProfile;
import static brig.ck8s.cli.subcom.PackageCommand.createCk8sPayload;

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

        if (!cliOperationContext.cliApp().isTestMode()) {
            ExecutorService executor = Executors.newCachedThreadPool();
            RemoteFlowExecutor flowExecutor = new RemoteFlowExecutor(getConcordProfile(profile), false);
            executeFlow(cliOperationContext, flowExecutor, executor, "cert-manager-local");
            executeFlow(cliOperationContext, flowExecutor, executor, "polaris");
        }

        scriptAction.perform(cliOperationContext, "assertLocalCluster");

        return 0;
    }

    private void executeFlow(
            CliOperationContext cliOperationContext,
            RemoteFlowExecutor flowExecutor,
            ExecutorService executor,
            String flowName)
    {
        try {
            Ck8sPayload payload = createCk8sPayload(
                    cliOperationContext,
                    flowName,
                    Map.of(),
                    false,
                    "local");
            ConcordProcess process = flowExecutor.startRemoteProcess(payload);
            if (process != null) {
                process.streamLogs(executor);
            }
        }
        catch (ApiException e) {
            throw new RuntimeException("Failed to execute flow on local cluster", e);
        }
    }
}
