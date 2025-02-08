package dev.ybrig.ck8s.cli.op;

import com.walmartlabs.concord.cli.Verbosity;
import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.common.verify.CheckError;
import dev.ybrig.ck8s.cli.common.verify.Ck8sPayloadVerifier;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.FlowExecutor;
import dev.ybrig.ck8s.cli.executor.FlowExecutorFactory;
import dev.ybrig.ck8s.cli.executor.FlowExecutorParams;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import picocli.CommandLine;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.ybrig.ck8s.cli.common.DefaultArgs.GROUP_CLUSTER_REQUESTS;
import static java.util.Objects.nonNull;

public class RunFlowOperation
        implements CliOperation
{
    private static final Set<String> flowPatternsToConfirm = Set.of("(?i).*delete.*", "(?i).*reinstall.*");
    private static final Set<String> confirmInput = Set.of("y", "yes");

    public Integer execute(CliOperationContext cliOperationContext)
    {
        CliApp cliApp = cliOperationContext.cliApp();
        String flow = cliApp.getFlow();
        if (cliApp.getClusterAlias() == null) {
            throw new CommandLine.ParameterException(new CommandLine(cliApp), "Missing required option: '--cluster=<clusterAlias>'");
        }

        boolean needConfirmation = !cliOperationContext.cliApp().isSkipConfirm() && nonNull(flow)
                && flowPatternsToConfirm.stream()
                .anyMatch(flow::matches) ;
        if (needConfirmation) {
            String msg = String.format("Are you sure you want to execute '%s' on '%s' cluster? (y/N): ", flow, cliApp.getClusterAlias());
            System.out.print(msg);

            try (Scanner input = new Scanner(System.in)) {
                if (input.hasNextLine()) {
                    String confirm = input.nextLine();
                    if (!confirmInput.contains(confirm)) {
                        return -1;
                    }
                } else {
                    return -1;
                }
            }
        }

        Ck8sPayloadVerifier verifier = new Ck8sPayloadVerifier();
        Ck8sPath ck8s = cliOperationContext.ck8sPath();

        Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8s, cliApp.getTargetRootPath(), verifier)
                .includeTests(cliApp.isWithTests())
                .build();

        assertNoErrors(ck8s, verifier.errors());

        Map<String, Object> clusterRequest = Ck8sUtils.buildClusterRequest(ck8s, cliOperationContext.cliApp().getClusterAlias());
        var clusterGroup = MapUtils.getString(clusterRequest, "clusterGroup.alias");
        var groupClusterRequests = Ck8sUtils.findClustersYaml(ck8s, clusterGroup).stream()
                .map(c -> Ck8sUtils.buildClusterRequest(ck8s, c))
                .toList();

        Ck8sPayload payload = Ck8sPayload.builder()
                .debug(cliOperationContext.verbosity().verbose())
                .arguments(MapUtils.merge(Map.of("clusterRequest", clusterRequest), cliApp.getExtraVars()))
                .putArguments(GROUP_CLUSTER_REQUESTS, groupClusterRequests)
                .ck8sFlows(ck8sFlows)
                .project(cliOperationContext.cliApp().getProject())
                .build();

        FlowExecutorParams executorParams = FlowExecutorParams.builder()
                .executorType(cliApp.getFlowExecutorType().getType())
                .concordProfile(cliApp.getProfile())
                .clusterAlias(cliApp.getClusterAlias())
                .secretProvider(cliApp.getSecretsProvider())
                .verbosity(new Verbosity(cliApp.getVerbosity()))
                .useLocalDependencies(cliApp.isWithLocalDependencies())
                .connectTimeout(cliApp.getConnectTimeout())
                .responseTimeout(cliApp.getReadTimeout())
                .eventsPath(cliApp.getEventsDir())
                .isDryRunMode(cliApp.isDryRunMode())
                .build();

        FlowExecutor flowExecutor = new FlowExecutorFactory().create(executorParams);
        ConcordProcess process = flowExecutor.execute(payload, flow, cliApp.getActiveProfiles());
        if (process == null) {
            return -1;
        }

        if (cliApp.isStreamLogs()) {
            ExecutorService executor = Executors.newCachedThreadPool();
            try {
                process.streamLogs(executor);
            } finally {
                executor.shutdownNow();
            }
        }

        if (cliApp.getWaitSeconds() != null && cliApp.getWaitSeconds() > 0) {
            process.waitEnded(cliApp.getWaitSeconds() * 1000);
        }

        return 0;
    }

    private void assertNoErrors(Ck8sPath ck8sPath, List<CheckError> errors) {
        boolean hasErrors = false;
        for (CheckError error : errors) {
            LogUtils.error("processing '" + ck8sPath.relativize(error.concordYaml()) + ": " + error.message());
            hasErrors = hasErrors || !errors.isEmpty();
        }
        if (hasErrors) {
            throw new RuntimeException("Payload has errors");
        }
    }
}
