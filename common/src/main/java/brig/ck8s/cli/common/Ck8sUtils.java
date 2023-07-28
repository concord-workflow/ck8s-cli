package brig.ck8s.cli.common;

import brig.ck8s.cli.common.metadata.Ck8sMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Ck8sUtils
{
    private static ObjectMapper JSON_MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module());

    private static final String METADATA_JSON = "metadata.json";
    private static final String CONCORD_YAML_PATTERN = "ck8s-.*\\.concord\\.yaml";
    private static final String CK8S_CLUSTER_YAML_NAME = "cluster.yaml";

    private static final String CK_8_S_COMPONENTS_TESTS_DIR_NAME = "ck8s-components-tests";

    private static final List<String> ignorePatterns = Arrays.asList(".*\\.pdf$", ".*\\.png$", ".*\\.jpg$");

    public static Stream<Path> streamConcordYaml(Ck8sRepos ck8sPath)
    {
        return Stream.concat(
                streamConcordYaml(ck8sPath.ck8sComponents()),
                streamConcordYaml(ck8sPath.ck8sExtComponents()));
    }

    private static Stream<Path> streamConcordYaml(Path root)
    {
        return streamConcordYaml(Optional.of(root));
    }

    private static Stream<Path> streamConcordYaml(Optional<Path> rootOpt)
    {
        if (rootOpt.isEmpty()) {
            return Stream.empty();
        }

        Path root = rootOpt.get();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return Stream.empty();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(CONCORD_YAML_PATTERN))
                    .toList().stream();
        }
        catch (IOException e) {
            throw new RuntimeException("stream concord yaml error: " + e.getMessage());
        }
    }

    public static Stream<Path> streamClusterYaml(Ck8sRepos ck8sPath)
    {
        return Stream.concat(
                streamClusterYaml(ck8sPath.ck8sOrgDir()),
                streamClusterYaml(ck8sPath.ck8sExtOrgDir()));
    }

    private static Stream<Path> streamClusterYaml(Path root)
    {
        return streamClusterYaml(Optional.of(root));
    }

    private static Stream<Path> streamClusterYaml(Optional<Path> rootOpt)
    {
        if (rootOpt.isEmpty()) {
            return Stream.empty();
        }

        Path root = rootOpt.get();
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return Stream.empty();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> CK8S_CLUSTER_YAML_NAME.equals(p.getFileName().toString()))
                    .toList().stream();
        }
        catch (IOException e) {
            throw new RuntimeException("stream cluster yaml error: " + e.getMessage());
        }
    }

    public static Path findClusterYamlByAlias(Ck8sRepos ck8sPath, String alias)
    {
        return findClusterYamlBy(ck8sPath, cluster -> alias.equals(MapUtils.getString(cluster, "alias")));
    }

    public static Path findClusterYamlBy(Ck8sRepos ck8sPath, Predicate<Map<String, Object>> filter)
    {
        return streamClusterYaml(ck8sPath)
                .filter(p -> filter.test(Mapper.yamlMapper().readMap(p)))
                .findFirst()
                .orElse(null);
    }

    public static Map<String, Object> buildConcordYaml(Ck8sRepos ck8sPath, Path clusterYaml, Map<String, Object> concordYmlTemplate, boolean debug, List<String> additionalDeps)
    {

        Path defaultCfg = ck8sPath.defaultCfg();
        Path organizationYamlPath = ck8sPath.orgCfgForCluster(clusterYaml);
        Path accountYamlPath = ck8sPath.accountCfgForCluster(clusterYaml);

        Map<String, Object> concordYml = new HashMap<>(concordYmlTemplate);
        ConfigurationUtils.set(concordYml, debug, "configuration", "debug");
        Map<String, Object> merged = merge(defaultCfg, organizationYamlPath, accountYamlPath, clusterYaml);
        MapUtils.set(concordYml, merged, "configuration.arguments.clusterRequest");

        List<String> currentDependencies = MapUtils.getList(concordYml, "configuration.dependencies");
        List<String> dependencies = new ArrayList<>(currentDependencies);
        dependencies.addAll(additionalDeps);

        ConfigurationUtils.set(concordYml, dependencies, "configuration", "dependencies");

        return concordYml;
    }

    public static Ck8sMetadata readCk8sMetadata(Path packageDir)
            throws IOException
    {
        Path metadataFile = packageDir.resolve(METADATA_JSON);
        return JSON_MAPPER.readValue(metadataFile.toFile(), Ck8sMetadata.class);
    }

    public static void writeCk8sMetadata(Ck8sRepos ck8sPath, Path packageDir)
            throws IOException
    {
        Path metadataFile = packageDir.resolve(METADATA_JSON);
        JSON_MAPPER.writeValue(metadataFile.toFile(), ck8sPath.asCk8sMetadata());
    }

    public static void copyComponents(Ck8sRepos ck8sPath, Path target)
    {
        copyComponents(ck8sPath.ck8sComponents(), ck8sPath.ck8sExtComponents(), target, "ck8s-components");
    }

    public static void copyTestComponents(Ck8sRepos ck8sPath, Path target)
    {
        copyComponents(ck8sPath.ck8sComponentsTests(), ck8sPath.ck8sExtComponentsTests(), target, CK_8_S_COMPONENTS_TESTS_DIR_NAME);
    }

    public static void removeTestComponents(Path target)
    {
        Path ck8sTestResourcesDir = target.resolve(CK_8_S_COMPONENTS_TESTS_DIR_NAME);
        brig.ck8s.cli.common.IOUtils.deleteRecursively(ck8sTestResourcesDir);
    }

    private static void copyComponents(Path sourceCk8sComponents, Optional<Path> sourceCk8sExtComponentsOpt, Path target, String componentsDirName)
    {
        Path concordDir = target.resolve("concord");
        try {
            if (Files.notExists(concordDir)) {
                Files.createDirectory(concordDir);
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Error creating concord target dir: " + e.getMessage());
        }

        Path componentsDir = target.resolve(componentsDirName);
        try {
            Files.createDirectory(componentsDir);
        }
        catch (IOException e) {
            throw new RuntimeException("Error creating '" + componentsDirName + "' target dir: " + e.getMessage());
        }

        if (Files.notExists(sourceCk8sComponents)) {
            throw new RuntimeException("Can't find ck8s components in '" + sourceCk8sComponents + "'. Check the configuration");
        }

        try {
            copyComponentsYaml(sourceCk8sComponents, concordDir);
            IOUtils.copy(sourceCk8sComponents, componentsDir, ignorePatterns, null, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            throw new RuntimeException("Error copying ck8s components '" + sourceCk8sComponents + "' to '" + componentsDir + "': " + e.getMessage(), e);
        }

        if (sourceCk8sExtComponentsOpt.isEmpty()) {
            return;
        }

        Path sourceCk8sExtComponents = sourceCk8sExtComponentsOpt.get();
        if (Files.isDirectory(sourceCk8sExtComponents)) {
            try {
                copyComponentsYaml(sourceCk8sExtComponents, concordDir);
                IOUtils.copy(sourceCk8sExtComponents, componentsDir, ignorePatterns, null, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e) {
                throw new RuntimeException("Error copying ck8sExt components '" + sourceCk8sExtComponents + "' to '" + componentsDir + "': " + e.getMessage());
            }
        }
    }

    private static Map<String, Object> merge(Path... yamls)
    {
        Map<String, Object> merged = null;
        for (Path p : yamls) {
            if (Files.exists(p)) {
                merged = MapUtils.merge(merged, Mapper.yamlMapper().readMap(p));
            }
        }
        return merged;
    }

    private static void copyComponentsYaml(Path src, Path dest)
            throws IOException
    {
        try (Stream<Path> walk = Files.walk(src)) {
            walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(CONCORD_YAML_PATTERN))
                    .forEach(p -> copy(p, dest.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING));
        }
    }

    private static void copy(Path source, Path target, CopyOption... options)
    {
        try {
            Files.copy(source, target, options);
        }
        catch (IOException e) {
            throw new RuntimeException("Error copy '" + source + "' to '" + target + "':" + e.getMessage());
        }
    }

    public static ProcessDefinition findYaml(Path flowsDir, String flowName)
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
}
