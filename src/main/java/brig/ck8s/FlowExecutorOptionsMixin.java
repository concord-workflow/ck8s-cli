package brig.ck8s;

import brig.ck8s.executor.FlowExecutor;
import brig.ck8s.utils.EnumCompletionCandidates;
import brig.ck8s.utils.EnumConverter;
import picocli.CommandLine;

@CommandLine.Command
@SuppressWarnings("unused")
public class FlowExecutorOptionsMixin extends BaseMixin<FlowExecutorOptionsMixin> {

    private FlowExecutor.ExecutorType type = FlowExecutor.ExecutorType.REMOTE;

    @CommandLine.Option(names = {"--flow-executor"}, description = "flow executor: ${COMPLETION-CANDIDATES}", completionCandidates = ExecutorTypeCompletionCandidates.class, converter = ExecutorTypeTypeConverter.class)
    public void setType(FlowExecutor.ExecutorType type) {
        rootMixin().type = type;
    }

    public FlowExecutor.ExecutorType getType() {
        return rootMixin().type;
    }

    static class ExecutorTypeCompletionCandidates extends EnumCompletionCandidates<FlowExecutor.ExecutorType> {

        public ExecutorTypeCompletionCandidates() {
            super(FlowExecutor.ExecutorType.class);
        }
    }

    static class ExecutorTypeTypeConverter extends EnumConverter<FlowExecutor.ExecutorType> {

        public ExecutorTypeTypeConverter() {
            super(FlowExecutor.ExecutorType.class);
        }
    }

}
