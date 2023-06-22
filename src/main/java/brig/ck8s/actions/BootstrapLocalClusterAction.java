package brig.ck8s.actions;

import brig.ck8s.cfg.CliConfigurationProvider;
import brig.ck8s.concord.Ck8sFlowBuilder;
import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.concord.ConcordProcess;
import brig.ck8s.executor.RemoteFlowExecutor;
import brig.ck8s.utils.Ck8sPath;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootstrapLocalClusterAction
{

    private final Ck8sPath ck8s;
    private final Path targetRoot;
    private final String profile;

    public BootstrapLocalClusterAction(Ck8sPath ck8s, Path targetRoot, String profile)
    {
        this.ck8s = ck8s;
        this.targetRoot = targetRoot;
        this.profile = profile;
    }

    public int perform()
    {
        ExecuteScriptAction scriptAction = new ExecuteScriptAction(ck8s);

        scriptAction.perform("ck8sDown");
        scriptAction.perform("ck8sUp");

        Path payloadLocation = new Ck8sFlowBuilder(ck8s, targetRoot)
                .build("local");

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

        scriptAction.perform("assertLocalCluster");

        return 0;
    }
}
