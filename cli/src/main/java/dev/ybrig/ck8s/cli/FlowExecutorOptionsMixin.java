package dev.ybrig.ck8s.cli;

import dev.ybrig.ck8s.cli.executor.ExecutorType;
import dev.ybrig.ck8s.cli.utils.EnumCompletionCandidates;
import dev.ybrig.ck8s.cli.utils.EnumConverter;
import picocli.CommandLine;

@CommandLine.Command
@SuppressWarnings("unused")
public class FlowExecutorOptionsMixin
        extends BaseMixin<FlowExecutorOptionsMixin> {

    private ExecutorType type = ExecutorType.REMOTE;

    public ExecutorType getType() {
        return rootMixin().type;
    }

    @CommandLine.Option(names = {
            "--flow-executor"}, description = "flow executor: ${COMPLETION-CANDIDATES}", completionCandidates = ExecutorTypeCompletionCandidates.class, converter = ExecutorTypeTypeConverter.class)
    public void setType(ExecutorType type) {
        rootMixin().type = type;
    }

    static class ExecutorTypeCompletionCandidates
            extends EnumCompletionCandidates<ExecutorType> {

        public ExecutorTypeCompletionCandidates() {
            super(ExecutorType.class);
        }
    }

    static class ExecutorTypeTypeConverter
            extends EnumConverter<ExecutorType> {

        public ExecutorTypeTypeConverter() {
            super(ExecutorType.class);
        }
    }
}
