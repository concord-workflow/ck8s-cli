package dev.ybrig.ck8s.cli.codecoverage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.cli.runner.CliImportsListener;
import com.walmartlabs.concord.cli.runner.CliImportsNormalizer;
import com.walmartlabs.concord.cli.runner.CliRepositoryExporter;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.dependencymanager.DependencyManagerRepositories;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportManagerFactory;
import com.walmartlabs.concord.imports.ImportProcessingException;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import dev.ybrig.ck8s.cli.Ck8sPathOptionsMixin;
import dev.ybrig.ck8s.cli.cfg.CliDefaultParamValuesProvider;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.executor.MvnJsonProvider;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(name = "code-coverage",
        description = {"Concord process events"},
        defaultValueProvider = CliDefaultParamValuesProvider.class
)
public class CodeCoverageCommand implements Callable<Integer> {

    private static final String DEFAULT_IMPORTS_SOURCE = "https://github.com";
    private static final String DEFAULT_VERSION = "main";

    private static final TypeReference<List<ProcessEventEntry>> EVENTS_TYPE = new TypeReference<>()
    {
    };

    @CommandLine.Mixin
    Ck8sPathOptionsMixin ck8sPathOptions;

    @CommandLine.Option(names = {"--target-root"}, description = "path to target dir")
    Path targetRootPath = Path.of(System.getProperty("java.io.tmpdir")).resolve("ck8s-cli");

    @CommandLine.Option(required = true, names = {"--events-dir"}, description = "where to store events")
    Path eventsDir = null;

    @CommandLine.Option(required = true, names = {"--output-dir"}, description = "where to store code coverage reports")
    Path outputDir = null;

    @Override
    public Integer call() throws Exception {
        LogUtils.info("ck8s: {}", ck8sPathOptions.getCk8sPath());
        LogUtils.info("ck8s-ext: {}", ck8sPathOptions.getCk8sExtPath());
        LogUtils.info("targetRootPath: {}", targetRootPath);
        LogUtils.info("outputDir: {}", outputDir);

        Files.createDirectories(outputDir);

        var events = collectEvents(eventsDir);

        LcovReportProducer report = new LcovReportProducer();
        report.init(loadProcessDefinition(payload()));
        report.onEvents(events);
        report.produce(outputDir);

        Files.copy(targetRootPath.resolve("flows").resolve("concord.yml"), outputDir.resolve("concord.yml"), StandardCopyOption.REPLACE_EXISTING);

        IOUtils.copy(targetRootPath.resolve("flows").resolve("concord"), outputDir.resolve("concord"), List.of(), StandardCopyOption.REPLACE_EXISTING);

        return 0;
    }

    private Ck8sPayload payload() {
        Ck8sPath ck8s = new Ck8sPath(ck8sPathOptions.getCk8sPath(), ck8sPathOptions.getCk8sExtPath());

        Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8s, targetRootPath, null)
                .includeTests(true)
                .build();

        return Ck8sPayload.builder()
                .debug(true)
                .arguments(Map.of())
                .ck8sFlows(ck8sFlows)
                .build();
    }

    private static ProcessDefinition loadProcessDefinition(Ck8sPayload payload) throws Exception {
        Path targetDir = payload.ck8sFlows().location();

        ConcordYaml concordYaml = ConcordYaml.builder()
                .entryPoint("normalFlow")
                .debug(true)
                .arguments(Map.of())
                .putArguments("flow", "n/a")
                .build();
        concordYaml.write(payload.ck8sFlows().location());

        DependencyManager dependencyManager = new DependencyManager(getDependencyManagerConfiguration());

        Path repoCacheDir = Paths.get(System.getProperty("user.home")).resolve(".ck8s").resolve("repo-cache");
        ImportManager importManager = new ImportManagerFactory(dependencyManager,
                new CliRepositoryExporter(repoCacheDir), Collections.emptySet())
                .create();

        ProjectLoaderV2.Result loadResult;
        try {
            loadResult = new ProjectLoaderV2(importManager)
                    .load(targetDir, new CliImportsNormalizer(DEFAULT_IMPORTS_SOURCE, true, DEFAULT_VERSION), true ? new CliImportsListener() : null);
        } catch (ImportProcessingException e) {
            ObjectMapper om = new ObjectMapper();
            LogUtils.error("while processing import {}: {}", om.writeValueAsString(e.getImport()), e.getMessage());
            throw e;
        } catch (Exception e) {
            LogUtils.error("while loading {}", targetDir, e);
            throw e;
        }

        return loadResult.getProjectDefinition();
    }

    private static DependencyManagerConfiguration getDependencyManagerConfiguration() {
        Path cfgFile = new MvnJsonProvider().get();
        Path depsCacheDir = ck8sHome().resolve("depsCache");
        return DependencyManagerConfiguration.builder().from(DependencyManagerConfiguration.of(depsCacheDir, DependencyManagerRepositories.get(cfgFile)))
                .offlineMode(false)
                .build();
    }

    private static Path ck8sHome() {
        return Paths.get(System.getProperty("user.home")).resolve(".ck8s");
    }

    private static List<ProcessEventEntry> collectEvents(Path eventsDir) throws IOException {
        try (Stream<Path> files = Files.walk(eventsDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".events.json"))
                    .flatMap(file -> Mapper.jsonMapper().read(file, EVENTS_TYPE).stream())
                    .toList();
        }
    }
}
