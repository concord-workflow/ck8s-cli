package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.common.Ck8sFlowBuilder;
import brig.ck8s.cli.common.Ck8sPath;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.executor.FlowExecutor;
import com.walmartlabs.concord.cli.Verbosity;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

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

        Path payloadLocation = new Ck8sFlowBuilder(ck8s, cliApp.getTargetRootPath())
                .includeTests(cliApp.isWithTests())
                .withDependencies(deps)
                .debug(verbosity.verbose())
                .build(clusterAlias);

        Ck8sPayload payload = Ck8sPayload.builder()
                .location(payloadLocation)
                .putAllArgs(cliApp.getExtraVars())
                .flow(cliApp.getFlow())
                .build();

        if (cliApp.isTestMode()) {
            System.out.println("Running flow: %s on cluster: %s with profile: %s".formatted(flow, clusterAlias, profile));
            return 0;
        }

        return new FlowExecutor().execute(cliApp.getFlowExecutorType().getType(), payload, profile, verbosity, cliApp.isTestMode());
    }
}
