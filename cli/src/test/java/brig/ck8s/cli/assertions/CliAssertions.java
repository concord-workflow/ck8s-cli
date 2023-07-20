package brig.ck8s.cli.assertions;

public class CliAssertions
{
    public static CliExecAssertion assertSuccess(Runnable commandRun)
    {
        return executeCli(commandRun)
                .assertExitCode(code -> code == 0,
                        code -> "Expected 0 exit code but got: " + code)
                .asserErrEmpty();
    }

    public static CliExecAssertion assertFailed(Runnable commandRun)
    {
        return executeCli(commandRun)
                .assertExitCode(code -> code != 0,
                        code -> "Expected non 0 exit code but got: " + code);
    }

    private static CliExecAssertion executeCli(Runnable commandRun)
    {
        try (CliExec grabber = new CliExec()) {
            return grabber.runCommand(commandRun);
        }
    }
}
