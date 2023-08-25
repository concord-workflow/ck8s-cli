package brig.ck8s.cli;

import brig.ck8s.cli.assertions.CliAssertions;
import brig.ck8s.cli.assertions.CliExecAssertion;
import dev.ybrig.ck8s.cli.Main;

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
        return assertSuccess(String.format("-a %s --dry-run", actionName))
                .assertOutContainsMatchingLine(String.format("Executing action: %s", expected));
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
