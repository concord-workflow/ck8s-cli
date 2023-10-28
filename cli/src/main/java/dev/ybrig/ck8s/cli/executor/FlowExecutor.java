package dev.ybrig.ck8s.cli.executor;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;

import java.util.List;

public interface FlowExecutor {

    int execute(Ck8sPayload payload, String flowName, List<String> activeProfiles);
}
