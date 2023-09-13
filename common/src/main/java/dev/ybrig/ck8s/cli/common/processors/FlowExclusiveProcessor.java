package dev.ybrig.ck8s.cli.common.processors;

import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.eval.ExpressionEvaluator;

import java.util.Map;

public class FlowExclusiveProcessor extends ConcordYamlProcessor
{
    @Override
    protected Map<String, Object> processRootYaml(Ck8sPayload payload, ProcessDefinition pd, Map<String, Object> rootYaml) {
        ExclusiveMode exclusive = pd.configuration().exclusive();
        if (exclusive == null) {
            return rootYaml;
        }

        Map<String, Object> m = MapUtils.merge(pd.configuration().arguments(), payload.args());
        String group = ExpressionEvaluator.getInstance().eval(m, exclusive.group(), String.class);

        MapUtils.delete(rootYaml, "configuration.exclusive");
        return MapUtils.merge(rootYaml, Map.of("configuration", Map.of("exclusive", ExclusiveMode.of(group, exclusive.mode()))));
    }
}
