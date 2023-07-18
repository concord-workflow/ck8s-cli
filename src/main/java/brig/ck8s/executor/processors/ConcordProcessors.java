package brig.ck8s.executor.processors;

import brig.ck8s.concord.Ck8sPayload;

import java.util.Arrays;
import java.util.List;

public class ConcordProcessors {

    private final List<PayloadProcessor> payloadProcessors = Arrays.asList(
            new FlowRequirementsProcessor(),
            new ConcordArgsProcessor(),
            new FlowExclusiveProcessor());

    public Ck8sPayload process(Ck8sPayload payload) {
        for (PayloadProcessor p : payloadProcessors) {
            payload = p.process(payload);
        }
        return payload;
    }
}
