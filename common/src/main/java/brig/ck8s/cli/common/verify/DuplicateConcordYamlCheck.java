package brig.ck8s.cli.common.verify;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DuplicateConcordYamlCheck extends AbstractChecker {

    // filename -> Path
    private final Map<String, Path> processedFiles = new HashMap<>();

    public void process(Path concordYaml) {
        String fileName = concordYaml.getFileName().toString();
        if (processedFiles.containsKey(fileName)) {
            addError(concordYaml, "Duplicate file: " + processedFiles.get(fileName));
        }

        processedFiles.put(fileName, concordYaml);
    }
}
