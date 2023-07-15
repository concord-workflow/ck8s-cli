package brig.ck8s.executor;

import brig.ck8s.utils.MapUtils;
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
    protected Map<String, Object> processRootYaml(ProcessDefinition pd, Map<String, Object> rootYaml) {
        ExclusiveMode exclusive = pd.configuration().exclusive();
        if (exclusive == null) {
            return rootYaml;
        }

        EvalContext evalContext = EvalContext.builder()
                .variables(new MapBackedVariables(pd.configuration().arguments()))
                .build();

        DefaultExpressionEvaluator expressionEvaluator = new DefaultExpressionEvaluator(new TaskProviders());
        String group = expressionEvaluator.eval(evalContext, exclusive.group(), String.class);

        MapUtils.delete(rootYaml, "configuration.exclusive");
        return MapUtils.merge(rootYaml, Map.of("configuration", Map.of("exclusive", ExclusiveMode.of(group, exclusive.mode()))));
    }
}
