package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.Ck8sUtils;
import dev.ybrig.ck8s.cli.common.Mapper;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.nio.file.Path;
import java.util.Map;

public abstract class ConcordYamlProcessor implements PayloadProcessor {

    @Override
    public Ck8sPayload process(ProcessorsContext context, Ck8sPayload payload) {
        ProcessDefinition flowProcessDefinition = Ck8sUtils.assertYaml(payload.flows().flowsPath(), context.flowName());
        Path rootConcordYamlPath = payload.flows().rootConcordYaml();
        Map<String, Object> rootYaml = Mapper.yamlMapper().readMap(rootConcordYamlPath);
        rootYaml = processRootYaml(context, payload, flowProcessDefinition, rootYaml);
        Mapper.yamlMapper().write(rootConcordYamlPath, rootYaml);
        return payload;
    }

    protected abstract Map<String, Object> processRootYaml(ProcessorsContext context, Ck8sPayload payload, ProcessDefinition pd, Map<String, Object> rootYaml);
}
