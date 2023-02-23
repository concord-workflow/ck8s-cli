package brig.ck8s.utils;

import com.walmartlabs.concord.common.IOUtils;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static brig.ck8s.utils.MapUtils.getString;
import static brig.ck8s.utils.YamlMapper.readMap;

public class Ck8sUtils {

    private static final String CK8S_CLUSTER_YAML_NAME = "cluster.yaml";

    public static Stream<Path> streamClusterYaml(Ck8sPath ck8sPath) {
        return Stream.concat(
                streamClusterYaml(ck8sPath.ck8sOrgDir()),
                streamClusterYaml(ck8sPath.ck8sExtOrgDir()));
    }

    private static Stream<Path> streamClusterYaml(Path root) {
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return Stream.empty();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> CK8S_CLUSTER_YAML_NAME.equals(p.getFileName().toString()))
                    .toList().stream();
        } catch (IOException e) {
            throw new RuntimeException("stream cluster yaml error: " + e.getMessage());
        }
    }

    public static Path findClusterYamlByAlias(Ck8sPath ck8sPath, String alias) {
        return findClusterYamlBy(ck8sPath, cluster -> alias.equals(getString(cluster, "alias")));
    }

    public static Path findClusterYamlBy(Ck8sPath ck8sPath, Predicate<Map<String, Object>> filter) {
        return streamClusterYaml(ck8sPath)
                .filter(p -> filter.test(readMap(p)))
                .findFirst()
                .orElse(null);
    }

    public static Map<String, Object> buildConcordYaml(Ck8sPath ck8sPath, Path clusterYaml, Map<String, Object> concordYmlTemplate) {

        Path defaultCfg = ck8sPath.defaultCfg();
        Path organizationYamlPath = ck8sPath.orgCfgForCluster(clusterYaml);
        Path accountYamlPath = ck8sPath.accountCfgForCluster(clusterYaml);

        Map<String, Object> concordYml = new HashMap<>(concordYmlTemplate);
        Map<String, Object> merged = merge(defaultCfg, organizationYamlPath, accountYamlPath, clusterYaml);
        MapUtils.set(concordYml, merged, "configuration.arguments.clusterRequest");

        return concordYml;
    }

    public static void copyComponents(Ck8sPath ck8sPath, Path target) {
        copyComponents(ck8sPath.ck8sComponents(), ck8sPath.ck8sExtComponents(), target, "ck8s-components");
    }

    public static void copyTestComponents(Ck8sPath ck8sPath, Path target) {
        copyComponents(ck8sPath.ck8sComponentsTests(), ck8sPath.ck8sExtComponentsTests(), target, "ck8s-components-tests");
    }

    private static void copyComponents(Path sourceCk8sComponents, Path sourceCk8sExtComponents, Path target, String componentsDirName) {
        Path concordDir = target.resolve("concord");
        try {
            if (Files.notExists(concordDir)) {
                Files.createDirectory(concordDir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error creating concord target dir: " + e.getMessage());
        }

        Path componentsDir = target.resolve(componentsDirName);
        try {
            Files.createDirectory(componentsDir);
        } catch (IOException e) {
            throw new RuntimeException("Error creating '" + componentsDirName + "' target dir: " + e.getMessage());
        }

        try {
            copyComponentsYaml(sourceCk8sComponents, concordDir);
            IOUtils.copy(sourceCk8sComponents, componentsDir, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Error copying ck8s components '" + sourceCk8sComponents + "' to '" + componentsDir + "': " + e.getMessage(), e);
        }

        if (sourceCk8sExtComponents != null && Files.isDirectory(sourceCk8sExtComponents)) {
            try {
                copyComponentsYaml(sourceCk8sExtComponents, concordDir);
                IOUtils.copy(sourceCk8sExtComponents, componentsDir, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Error copying ck8sExt components '" + sourceCk8sExtComponents + "' to '" + componentsDir + "': " + e.getMessage());
            }
        }
    }

    private static Map<String, Object> merge(Path ... yamls) {
        Map<String, Object> merged = null;
        for (Path p : yamls) {
            if (Files.exists(p)) {
                merged = MapUtils.merge(merged, YamlMapper.readMap(p));
            }
        }
        return merged;
    }

    private static void copyComponentsYaml(Path src, Path dest) throws IOException {
        try (Stream<Path> walk = Files.walk(src)) {
            walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("ck8s-.*\\.concord\\.yaml"))
                    .forEach(p -> copy(p, dest.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING));
        }
    }

    private static void copy(Path source, Path target, CopyOption... options) {
        try {
            Files.copy(source, target, options);
        } catch (IOException e) {
            throw new RuntimeException("Error copy '" + source + "' to '" + target + "':" + e.getMessage());
        }
    }
}
