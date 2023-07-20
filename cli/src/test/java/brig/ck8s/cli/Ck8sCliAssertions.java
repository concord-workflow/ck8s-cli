package brig.ck8s.cli;

import brig.ck8s.cli.assertions.CliAssertions;
import brig.ck8s.cli.assertions.CliExecAssertion;

public class Ck8sCliAssertions
{
    public static CliExecAssertion assertSuccess(String cliArgs)
    {
        return CliAssertions.assertSuccess(() -> Main.main(parseArgs(cliArgs)));
    }

    public static CliExecAssertion assertFailed(String cliArgs)
    {
        return CliAssertions.assertFailed(() -> Main.main(parseArgs(cliArgs)));
    }

    public static CliExecAssertion assertRunAction(String actionName, String expected)
    {
        return assertSuccess("-a %s --dry-run".formatted(actionName))
                .assertOutContainsMatchingLine("Executing action: %s".formatted(expected));
    }

    private static String[] parseArgs(String cliArgs) {
        String[] argsArray;
        if (cliArgs != null) {
            argsArray = cliArgs.split(" ");
        } else {
            argsArray = new String[0];
        }
        return argsArray;
    }
}
