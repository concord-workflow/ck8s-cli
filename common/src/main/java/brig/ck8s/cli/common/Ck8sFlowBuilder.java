package brig.ck8s.cli.common;

import java.io.IOException;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

public class Ck8sFlowBuilder
{
    public static final String CONCORD_YAML_PATTERN = "ck8s-.*\\.concord\\.yaml";

    private static final List<String> FILE_IGNORE_PATTERNS = Arrays.asList(".*\\.pdf$", ".*\\.png$", ".*\\.jpg$");

    private final Ck8sPath ck8sPath;
    private final Path target;
    private boolean includeTests;
    private boolean debug;
    private List<String> additionalDependencies = Collections.emptyList();
    private final Ck8sFlowBuilderListener listener;

    public Ck8sFlowBuilder(Ck8sPath ck8sPath, Path target, Ck8sFlowBuilderListener listener)
    {
        this.ck8sPath = ck8sPath;
        this.target = target;
        this.listener = listener;
    }

    private static Map<String, Object> concordYamlTemplate()
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

    public Ck8sFlows build(String clusterAlias)
    {
        Path clusterYaml = Ck8sUtils.findClusterYamlByAlias(ck8sPath, clusterAlias);
        if (clusterYaml == null) {
            throw new RuntimeException("The cluster alias '" + clusterAlias + "' doesn't map to any ck8s cluster yaml file.");
        }

        try {
            IOUtils.deleteRecursively(target);
        }
        catch (Exception e) {
            throw new RuntimeException("Can't delete target '" + target + "': " + e.getMessage());
        }

        Path flows = target.resolve("flows");
        try {
            Files.createDirectories(flows);
        }
        catch (Exception e) {
            throw new RuntimeException("Can't create target '" + target + "': " + e.getMessage());
        }

        Map<String, Object> concordYaml = Ck8sUtils.buildConcordYaml(ck8sPath, clusterYaml, concordYamlTemplate(), debug, additionalDependencies);
        Mapper.yamlMapper().write(flows.resolve("concord.yml"), concordYaml);

        copyComponents(ck8sPath, flows);
        if (includeTests) {
            copyTestComponents(ck8sPath, flows);
        }

        return Ck8sFlows.builder()
                .clusterAlias(clusterAlias)
                .location(flows)
                .build();
    }

    private void copyComponents(Ck8sPath ck8sPath, Path target)
    {
        copyComponents(ck8sPath.ck8sComponents(), ck8sPath.ck8sExtComponents(), target, "ck8s-components");
    }

    private void copyTestComponents(Ck8sPath ck8sPath, Path target)
    {
        copyComponents(ck8sPath.ck8sComponentsTests(), ck8sPath.ck8sExtComponentsTests(), target, "ck8s-components-tests");
    }

    private void copyComponents(Path sourceCk8sComponents, Path sourceCk8sExtComponents, Path target, String componentsDirName)
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
            com.walmartlabs.concord.common.IOUtils.copy(sourceCk8sComponents, componentsDir, FILE_IGNORE_PATTERNS, null, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            throw new RuntimeException("Error copying ck8s components '" + sourceCk8sComponents + "' to '" + componentsDir + "': " + e.getMessage(), e);
        }

        if (sourceCk8sExtComponents != null && Files.isDirectory(sourceCk8sExtComponents)) {
            try {
                copyComponentsYaml(sourceCk8sExtComponents, concordDir);
                com.walmartlabs.concord.common.IOUtils.copy(sourceCk8sExtComponents, componentsDir, FILE_IGNORE_PATTERNS, null, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e) {
                throw new RuntimeException("Error copying ck8sExt components '" + sourceCk8sExtComponents + "' to '" + componentsDir + "': " + e.getMessage());
            }
        }
    }

    private void copyComponentsYaml(Path src, Path dest)
            throws IOException
    {
        try (Stream<Path> walk = Files.walk(src)) {
            walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(CONCORD_YAML_PATTERN))
                    .forEach(p -> {
                        if (listener != null) {
                            listener.beforeConcordYamlAdd(p, dest.resolve(p.getFileName()));
                        }
                        copy(p, dest.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    });
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
}
