package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.common.Ck8sUtils;
import brig.ck8s.cli.common.Mapper;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.nio.file.Path;
import java.util.Map;

public abstract class ConcordYamlProcessor implements PayloadProcessor {

    @Override
    public Ck8sPayload process(Ck8sPayload payload) {
        String flowName = payload.flowName();
        if (flowName == null) {
            return payload;
        }

        ProcessDefinition flowProcessDefinition = Ck8sUtils.findYaml(payload.flowsPath(), flowName);
        if (flowProcessDefinition == null) {
            return payload;
        }

        Path rootConcordYamlPath = payload.rootConcordYaml();
        Map<String, Object> rootYaml = Mapper.yamlMapper().readMap(rootConcordYamlPath);
        rootYaml = processRootYaml(payload, flowProcessDefinition, rootYaml);
        Mapper.yamlMapper().write(rootConcordYamlPath, rootYaml);
        return payload;
    }

    protected abstract Map<String, Object> processRootYaml(Ck8sPayload payload, ProcessDefinition pd, Map<String, Object> rootYaml);
}