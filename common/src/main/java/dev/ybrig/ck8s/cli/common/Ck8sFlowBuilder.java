package dev.ybrig.ck8s.cli.common;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Ck8sFlowBuilder {
    public static final String CONCORD_YAML_PATTERN = "ck8s-.*\\.concord\\.yaml";

    private static final List<String> FILE_IGNORE_PATTERNS = Arrays.asList(".*\\.pdf$", ".*\\.png$", ".*\\.jpg$");

    private final Ck8sPath ck8sPath;
    private final Path target;
    private boolean includeTests;
    private final Ck8sFlowBuilderListener listener;

    public Ck8sFlowBuilder(Ck8sPath ck8sPath, Path target) {
        this(ck8sPath, target, (src, dest) -> {});
    }

    public Ck8sFlowBuilder(Ck8sPath ck8sPath, Path target, Ck8sFlowBuilderListener listener)
    {
        this.ck8sPath = ck8sPath;
        this.target = target;
        this.listener = listener;
    }

    public Ck8sFlowBuilder includeTests(boolean include)
    {
        this.includeTests = include;
        return this;
    }

    public Ck8sFlows build()
    {
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

        copyComponents(ck8sPath, flows);
        if (includeTests) {
            copyTestComponents(ck8sPath, flows);
        }

        // copy configs
        if (Files.exists(ck8sPath.configs())) {
            try {
                IOUtils.copy(ck8sPath.configs(), flows.resolve("configs"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new RuntimeException("Can't copy configs: " + e.getMessage());
            }
        }

        return Ck8sFlows.builder()
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
            IOUtils.copy(sourceCk8sComponents, componentsDir, FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            throw new RuntimeException("Error copying ck8s components '" + sourceCk8sComponents + "' to '" + componentsDir + "': " + e.getMessage(), e);
        }

        if (sourceCk8sExtComponents != null && Files.isDirectory(sourceCk8sExtComponents)) {
            try {
                copyComponentsYaml(sourceCk8sExtComponents, concordDir);
                IOUtils.copy(sourceCk8sExtComponents, componentsDir, FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
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
