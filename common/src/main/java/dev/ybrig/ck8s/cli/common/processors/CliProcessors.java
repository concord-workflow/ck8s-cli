package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.DefaultDependencies;

import java.util.Arrays;
import java.util.List;

public class CliProcessors {

    private final List<PayloadProcessor> payloadProcessors = Arrays.asList(
            new Ck8sInfoProcessor(),
            new Ck8sCliVersionProcessor(),
            new DependenciesProcessor());

    public Ck8sPayload process(Ck8sPayload payload, String flowName) {
        ProcessorsContext context = ProcessorsContext.builder()
                .flowName(flowName)
                .addAllDefaultDependencies(DefaultDependencies.load(payload.ck8sPath()))
                .build();

        for (PayloadProcessor p : payloadProcessors) {
            payload = p.process(context, payload);
        }
        return payload;
    }
}
