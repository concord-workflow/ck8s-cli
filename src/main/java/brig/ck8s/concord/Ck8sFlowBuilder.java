package brig.ck8s.concord;

import brig.ck8s.executor.RemoteFlowExecutor;
import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.Ck8sUtils;
import brig.ck8s.utils.IOUtils;
import brig.ck8s.utils.YamlMapper;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static brig.ck8s.utils.Ck8sUtils.copyComponents;
import static brig.ck8s.utils.Ck8sUtils.copyTestComponents;

public class Ck8sFlowBuilder {

    private final Ck8sPath ck8sPath;
    private final Path target;
    private boolean includeTests;

    public Ck8sFlowBuilder(Ck8sPath ck8sPath, Path target) {
        this.ck8sPath = ck8sPath;
        this.target = target;
    }

    public Ck8sFlowBuilder includeTests(boolean include) {
        this.includeTests = include;
        return this;
    }

    public Path build(String clusterAlias) {
        Path clusterYaml = Ck8sUtils.findClusterYamlByAlias(ck8sPath, clusterAlias);
        if (clusterYaml == null) {
            throw new RuntimeException("The cluster alias '" + clusterAlias + "' doesn't map to any ck8s cluster yaml file.");
        }

        try {
            IOUtils.deleteRecursively(target);
        } catch (Exception e) {
            throw new RuntimeException("Can't delete target '" + target + "': " + e.getMessage());
        }

        Path flows = target.resolve("flows");
        try {
            Files.createDirectories(target);
            Files.createDirectories(flows);
        } catch (Exception e) {
            throw new RuntimeException("Can't create target '" + target + "': " + e.getMessage());
        }

        Map<String, Object> concordYaml = Ck8sUtils.buildConcordYaml(ck8sPath, clusterYaml, concordYamlTemplate());
        YamlMapper.write(flows.resolve("concord.yml"), concordYaml);

        copyComponents(ck8sPath, flows);
        if (includeTests) {
            copyTestComponents(ck8sPath, flows);
        }

        return flows;
    }

    private static Map<String, Object> concordYamlTemplate() {
        URL url = RemoteFlowExecutor.class.getResource("/template.concord.yaml");
        if (url == null) {
            throw new RuntimeException("Can't find concord.yml template. This is most likely a bug.");
        }

        try {
            return YamlMapper.readMap(url);
        } catch (Exception e) {
            throw new RuntimeException("Error reading concord template. This is most likely a bug.", e);
        }
    }
}
