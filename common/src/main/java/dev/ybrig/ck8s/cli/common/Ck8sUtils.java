package dev.ybrig.ck8s.cli.common;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Ck8sUtils
{

    private static final String CK8S_CLUSTER_YAML_NAME = "cluster.yaml";

    public static Stream<Path> streamConcordYaml(Ck8sPath ck8sPath)
    {
        return Stream.concat(
                streamConcordYaml(ck8sPath.ck8sComponents()),
                streamConcordYaml(ck8sPath.ck8sExtComponents()));
    }

    public static Stream<Path> streamConcordYaml(Path root)
    {
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return Stream.empty();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(Ck8sFlowBuilder.CONCORD_YAML_PATTERN))
                    .collect(Collectors.toList())
                    .stream();
        }
        catch (IOException e) {
            throw new RuntimeException("stream concord yaml error: " + e.getMessage());
        }
    }

    public static Stream<Path> streamClusterYaml(Ck8sPath ck8sPath)
    {
        return Stream.concat(
                streamClusterYaml(ck8sPath.ck8sOrgDir()),
                streamClusterYaml(ck8sPath.ck8sExtOrgDir()));
    }

    private static Stream<Path> streamClusterYaml(Path root)
    {
        if (root == null || !Files.exists(root) || !Files.isDirectory(root)) {
            return Stream.empty();
        }

        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> CK8S_CLUSTER_YAML_NAME.equals(p.getFileName().toString()))
                    .collect(Collectors.toList())
                    .stream();
        }
        catch (IOException e) {
            throw new RuntimeException("stream cluster yaml error: " + e.getMessage());
        }
    }

    public static Path findClusterYamlByAlias(Ck8sPath ck8sPath, String alias)
    {
        return findClusterYamlBy(ck8sPath, cluster -> alias.equals(MapUtils.getString(cluster, "alias")));
    }

    public static Path findClusterYamlBy(Ck8sPath ck8sPath, Predicate<Map<String, Object>> filter)
    {
        return streamClusterYaml(ck8sPath)
                .filter(p -> filter.test(Mapper.yamlMapper().readMap(p)))
                .findFirst()
                .orElse(null);
    }

    public static String orgName(Ck8sPath ck8sPath, String clusterAlias) {
        Path clusterYaml = findClusterYamlByAlias(ck8sPath, clusterAlias);
        if (clusterYaml == null) {
            throw new RuntimeException("Can't find cluster.yaml for '" + clusterAlias + "' alias");
        }

        Path organizationYamlPath = ck8sPath.orgCfgForCluster(clusterYaml);
        Map<String, Object> org = Mapper.yamlMapper().readMap(organizationYamlPath);
        return MapUtils.assertString(org, "organization.name");
    }

    public static Map<String, Object> buildConcordYaml(Ck8sPath ck8sPath, Path clusterYaml, Map<String, Object> concordYmlTemplate, boolean debug,
                                                       List<String> additionalDeps)
    {
        Path defaultCfg = ck8sPath.defaultCfg();
        Path organizationYamlPath = ck8sPath.orgCfgForCluster(clusterYaml);
        Path accountYamlPath = ck8sPath.accountCfgForCluster(clusterYaml);

        Map<String, Object> concordYml = new HashMap<>(concordYmlTemplate);
        ConfigurationUtils.set(concordYml, debug, "configuration", "debug");
        Map<String, Object> merged = merge(defaultCfg, organizationYamlPath, accountYamlPath, clusterYaml);
        MapUtils.set(concordYml, merged, "configuration.arguments.clusterRequest");

        updateDependencies(additionalDeps, concordYml);

        return concordYml;
    }

    private static void updateDependencies(List<String> additionalDeps, Map<String, Object> concordYml) {
        List<String> currentDependencies = MapUtils.getList(concordYml, "configuration.dependencies");
        List<String> dependencies = new ArrayList<>(currentDependencies);
        dependencies.addAll(additionalDeps);

        ConfigurationUtils.set(concordYml, dependencies, "configuration", "dependencies");
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

    public static ProcessDefinition assertYaml(Path flowsDir, String flowName) {
        ProcessDefinition pd = findYaml(flowsDir, flowName);
        if (pd != null) {
            return pd;
        }
        throw new RuntimeException("Flow '" + flowName + "' not found");
    }

    private static ProcessDefinition findYaml(Path flowsDir, String flowName)
    {
        ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());

        try (Stream<Path> walk = Files.walk(flowsDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(Ck8sFlowBuilder.CONCORD_YAML_PATTERN))
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
