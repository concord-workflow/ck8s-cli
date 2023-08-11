package dev.ybrig.ck8s.cli.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class DefaultDependencies {

    @SuppressWarnings("unchecked")
    public static List<String> load(Ck8sPath path) {
        if (path.ck8sExtDir() == null) {
            return Collections.emptyList();
        }

        Path depsFile = path.ck8sExtDir().resolve("concord-dependencies.yaml");
        if (Files.notExists(depsFile)) {
            return Collections.emptyList();
        }

        return Mapper.yamlMapper().read(depsFile, List.class);
    }
}
