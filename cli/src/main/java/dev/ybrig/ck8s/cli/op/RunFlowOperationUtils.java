package dev.ybrig.ck8s.cli.op;

import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.executor.verify.CheckError;
import dev.ybrig.ck8s.cli.executor.verify.Ck8sPayloadVerifier;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public final class RunFlowOperationUtils {

    private static final Set<String> FLOW_PATTERS_TO_CONFIRM = Set.of("(?i).*delete.*", "(?i).*reinstall.*");

    private static final Set<String> CONFIRM_INPUT = Set.of("y", "yes");

    public static boolean needsConfirmation(CliApp cliApp, String flow, String clientCluster) {
        var needConfirmation = !cliApp.isDryRunMode() && !cliApp.isSkipConfirm() && FLOW_PATTERS_TO_CONFIRM.stream().anyMatch(flow::matches);

        if (needConfirmation) {
            var msg = String.format("Are you sure you want to execute '%s' on '%s' cluster? (y/N): ", flow, clientCluster);
            System.out.print(msg);

            try (var input = new Scanner(System.in)) {
                return input.hasNextLine() && !CONFIRM_INPUT.contains(input.nextLine().toLowerCase());
            }
        }

        return false;
    }

    public static void validate(Path workspaceDir) {
        Ck8sPayloadVerifier verifier = new Ck8sPayloadVerifier();

        try {
            verifier.verify(workspaceDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        assertNoErrors(workspaceDir, verifier.errors());
    }

    private static void assertNoErrors(Path workspaceDir, List<CheckError> errors) {
        var hasErrors = false;
        for (var error : errors) {
            LogUtils.error("processing '" + workspaceDir.relativize(error.concordYaml()) + ": " + error.message());
            hasErrors = hasErrors || !errors.isEmpty();
        }
        if (hasErrors) {
            throw new RuntimeException("Payload has errors");
        }
    }

    private RunFlowOperationUtils() {
    }
}
