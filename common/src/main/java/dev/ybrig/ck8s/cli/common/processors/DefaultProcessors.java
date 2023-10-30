package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.DefaultDependencies;

import java.util.Arrays;
import java.util.List;

public class DefaultProcessors {

    private final List<PayloadProcessor> payloadProcessors = Arrays.asList(
            new FlowRequirementsProcessor(),
            new FlowExclusiveProcessor(),
            new ProfileProcessor(),
            new FlowMetaProcessor(),
            new Ck8sInfoProcessor(),
            new Ck8sCliVersionProcessor(),
            new DependenciesProcessor());

    public Ck8sPayload process(Ck8sPayload payload, String flowName) {
        return process(null, payload, flowName, null, null, false);
    }

    public Ck8sPayload process(String clientClusterAlias, Ck8sPayload payload, String flowName, String defaultOrg, String defaultProject, boolean projectPerCluster) {
        ImmutableProcessorsContext.Builder contextBuilder = ProcessorsContext.builder()
                .flowName(flowName)
                .clientClusterAlias(clientClusterAlias)
                .projectPerCluster(projectPerCluster)
                .addAllDefaultDependencies(DefaultDependencies.load(payload.ck8sPath()));

        if (defaultOrg != null && defaultProject != null) {
            contextBuilder
                    .defaultOrg(defaultOrg)
                    .defaultProject(defaultProject);
        }

        ProcessorsContext context = contextBuilder.build();

        for (PayloadProcessor p : payloadProcessors) {
            payload = p.process(context, payload);
        }
        return payload;
    }
}
