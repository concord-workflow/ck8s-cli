package dev.ybrig.ck8s.cli;

import dev.ybrig.ck8s.cli.executor.FlowExecutor;
import dev.ybrig.ck8s.cli.utils.EnumCompletionCandidates;
import dev.ybrig.ck8s.cli.utils.EnumConverter;
import picocli.CommandLine;

@CommandLine.Command
@SuppressWarnings("unused")
public class FlowExecutorOptionsMixin
        extends BaseMixin<FlowExecutorOptionsMixin>
{

    private FlowExecutor.ExecutorType type = FlowExecutor.ExecutorType.REMOTE;

    public FlowExecutor.ExecutorType getType()
    {
        return rootMixin().type;
    }

    @CommandLine.Option(names = {
            "--flow-executor"}, description = "flow executor: ${COMPLETION-CANDIDATES}", completionCandidates = ExecutorTypeCompletionCandidates.class, converter = ExecutorTypeTypeConverter.class)
    public void setType(FlowExecutor.ExecutorType type)
    {
        rootMixin().type = type;
    }

    static class ExecutorTypeCompletionCandidates
            extends EnumCompletionCandidates<FlowExecutor.ExecutorType>
    {

        public ExecutorTypeCompletionCandidates()
        {
            super(FlowExecutor.ExecutorType.class);
        }
    }

    static class ExecutorTypeTypeConverter
            extends EnumConverter<FlowExecutor.ExecutorType>
    {

        public ExecutorTypeTypeConverter()
        {
            super(FlowExecutor.ExecutorType.class);
        }
    }
}
