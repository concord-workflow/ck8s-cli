package brig.ck8s.cli.executor.cli;

import com.walmartlabs.concord.runtime.v2.model.FlowCall;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.vm.StepCommand;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Command;
import com.walmartlabs.concord.svm.ExecutionListener;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;
import com.walmartlabs.concord.svm.VM;

import java.io.Serializable;
import java.util.Map;

public class FlowCallParamsLogger
        implements ExecutionListener
{

    @Override
    public Result beforeCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd)
    {
        if (!(cmd instanceof StepCommand<?> stepCommand)) {
            return Result.CONTINUE;
        }

        Step step = stepCommand.getStep();
        if (!(step instanceof FlowCall flowCall)) {
            return Result.CONTINUE;
        }

        if (flowCall.getOptions() == null) {
            return Result.CONTINUE;
        }

        Map<String, Serializable> inVars = flowCall.getOptions().input();
        if (!inVars.isEmpty()) {
            ContextFactory contextFactory = runtime.getService(ContextFactory.class);
            Context ctx = contextFactory.create(runtime, state, threadId, flowCall);

            System.out.println("     in: " + ctx.eval(inVars, Map.class));
        }

        return Result.CONTINUE;
    }
}
