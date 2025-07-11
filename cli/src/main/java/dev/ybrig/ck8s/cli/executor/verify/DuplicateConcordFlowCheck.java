package dev.ybrig.ck8s.cli.executor.verify;

import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.Mapper;

import java.nio.file.Path;
import java.util.*;

public class DuplicateConcordFlowCheck extends AbstractChecker {

    private final List<AbstractMap.SimpleEntry<Path, Set<String>>> processedFlows = new ArrayList<>();

    public void process(Path concordYamlPath) {
        Map<String, Object> concordYaml;
        try {
            concordYaml = Mapper.yamlMapper().readMap(concordYamlPath);
        } catch (Exception e) {
            addError(concordYamlPath, "Can't parse yaml: " + e.getMessage());
            return;
        }

        var flows = MapUtils.getMap(concordYaml, "flows", Map.of()).keySet();
        if (flows.isEmpty()) {
            return;
        }

        for (Map.Entry<Path, Set<String>> e : processedFlows) {
            Set<String> maybeDuplicateFlows = new HashSet<>(e.getValue());
            maybeDuplicateFlows.retainAll(flows);

            if (!maybeDuplicateFlows.isEmpty()) {
                var f = String.join(", ", maybeDuplicateFlows);
                addError(concordYamlPath, "Flow '" + f + "' already defined at " + e.getKey());
            }
        }

        processedFlows.add(new AbstractMap.SimpleEntry<>(concordYamlPath, flows));
    }
}
