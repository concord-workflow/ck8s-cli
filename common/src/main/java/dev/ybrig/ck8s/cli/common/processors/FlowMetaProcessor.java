package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.MapUtils;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import dev.ybrig.ck8s.cli.common.eval.ExpressionEvaluator;

import java.util.Map;

/**
 * grab meta info from target concord.yaml
 */
public class FlowMetaProcessor extends ConcordYamlProcessor
{
    @Override
    protected Map<String, Object> processRootYaml(ProcessorsContext context, Ck8sPayload payload, ProcessDefinition pd, Map<String, Object> rootYaml) {
        Map<String, Object> meta = pd.configuration().meta();
        if (meta.isEmpty()) {
            return rootYaml;
        }

        Map<String, Object> variables = MapUtils.merge(pd.configuration().arguments(), payload.args());

        Map<String, Object> evalMeta = ExpressionEvaluator.getInstance().evalMap(variables, meta);

        return MapUtils.merge(rootYaml, Map.of("configuration", Map.of("meta", evalMeta)));
    }
}
