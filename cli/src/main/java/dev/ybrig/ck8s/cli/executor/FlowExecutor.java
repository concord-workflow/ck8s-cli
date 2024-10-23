package dev.ybrig.ck8s.cli.executor;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;

import java.util.List;

public interface FlowExecutor {

    ConcordProcess execute(Ck8sPayload payload, String flowName, List<String> activeProfiles);
}
