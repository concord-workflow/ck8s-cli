package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.utils.MapUtils;
import brig.ck8s.utils.Mapper;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static brig.ck8s.utils.Ck8sUtils.CONCORD_YAML_PATTERN;

public class FlowRequirementsProcessor
{

    private static ProcessDefinition findFlowYaml(Path flowsDir, String flowName)
    {
        ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());

        try (Stream<Path> walk = Files.walk(flowsDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(CONCORD_YAML_PATTERN))
                    .map(p -> {
                        try {
                            return loader.loadFromFile(p).getProjectDefinition();
                        }
                        catch (IOException e) {
                            throw new RuntimeException("Error loading " + p + ":" + e);
                        }
                    })
                    .filter(pd -> pd.flows().containsKey(flowName))
                    .findFirst().orElse(null);
        }
        catch (IOException e) {
            throw new RuntimeException("find yaml for flow '" + flowName + "' error: " + e.getMessage());
        }
    }

    public Ck8sPayload process(Ck8sPayload payload)
    {
        String flowName = MapUtils.getString(payload.args(), "flow");
        if (flowName == null) {
            return payload;
        }

        ProcessDefinition pd = findFlowYaml(payload.location().resolve("concord"), flowName);
        if (pd == null) {
            return payload;
        }

        Map<String, Object> requirements = pd.configuration().requirements();
        if (requirements == null) {
            return payload;
        }

        Path rootConcordYamlPath = payload.location().resolve("concord.yml");
        Map<String, Object> rootYaml = Mapper.yamlMapper().readMap(rootConcordYamlPath);
        MapUtils.delete(rootYaml, "configuration.requirements");
        rootYaml = MapUtils.merge(rootYaml, Map.of("configuration", Map.of("requirements", requirements)));
        Mapper.yamlMapper().write(rootConcordYamlPath, rootYaml);

        return payload;
    }
}
