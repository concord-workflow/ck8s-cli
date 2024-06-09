package dev.ybrig.ck8s.cli.op;

import com.walmartlabs.concord.cli.Verbosity;
import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.common.verify.CheckError;
import dev.ybrig.ck8s.cli.common.verify.Ck8sPayloadVerifier;
import dev.ybrig.ck8s.cli.executor.FlowExecutor;
import dev.ybrig.ck8s.cli.executor.FlowExecutorFactory;
import dev.ybrig.ck8s.cli.executor.FlowExecutorParams;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.util.*;

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

        boolean needConfirmation = !cliOperationContext.cliApp().isSkipConfirm() && nonNull(flow)
                && flowPatternsToConfirm.stream()
                .anyMatch(flow::matches) ;
        if (needConfirmation) {
            String msg = String.format("Are you sure you want to execute '%s' on '%s' cluster? (y/N): ", flow, cliApp.getClusterAlias());
            System.out.print(msg);

            try (Scanner input = new Scanner(System.in)) {
                String confirm = input.nextLine();
                if (!confirmInput.contains(confirm)) {
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

        Ck8sPayload payload = Ck8sPayload.builder()
                .debug(cliOperationContext.verbosity().verbose())
                .arguments(MapUtils.merge(Map.of("clusterRequest", clusterRequest), cliApp.getExtraVars()))
                .ck8sFlows(ck8sFlows)
                .build();

        if (cliApp.isTestMode()) {
            LogUtils.info("Running flow: {} on cluster: {} with profile: {}", flow, cliOperationContext.cliApp().getClusterAlias(), cliOperationContext.cliApp().getProfile());
            return 0;
        }

        FlowExecutorParams executorParams = FlowExecutorParams.builder()
                .executorType(cliApp.getFlowExecutorType().getType())
                .concordProfile(cliApp.getProfile())
                .clusterAlias(cliApp.getClusterAlias())
                .activeProfiles(cliApp.getActiveProfiles())
                .secretProvider(cliApp.getSecretsProvider())
                .verbosity(new Verbosity(cliApp.getVerbosity()))
                .build();

        FlowExecutor flowExecutor = new FlowExecutorFactory().create(executorParams);
        return flowExecutor.execute(payload, flow, Collections.emptyList());
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
