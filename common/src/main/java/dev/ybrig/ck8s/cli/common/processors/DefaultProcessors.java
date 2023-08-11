package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;

import java.util.Arrays;
import java.util.List;

public class DefaultProcessors {

    private final List<PayloadProcessor> payloadProcessors = Arrays.asList(
            new FlowRequirementsProcessor(),
            new FlowExclusiveProcessor(),
            new ProfileProcessor(),
            new FlowMetaProcessor(),
            new Ck8sInfoProcessor(),
            new Ck8sCliVersionProcessor());

    public Ck8sPayload process(Ck8sPayload payload, String flowName) {
        for (PayloadProcessor p : payloadProcessors) {
            payload = p.process(payload, flowName);
        }
        return payload;
    }
}
