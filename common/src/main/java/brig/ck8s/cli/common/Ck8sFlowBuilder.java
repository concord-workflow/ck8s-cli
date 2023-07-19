package brig.ck8s.cli.common;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static brig.ck8s.cli.common.Ck8sPayload.createClusterConcordYamlFileName;
import static brig.ck8s.cli.common.Ck8sUtils.copyComponents;
import static brig.ck8s.cli.common.Ck8sUtils.copyTestComponents;
import static brig.ck8s.cli.common.Ck8sUtils.removeTestComponents;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.nonNull;

public class Ck8sFlowBuilder
{
    public static Ck8sFlowBuilder builder(Ck8sPath ck8sPath, Path target)
    {
        return new Ck8sFlowBuilder(ck8sPath, target);
    }

    private final Ck8sPath ck8sPath;
    private final Path target;
    private boolean includeTests;
    private boolean debug;
    private List<String> additionalDependencies = Collections.emptyList();
    private Path ck8sPackage;
    private Set<String> clusterAliases = new HashSet<>();

    private Ck8sFlowBuilder(Ck8sPath ck8sPath, Path target)
    {
        this.ck8sPath = ck8sPath;
        this.target = target;
    }

    public Ck8sFlowBuilder withCk8sPackage(Path ck8sPackage)
    {
        this.ck8sPackage = ck8sPackage;
        return this;
    }

    public Ck8sFlowBuilder withClusterAlias(String... clusterAlias)
    {
        this.clusterAliases.addAll(Arrays.asList(clusterAlias));
        return this;
    }

    public Ck8sFlowBuilder includeTests(boolean include)
    {
        this.includeTests = include;
        return this;
    }

    public Ck8sFlowBuilder withDependencies(List<String> additionalDependencies)
    {
        this.additionalDependencies = additionalDependencies;
        return this;
    }

    public Ck8sFlowBuilder debug(boolean debug)
    {
        this.debug = debug;
        return this;
    }

    public Path build()
    {
        Path packageDir = cleanupPackageWorkDir();

        if (nonNull(ck8sPackage)) {
            try {
                com.walmartlabs.concord.common.IOUtils.unzip(ck8sPackage, packageDir, REPLACE_EXISTING);
            }
            catch (IOException e) {
                throw new RuntimeException("Failed to unpack ck8s package: %s".formatted(ck8sPackage), e);
            }
            if (!includeTests) {
                removeTestComponents(packageDir);
            }
        }
        else {
            clusterAliases.forEach(
                    clusterAlias -> renderClusterConcordYaml(clusterAlias, packageDir));
            copyComponents(ck8sPath, packageDir);
            if (includeTests) {
                copyTestComponents(ck8sPath, packageDir);
            }
        }

        return packageDir;
    }

    private Path cleanupPackageWorkDir()
    {
        if (!Files.exists(target)) {
            IOUtils.createDir(target);
        }

        Path packageDir = target.resolve("flows");
        try {
            IOUtils.deleteRecursively(packageDir);
        }
        catch (Exception e) {
            throw new RuntimeException("Can't delete target '" + target + "': " + e.getMessage());
        }
        IOUtils.createDir(packageDir);
        return packageDir;
    }

    private Path renderClusterConcordYaml(String clusterAlias, Path flowsLocation)
    {
        Path clusterYaml = Ck8sUtils.findClusterYamlByAlias(ck8sPath, clusterAlias);
        if (clusterYaml == null) {
            throw new RuntimeException("The cluster alias '" + clusterAlias + "' doesn't map to any ck8s cluster yaml file.");
        }
        Map<String, Object> concordYaml = Ck8sUtils.buildConcordYaml(ck8sPath, clusterYaml, concordYamlTemplate(), debug, additionalDependencies);
        Path clusterConcordYaml = flowsLocation.resolve(createClusterConcordYamlFileName(clusterAlias));
        Mapper.yamlMapper().write(clusterConcordYaml, concordYaml);
        return clusterConcordYaml;
    }

    private Map<String, Object> concordYamlTemplate()
    {
        URL url = Ck8sFlowBuilder.class.getResource("/templates/concord.yaml");
        if (url == null) {
            throw new RuntimeException("Can't find concord.yml template. This is most likely a bug.");
        }

        try {
            return Mapper.yamlMapper().readMap(url);
        }
        catch (Exception e) {
            throw new RuntimeException("Error reading concord template. This is most likely a bug.", e);
        }
    }
}
