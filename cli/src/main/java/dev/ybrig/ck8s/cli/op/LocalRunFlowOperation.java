package dev.ybrig.ck8s.cli.op;

import com.walmartlabs.concord.cli.Verbosity;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.ConcordCliFlowExecutor;

import java.util.Map;

import static dev.ybrig.ck8s.cli.op.RunFlowOperationUtils.needsConfirmation;

public class LocalRunFlowOperation implements CliOperation {

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Integer execute(CliOperationContext cliOperationContext) {
        var cliApp = cliOperationContext.cliApp();
        var clientCluster = cliApp.getClusterAlias();
        var ck8s = cliOperationContext.ck8sPath();

        if (needsConfirmation(cliApp, cliApp.getFlow(), clientCluster)) {
            return -1;
        }

        var flowExecutor = new ConcordCliFlowExecutor(ck8s, new Verbosity(cliApp.getVerbosity()), cliApp.getSecretsProvider(), cliApp.isWithLocalDependencies(),
                cliApp.getProfile(), cliApp.getEventsDir(), cliApp.isDryRunMode(), cliApp.getTargetRootPath());

        var process = flowExecutor.execute(clientCluster, cliApp.getFlow(), (Map) cliApp.getExtraVars(), cliApp.getActiveProfiles());
        if (process == null) {
            return -1;
        }

        return 0;
    }
}
