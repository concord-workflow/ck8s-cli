package brig.ck8s.cli.op;

public class DefaultOperation
        implements CliOperation
{
    public Integer execute(CliOperationContext cliOperationContext)
    {
        cliOperationContext.cliApp()
                .getSpec()
                .commandLine()
                .usage(System.out);
        return -1;
    }
}
