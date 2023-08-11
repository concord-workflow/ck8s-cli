package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.Mapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class DependenciesProcessor implements PayloadProcessor {

    @Override
    public Ck8sPayload process(ProcessorsContext context, Ck8sPayload payload) {
        Map<String, String> defaultVersions = parseVersions(context);

        payload.flows().concordYamls().forEach(p -> {
            Map<String, Object> yaml = Mapper.yamlMapper().readMap(p);
            List<String> dependencies = MapUtils.getList(yaml, "configuration.dependencies", Collections.emptyList());

            List<RewriteDep> rewrite = new ArrayList<>();
            for (String dep : dependencies) {
                IdAndVersion depIdAndVersion = IdAndVersion.parse(dep);

                String version = defaultVersions.get(depIdAndVersion.id);
                if (version == null) {
                    throw new RuntimeException("Dependency '" + dep + "' in '" + p + "' is undefined in default dependencies file");
                }

                if (!depIdAndVersion.version.equals(version)) {
                    rewrite.add(new RewriteDep(dep, depIdAndVersion.id + ":" + version));
                }
            }

            rewriteDeps(p, rewrite);
        });

        return payload;
    }

    private static void rewriteDeps(Path p, List<RewriteDep> rewrite) {
        try {
            String content = Files.readString(p);
            for (RewriteDep r : rewrite) {
                content = content.replace(r.oldDep, r.newDep);
            }
            Files.writeString(p, content, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Error rewriting dependencies in '" + p + "': " + e.getMessage());
        }

    }

    private Map<String, String> parseVersions(ProcessorsContext context) {
        Map<String, String> result = new HashMap<>();
        for (String dep : context.defaultDependencies()) {
            IdAndVersion idAndVersion = IdAndVersion.parse(dep);
            result.put(idAndVersion.id, idAndVersion.version);
        }

        return result;
    }

    private static class IdAndVersion {

        public static IdAndVersion parse(String s) {
            int i = s.lastIndexOf(':');
            if (i >= 0 && i + 1 < s.length()) {
                String id = s.substring(0, i);
                String v = s.substring(i + 1);
                return new IdAndVersion(id, v);
            }

            throw new IllegalArgumentException("Invalid dependency format: " + s);
        }

        private final String id;
        private final String version;

        private IdAndVersion(String id, String version) {
            this.id = id;
            this.version = version;
        }
    }

    private static class RewriteDep {

        private final String oldDep;
        private final String newDep;

        private RewriteDep(String oldDep, String newDep) {
            this.oldDep = oldDep;
            this.newDep = newDep;
        }
    }
}
