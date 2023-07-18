package brig.ck8s.cli.op;

public interface CliOperation
{
    Integer execute(CliOperationContext cliOperationContext);
}
