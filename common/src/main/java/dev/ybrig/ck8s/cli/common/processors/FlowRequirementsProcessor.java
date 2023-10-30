package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.util.HashMap;
import java.util.Map;

public class FlowRequirementsProcessor extends ConcordYamlProcessor
{
    @Override
    protected Map<String, Object> processRootYaml(ProcessorsContext context, Ck8sPayload payload, ProcessDefinition pd, Map<String, Object> rootYaml) {
        Map<String, Object> clusterAliasRequirements = new HashMap<>();
        if (context.clientClusterAlias() != null) {
            clusterAliasRequirements.put("agent", Map.of("clusterAlias", context.clientClusterAlias()));
        } else {
            clusterAliasRequirements.put("agent", Map.of("clusterAlias", payload.flows().clusterAlias()));
        }

        Map<String, Object> requirements = MapUtils.merge(pd.configuration().requirements(), clusterAliasRequirements);
        return MapUtils.merge(rootYaml, Map.of("configuration", Map.of("requirements", requirements)));
    }
}
