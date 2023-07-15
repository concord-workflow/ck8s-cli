package brig.ck8s.executor;

import brig.ck8s.utils.MapUtils;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.util.Map;

public class FlowRequirementsProcessor extends ConcordYamlProcessor
{
    @Override
    protected Map<String, Object> processRootYaml(ProcessDefinition pd, Map<String, Object> rootYaml) {
        Map<String, Object> requirements = pd.configuration().requirements();
        if (requirements == null) {
            return rootYaml;
        }
        MapUtils.delete(rootYaml, "configuration.requirements");
        return MapUtils.merge(rootYaml, Map.of("configuration", Map.of("requirements", requirements)));
    }
}
