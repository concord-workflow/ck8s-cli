package brig.ck8s.cli.executor;

import brig.ck8s.cli.common.Ck8sPayload;

public interface FlowExecutor
{
    int execute(Ck8sPayload payload);
}
