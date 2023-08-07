package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayloadForRemote;

import java.util.Arrays;
import java.util.List;

public class ConcordProcessors {

    private final List<PayloadProcessor> payloadProcessors = Arrays.asList(
            new FlowRequirementsProcessor(),
            new ConcordArgsProcessor(),
            new FlowExclusiveProcessor(),
            new FlowMetaProcessor(),
            new Ck8sInfoProcessor());

    public Ck8sPayloadForRemote process(Ck8sPayloadForRemote payload) {
        for (PayloadProcessor p : payloadProcessors) {
            payload = p.process(payload);
        }
        return payload;
    }
}
