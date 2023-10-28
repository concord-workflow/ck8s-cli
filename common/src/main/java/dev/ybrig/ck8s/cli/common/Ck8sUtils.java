package dev.ybrig.ck8s.cli.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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

    public static Path findClusterYamlByAnyAlias(Ck8sPath ck8sPath, String alias)
    {
        return findClusterYamlBy(ck8sPath, cluster -> {
            if (alias.equals(cluster.alias())) {
                return true;
            }

            return cluster.clusterGroup()
                    .map(g -> g.isActive() && alias.equals(g.alias()))
                    .orElse(false);
        });
    }

    private static Path findClusterYamlBy(Ck8sPath ck8sPath, Predicate<ClusterConfiguration> filter)
    {
        return streamClusterYaml(ck8sPath)
                .filter(p -> filter.test(new ClusterConfiguration(Mapper.yamlMapper().readMap(p))))
                .findFirst()
                .orElse(null);
    }

    public static Map<String, Object> buildClusterRequest(Ck8sPath ck8sPath, String clusterAlias) {
        Path clusterYaml = Ck8sUtils.findClusterYamlByAnyAlias(ck8sPath, clusterAlias);
        if (clusterYaml == null) {
            throw new RuntimeException("The cluster alias '" + clusterAlias + "' doesn't map to any ck8s cluster yaml file.");
        }

        return buildClusterRequest(ck8sPath, clusterYaml);
    }

    public static Map<String, Object> buildClusterRequest(Ck8sPath ck8sPath, Path clusterYaml) {
        Path defaultCfg = ck8sPath.defaultCfg();
        Path organizationYamlPath = ck8sPath.orgCfgForCluster(clusterYaml);
        Path accountYamlPath = ck8sPath.accountCfgForCluster(clusterYaml);

        return merge(defaultCfg, organizationYamlPath, accountYamlPath, clusterYaml);
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
}
