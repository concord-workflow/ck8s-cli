package brig.ck8s.cli;

import brig.ck8s.cli.assertions.CliAssertions;
import brig.ck8s.cli.assertions.CliExecAssertion;
import dev.ybrig.ck8s.cli.Main;

import java.util.Arrays;
import java.util.stream.Stream;

public class Ck8sCliAssertions {
    public static CliExecAssertion assertSuccess(String cliArgs) {
        return CliAssertions.assertSuccess(() -> Main.main(prepareArgs(cliArgs)));
    }

    public static CliExecAssertion assertFailed(String cliArgs) {
        return CliAssertions.assertFailed(() -> Main.main(prepareArgs(cliArgs)));
    }

    public static CliExecAssertion assertRunAction(String actionName, String expected) {
        return assertSuccess(String.format("-a %s --dry-run", actionName))
                .assertOutContainsMatchingLine(String.format("Executing action: %s", expected));
    }

    private static String[] prepareArgs(String cliArgs) {
        String[] argsArray;
        if (cliArgs != null) {
            argsArray = cliArgs.split(" ");
        } else {
            argsArray = new String[0];
        }
        return Stream.concat(Stream.of("--no-color"), Arrays.stream(argsArray)).toArray(String[]::new);
    }
}
