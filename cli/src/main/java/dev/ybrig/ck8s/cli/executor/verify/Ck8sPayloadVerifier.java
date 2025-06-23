package dev.ybrig.ck8s.cli.executor.verify;

import com.walmartlabs.concord.process.loader.StandardRuntimeTypes;
import com.walmartlabs.concord.runtime.v2.model.Resources;
import com.walmartlabs.concord.runtime.v2.parser.YamlParserV2;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Ck8sPayloadVerifier  {

    private final List<Ck8sPayloadChecker> checkers = Arrays.asList(
//            new DuplicateConcordArgsCheck(), // we should ignore args with the same values...
            new DuplicateConcordFlowCheck(),
            new UndefinedFlowCallCheck()
    );

    public void verify(Path workspaceDir) throws IOException {
        var root = loadRoot(workspaceDir);
        if (root == null) {
            throw new RuntimeException("Unable to load root concord.yaml definition");
        }

        var parser = new YamlParserV2();
        var rootProcessDefinition = parser.parse(workspaceDir, root);
        var files = loadResources(workspaceDir, rootProcessDefinition.resources());
        Collections.sort(files);
        files.add(root);

        for (var f : files) {
            for (var checker : checkers) {
                checker.process(f);
            }
        }
    }

    public List<CheckError> errors() {
        List<CheckError> results = new ArrayList<>();
        for (var checker : checkers) {
            results.addAll(checker.errors());
        }
        return results;
    }

    private static List<Path> loadResources(Path baseDir, Resources resources) throws IOException {
        List<Path> result = new ArrayList<>();
        for (var pattern : resources.concord()) {
            var pathMatcher = parsePattern(baseDir, pattern);
            if (pathMatcher != null) {
                try (var w = Files.walk(baseDir)) {
                    w.filter(pathMatcher::matches).forEach(result::add);
                }
            } else {
                var path = Paths.get(concat(baseDir, pattern.trim()));
                if (Files.exists(path)) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private static PathMatcher parsePattern(Path baseDir, String pattern) {
        String normalizedPattern = null;

        pattern = pattern.trim();

        if (pattern.startsWith("glob:")) {
            normalizedPattern = "glob:" + concat(baseDir, pattern.substring("glob:".length()));
        } else if (pattern.startsWith("regex:")) {
            normalizedPattern = "regex:" + concat(baseDir, pattern.substring("regex:".length()));
        }

        if (normalizedPattern != null) {
            return FileSystems.getDefault().getPathMatcher(normalizedPattern);
        }

        return null;
    }

    private static String concat(Path path, String str) {
        var separator = "/";
        if (str.startsWith("/")) {
            separator = "";
        }
        return path.toAbsolutePath() + separator + str;
    }

    private static Path loadRoot(Path baseDir) throws IOException {
        for(var fileName : StandardRuntimeTypes.PROJECT_ROOT_FILE_NAMES) {
            var p = baseDir.resolve(fileName);
            if (Files.exists(p)) {
                return p;
            }
        }

        return null;
    }
}
