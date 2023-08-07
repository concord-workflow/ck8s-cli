package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayloadForRemote;
import brig.ck8s.cli.common.MapUtils;
import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.el.DefaultExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;

import java.util.Map;

public class FlowExclusiveProcessor extends ConcordYamlProcessor
{
    @Override
    protected Map<String, Object> processRootYaml(Ck8sPayloadForRemote payload, ProcessDefinition pd, Map<String, Object> rootYaml) {
        ExclusiveMode exclusive = pd.configuration().exclusive();
        if (exclusive == null) {
            return rootYaml;
        }

        Map<String, Object> m = MapUtils.merge(pd.configuration().arguments(), payload.args());
        EvalContext evalContext = EvalContext.builder()
                .variables(new MapBackedVariables(m))
                .build();

        DefaultExpressionEvaluator expressionEvaluator = new DefaultExpressionEvaluator(new TaskProviders());
        String group = expressionEvaluator.eval(evalContext, exclusive.group(), String.class);

        MapUtils.delete(rootYaml, "configuration.exclusive");
        return MapUtils.merge(rootYaml, Map.of("configuration", Map.of("exclusive", ExclusiveMode.of(group, exclusive.mode()))));
    }
}
