package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.common.MapUtils;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.el.DefaultExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;

import java.util.Map;

public class FlowMetaProcessor extends ConcordYamlProcessor
{
    @Override
    protected Map<String, Object> processRootYaml(Ck8sPayload payload, ProcessDefinition pd, Map<String, Object> rootYaml) {
        Map<String, Object> meta = MapUtils.merge(pd.configuration().meta(), payload.meta());
        if (meta.isEmpty()) {
            return rootYaml;
        }

        Map<String, Object> variables = MapUtils.merge(pd.configuration().arguments(), payload.args());
        EvalContext evalContext = EvalContext.builder()
                .variables(new MapBackedVariables(variables))
                .build();

        DefaultExpressionEvaluator expressionEvaluator = new DefaultExpressionEvaluator(new TaskProviders());
        Map<String, Object> evalMeta = expressionEvaluator.evalAsMap(evalContext, meta);

        return MapUtils.merge(rootYaml, Map.of("configuration", Map.of("meta", evalMeta)));
    }
}
