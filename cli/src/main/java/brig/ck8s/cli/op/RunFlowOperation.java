package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.subcom.pack.Ck8sPackageBuilder;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;

import static brig.ck8s.cli.common.Ck8sPayload.createClusterConcordYamlFileName;
import static brig.ck8s.cli.common.IOUtils.deleteRecursively;
import static brig.ck8s.cli.executor.FlowExecutorType.resolveFlowExecutor;
import static java.nio.file.Files.move;
import static java.nio.file.Files.walk;
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

        Ck8sPayload ck8sPayload = Ck8sPackageBuilder
                .builder(cliOperationContext)
                .build();

        Path packageDir = ck8sPayload.location();
        String clusterConcordYamlFileName = createClusterConcordYamlFileName(clusterAlias);
        try {
            walk(packageDir, 1)
                    // Filter out all cluster concord.yml
                    .filter(Ck8sPayload::isClusterConcordYaml)
                    // Filter out wanted cluster concord.yml
                    .filter(file -> !file.getFileName().toString().equals(clusterConcordYamlFileName))
                    // We delete other cluster concord.yml
                    .forEach(clusterConcordYaml -> deleteRecursively(clusterConcordYaml));
            move(packageDir.resolve(clusterConcordYamlFileName), packageDir.resolve("concord.yml"));
        }
        catch (Exception e) {
            throw new RuntimeException(
                    "Failed to select cluster alias: %s yaml file % for root concord.yml"
                            .formatted(clusterAlias, clusterConcordYamlFileName),
                    e);
        }

        if (cliApp.isTestMode()) {
            Path ck8sPackagePath = cliApp.getCk8sPackagePath();
            System.out.println(
                    "Running flow: %s on cluster: %s with profile: %s%s".formatted(
                            flow,
                            clusterAlias,
                            cliApp.getProfile(),
                            nonNull(ck8sPackagePath) ? " and package: %s".formatted(ck8sPackagePath) : ""));
            return 0;
        }

        return resolveFlowExecutor(cliOperationContext)
                .execute(ck8sPayload);
    }
}
