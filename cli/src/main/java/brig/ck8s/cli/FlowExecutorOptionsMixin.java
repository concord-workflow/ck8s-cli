package brig.ck8s.cli;

import brig.ck8s.cli.executor.FlowExecutorType;
import brig.ck8s.cli.utils.EnumCompletionCandidates;
import brig.ck8s.cli.utils.EnumConverter;
import picocli.CommandLine;

@CommandLine.Command
@SuppressWarnings("unused")
public class FlowExecutorOptionsMixin
        extends BaseMixin<FlowExecutorOptionsMixin>
{

    private FlowExecutorType type = FlowExecutorType.REMOTE;

    public FlowExecutorType getType()
    {
        return rootMixin().type;
    }

    @CommandLine.Option(names = {
            "--flow-executor"}, description = "flow executor: ${COMPLETION-CANDIDATES}", completionCandidates = ExecutorTypeCompletionCandidates.class, converter = ExecutorTypeTypeConverter.class)
    public void setType(FlowExecutorType type)
    {
        rootMixin().type = type;
    }

    static class ExecutorTypeCompletionCandidates
            extends EnumCompletionCandidates<FlowExecutorType>
    {

        public ExecutorTypeCompletionCandidates()
        {
            super(FlowExecutorType.class);
        }
    }

    static class ExecutorTypeTypeConverter
            extends EnumConverter<FlowExecutorType>
    {

        public ExecutorTypeTypeConverter()
        {
            super(FlowExecutorType.class);
        }
    }
}
