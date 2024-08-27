package dev.ybrig.ck8s.cli.executor;

import com.walmartlabs.concord.runtime.v2.model.FlowCall;
import com.walmartlabs.concord.runtime.v2.runner.context.ContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.vm.FlowCallCommand;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import java.io.Serializable;
import java.util.Map;

public class FlowCallParamsLogger
        implements ExecutionListener
{

    @Override
    public Result beforeCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd)
    {
        if (!(cmd instanceof FlowCallCommand)) {
            return Result.CONTINUE;
        }

        FlowCall flowCall = ((FlowCallCommand) cmd).getStep();
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
