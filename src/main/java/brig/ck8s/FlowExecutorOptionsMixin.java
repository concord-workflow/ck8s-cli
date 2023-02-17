package brig.ck8s;

import brig.ck8s.executor.FlowExecutor;
import picocli.CommandLine;

@CommandLine.Command
@SuppressWarnings("unused")
public class FlowExecutorOptionsMixin extends BaseMixin<FlowExecutorOptionsMixin> {

    private FlowExecutor.ExecutorType type = FlowExecutor.ExecutorType.REMOTE;

    @CommandLine.Option(names = {"--flow-executor"}, description = "flow executor: ${COMPLETION-CANDIDATES}")
    public void setType(FlowExecutor.ExecutorType type) {
        rootMixin().type = type;
    }

    public FlowExecutor.ExecutorType getType() {
        return rootMixin().type;
    }
}
