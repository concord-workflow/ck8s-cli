package brig.ck8s.cli;

import brig.ck8s.cli.assertions.CliAssertions;
import brig.ck8s.cli.assertions.CliExecAssertion;

public class Ck8sCliAssertions
{
    public static CliExecAssertion assertSuccess(String cliArgs)
    {
        String[] argsArray = cliArgs.split(" ");
        return CliAssertions.assertSuccess(() -> Main.main(argsArray));
    }

    public static CliExecAssertion assertFailed(String cliArgs)
    {
        String[] argsArray = cliArgs.split(" ");
        return CliAssertions.assertFailed(() -> Main.main(argsArray));
    }

    public static CliExecAssertion assertRunAction(String actionName, String expected)
    {
        return assertSuccess("-a %s --dry-run".formatted(actionName))
                .assertOutContainsMatchingLine("Executing action: %s".formatted(expected));
    }
}
