package dev.ybrig.ck8s.cli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.cli.Verbosity;
import com.walmartlabs.concord.cli.runner.*;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.dependencymanager.DependencyManagerRepositories;
import com.walmartlabs.concord.imports.*;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.ProjectLoadListeners;
import com.walmartlabs.concord.runtime.v2.runner.Runner;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ExecutionListener;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.ConcordYaml;
import dev.ybrig.ck8s.cli.common.IOUtils;
import dev.ybrig.ck8s.cli.concord.ConcordServer;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class ConcordCliFlowExecutor implements FlowExecutor {

    private static final String DEFAULT_IMPORTS_SOURCE = "https://github.com";
    private static final String DEFAULT_VERSION = "main";
    private static final String DEFAULT_VAULT_ID = "default";

    private final Verbosity verbosity;
    private final String secretsProvider;

    public ConcordCliFlowExecutor(Verbosity verbosity, String secretsProvider)
    {
        this.verbosity = verbosity;
        this.secretsProvider = secretsProvider;
    }

    private static ImmutableProcessConfiguration.Builder from(ProcessDefinitionConfiguration cfg, ProcessInfo processInfo, ProjectInfo projectInfo)
    {
        return ProcessConfiguration.builder()
                .debug(cfg.debug())
                .entryPoint(cfg.entryPoint())
                .arguments(cfg.arguments())
                .meta(cfg.meta())
                .events(cfg.events())
                .processInfo(processInfo)
                .projectInfo(projectInfo)
                .out(cfg.out());
    }

    private static ProcessInfo processInfo(List<String> activeProfiles)
    {
        return ProcessInfo.builder()
                .sessionToken("test")
                .activeProfiles(activeProfiles)
                .build();
    }

    private static ProjectInfo projectInfo()
    {
        return ProjectInfo.builder()
                .orgName("Default")
                .build();
    }

    private static void dumpArguments(Map<String, Object> args)
    {
        ObjectMapper om = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        try {
            LogUtils.info("Process arguments:\n{}", om.writerWithDefaultPrettyPrinter().writeValueAsString(args));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int execute(Ck8sPayload payload, String flowName, List<String> activeProfiles)
    {
        try {
            return _execute(payload, flowName, activeProfiles);
        }
        catch (Exception e) {
            if (verbosity.verbose()) {
                LogUtils.error("", e);
            }
            else {
                LogUtils.error("{}", e.getMessage());
            }
            return 1;
        }
    }

    private int _execute(Ck8sPayload payload, String flowName, List<String> activeProfiles)
            throws Exception
    {
        Path targetDir = payload.ck8sFlows().location();

        ConcordYaml concordYaml = ConcordYaml.builder()
                .entryPoint("normalFlow")
                .debug(payload.debug())
                .arguments(payload.arguments())
                .putArguments("flow", flowName)
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
                    .load(targetDir, new CliImportsNormalizer(DEFAULT_IMPORTS_SOURCE, verbosity.verbose(), DEFAULT_VERSION), verbosity.verbose() ? new CliImportsListener() : null);
        }
        catch (ImportProcessingException e) {
            ObjectMapper om = new ObjectMapper();
            LogUtils.error("while processing import {}: {}", om.writeValueAsString(e.getImport()), e.getMessage());
            return -1;
        }
        catch (Exception e) {
            LogUtils.error("while loading {}", targetDir, e);
            return -1;
        }

        ProcessDefinition processDefinition = loadResult.getProjectDefinition();

        UUID instanceId = UUID.randomUUID();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("concordUrl", "https://concord.local.localhost");
        if (secretsProvider != null) {
            ConfigurationUtils.set(args, secretsProvider, "clusterRequest", "secretsProvider");
        }

        Map<String, Object> flowAndUserArgs = ConfigurationUtils.deepMerge(processDefinition.configuration().arguments(), payload.arguments());
        args.putAll(flowAndUserArgs);

        args.put(Constants.Context.TX_ID_KEY, instanceId.toString());
        args.put(Constants.Context.WORK_DIR_KEY, targetDir.toAbsolutePath().toString());

        if (verbosity.verbose()) {
            dumpArguments(payload.arguments());

            LogUtils.info("Active profiles: {}", activeProfiles);
        }

        ProcessConfiguration cfg = from(processDefinition.configuration(), processInfo(activeProfiles), projectInfo())
                .instanceId(instanceId)
                .entryPoint("normalFlow")
                .build();

        if (!verbosity.verbose()) {
            LogUtils.info("Resolving process dependencies...");
        }

        long t1 = System.currentTimeMillis();
        Collection<String> dependencies = new DependencyResolver(dependencyManager, false)
                .resolveDeps(processDefinition.configuration().dependencies());

        if (!verbosity.verbose()) {
            System.out.println("Dependency resolution took " + (System.currentTimeMillis() - t1) + "ms");
        }

        // process local libs
        Path libsPath = Path.of(System.getProperty("user.dir")).resolve("lib");
        if (Files.exists(libsPath)) {
            IOUtils.copy(libsPath, targetDir.resolve(Constants.Files.LIBRARIES_DIR_NAME), null, StandardCopyOption.REPLACE_EXISTING);
        }

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .dependencies(dependencies)
                .debug(processDefinition.configuration().debug())
                .build();

        Path secretStoreDir = ck8sHome().resolve("secrets");
        Path vaultDir = ck8sHome().resolve("vaults");
        Injector injector = new InjectorFactory(new WorkingDirectory(targetDir),
                runnerCfg,
                () -> cfg,
                new ProcessDependenciesModule(targetDir, runnerCfg.dependencies(), cfg.debug()),
                new CliServicesModule(secretStoreDir, targetDir, new VaultProvider(vaultDir, DEFAULT_VAULT_ID), dependencyManager, verbosity),
                new AbstractModule()
                {
                    @Override
                    protected void configure()
                    {
                        if (verbosity.logTaskParams()) {
                            Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
                            executionListeners.addBinding().toInstance(new FlowCallParamsLogger());
                        }
                    }
                })
                .create();

        Runner runner = injector.getInstance(Runner.class);

        if (cfg.debug()) {
            LogUtils.info("Available tasks: " + injector.getInstance(TaskProviders.class).names());
        }

        // Just to notify listeners
        ProjectLoadListeners loadListeners = injector.getInstance(ProjectLoadListeners.class);
        ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());
        loader.load(targetDir, new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER, loadListeners);

        ConcordServer.start();

        try {
            runner.start(cfg, processDefinition, args);
        }
        catch (Exception e) {
            if (verbosity.verbose()) {
                LogUtils.error("", e);
            }
            else {
                LogUtils.error("{}", e.getMessage());
            }
            return 1;
        }

        return 0;
    }

    private DependencyManagerConfiguration getDependencyManagerConfiguration()
    {
        Path cfgFile = new MvnJsonProvider().get();
        Path depsCacheDir = ck8sHome().resolve("depsCache");
        return DependencyManagerConfiguration.of(depsCacheDir, DependencyManagerRepositories.get(cfgFile));
    }

    private Path ck8sHome()
    {
        return Paths.get(System.getProperty("user.home")).resolve(".ck8s");
    }
}
