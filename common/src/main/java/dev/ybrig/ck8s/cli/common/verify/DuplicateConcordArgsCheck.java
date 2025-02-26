package dev.ybrig.ck8s.cli.common.verify;

import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.Mapper;

import java.nio.file.Path;
import java.util.*;

public class DuplicateConcordArgsCheck extends AbstractChecker {

    private final List<AbstractMap.SimpleEntry<Path, Set<String>>> processedArguments = new ArrayList<>();

    @Override
    public void process(Path concordYamlPath) {
        Map<String, Object> concordYaml;
        try {
            concordYaml = Mapper.yamlMapper().readMap(concordYamlPath);
        } catch (Exception e) {
            addError(concordYamlPath, "Can't parse yaml: " + e.getMessage());
            return;
        }

        var arguments = MapUtils.getMap(concordYaml, "configuration.arguments", Map.of()).keySet();
        if (arguments.isEmpty()) {
            return;
        }

        for (Map.Entry<Path, Set<String>> e : processedArguments) {
            Set<String> maybeDuplicateArgs = new HashSet<>(e.getValue());
            maybeDuplicateArgs.retainAll(arguments);

            if (!maybeDuplicateArgs.isEmpty()) {
                var args = String.join(", ", maybeDuplicateArgs);
                addError(concordYamlPath, "Arguments '" + args + "' already defined at " + e.getKey());
            }
        }

        processedArguments.add(new AbstractMap.SimpleEntry<>(concordYamlPath, arguments));
    }
}
