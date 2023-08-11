package dev.ybrig.ck8s.cli.op;

import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.executor.ExecContext;
import dev.ybrig.ck8s.cli.executor.FlowExecutor;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import dev.ybrig.ck8s.cli.common.Ck8sFlowBuilder;
import dev.ybrig.ck8s.cli.common.Ck8sFlows;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.verify.CheckError;
import dev.ybrig.ck8s.cli.common.verify.Ck8sPayloadVerifier;
import com.walmartlabs.concord.cli.Verbosity;

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
        Ck8sPath ck8s = cliOperationContext.ck8sPath();
        Verbosity verbosity = cliOperationContext.verbosity();
        String flow = cliApp.getFlow();
        String profile = cliApp.getProfile();
        String clusterAlias = cliApp.getClusterAlias();

        boolean needConfirmation = nonNull(flow)
                && flowPatternsToConfirm.stream()
                .anyMatch(flow::matches);
        if (needConfirmation) {
            String msg = String.format("Are you sure you want to execute '%s' on '%s' cluster? (y/N): ", flow, clusterAlias);
            System.out.print(msg);

            try (Scanner input = new Scanner(System.in)) {
                String confirm = input.nextLine();
                if (!confirmInput.contains(confirm)) {
                    return -1;
                }
            }
        }

        List<String> deps = Collections.emptyList();
        if (cliApp.isWithInputAssert()) {
            deps = List.of("mvn://com.walmartlabs.concord.plugins.basic:input-params-assert:1.102.1-SNAPSHOT");
        }

        Ck8sPayloadVerifier verifier = new Ck8sPayloadVerifier();

        Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8s, cliApp.getTargetRootPath(), verifier)
                .includeTests(cliApp.isWithTests())
                .withDependencies(deps)
                .debug(verbosity.verbose())
                .build(clusterAlias);

        assertNoErrors(ck8s, verifier.errors());

        // TODO: restore original logic: executor also has testMode
        if (cliApp.isTestMode()) {
            LogUtils.info("Running flow: {} on cluster: {} with profile: {}", flow, clusterAlias, profile);
            return 0;
        }

        Ck8sPayload payload = Ck8sPayload.builder()
                .flows(ck8sFlows)
                .ck8sPath(ck8s)
                .build();

        ExecContext execContext = ExecContext.builder()
                .verbosity(verbosity)
                .profile(profile)
                .testMode(cliApp.isTestMode())
                .build();

        return new FlowExecutor().execute(cliApp.getFlowExecutorType().getType(),
                execContext, payload, cliApp.getFlow());
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
