package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.concord.ConcordServer;
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
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportManagerFactory;
import com.walmartlabs.concord.imports.ImportProcessingException;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.Runner;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ExecutionListener;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ConcordCliFlowExecutor {

    private static final String DEFAULT_IMPORTS_SOURCE = "https://github.com";
    private static final String DEFAULT_VERSION = "main";
    private static final String DEFAULT_VAULT_ID = "default";

    private final Verbosity verbosity;

    public ConcordCliFlowExecutor(Verbosity verbosity) {
        this.verbosity = verbosity;
    }

    public int execute(Ck8sPayload payload) {
        try {
            return _execute(payload);
        } catch (Exception e) {
            if (verbosity.verbose()) {
                System.err.print("Error: ");
                e.printStackTrace(System.err);
            } else {
                System.err.println("Error: " + e.getMessage());
            }
            return 1;
        }
    }

    private int _execute(Ck8sPayload payload) throws Exception {
        Path targetDir = payload.location();

        DependencyManager dependencyManager = new DependencyManager(getDependencyManagerConfiguration());

        Path repoCacheDir = Paths.get(System.getProperty("user.home")).resolve(".ck8s").resolve("repo-cache");
        ImportManager importManager = new ImportManagerFactory(dependencyManager,
                new CliRepositoryExporter(repoCacheDir), Collections.emptySet())
                .create();

        ProjectLoaderV2.Result loadResult;
        try {
            loadResult = new ProjectLoaderV2(importManager)
                    .load(targetDir, new CliImportsNormalizer(DEFAULT_IMPORTS_SOURCE, verbosity.verbose(), DEFAULT_VERSION), verbosity.verbose() ? new CliImportsListener() : null);
        } catch (ImportProcessingException e) {
            ObjectMapper om = new ObjectMapper();
            System.err.println("Error while processing import " + om.writeValueAsString(e.getImport()) + ": " + e.getMessage());
            return -1;
        } catch (Exception e) {
            System.err.println("Error while loading " + targetDir);
            e.printStackTrace();
            return -1;
        }

        ProcessDefinition processDefinition = loadResult.getProjectDefinition();

        UUID instanceId = UUID.randomUUID();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("concordUrl", "https://concord.local.localhost");

        Map<String, Object> flowAndUserArgs = ConfigurationUtils.deepMerge(processDefinition.configuration().arguments(), payload.args());
        args.putAll(flowAndUserArgs);

        args.put(Constants.Context.TX_ID_KEY, instanceId.toString());
        args.put(Constants.Context.WORK_DIR_KEY, targetDir.toAbsolutePath().toString());

        if (verbosity.verbose()) {
            dumpArguments(args);
        }

        ProcessConfiguration cfg = from(processDefinition.configuration(), processInfo(), projectInfo())
                .entryPoint(payload.entryPoint())
                .instanceId(instanceId)
                .build();

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .dependencies(new DependencyResolver(dependencyManager, verbosity.verbose()).resolveDeps(processDefinition.configuration().dependencies()))
                .debug(processDefinition.configuration().debug())
                .build();

        Path secretStoreDir = ck8sHome().resolve("secrets");
        Path vaultDir = ck8sHome().resolve("vaults");
        Injector injector = new InjectorFactory(new WorkingDirectory(targetDir),
                runnerCfg,
                () -> cfg,
                new ProcessDependenciesModule(targetDir, runnerCfg.dependencies(), cfg.debug()),
                new CliServicesModule(secretStoreDir, targetDir, new VaultProvider(vaultDir, DEFAULT_VAULT_ID), dependencyManager, verbosity),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        if (verbosity.logTaskParams()) {
                            Multibinder<ExecutionListener> executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
                            executionListeners.addBinding().toInstance(new FlowCallParamsLogger());
                        }
                    }
                })
                .create();

        Runner runner = injector.getInstance(Runner.class);

        if (cfg.debug()) {
            System.out.println("Available tasks: " + injector.getInstance(TaskProviders.class).names());
        }

        ConcordServer.start();

        try {
            runner.start(cfg, processDefinition, args);
        } catch (Exception e) {
            if (verbosity.verbose()) {
                System.err.print("Error: ");
                e.printStackTrace(System.err);
            } else {
                System.err.println("Error: " + e.getMessage());
            }
            return 1;
        }

        return 0;
    }

    private DependencyManagerConfiguration getDependencyManagerConfiguration() {
        Path cfgFile = new MvnJsonProvider().get();
        Path depsCacheDir = ck8sHome().resolve("depsCache");
        return DependencyManagerConfiguration.of(depsCacheDir, DependencyManagerRepositories.get(cfgFile));
    }

    private Path ck8sHome() {
        return Paths.get(System.getProperty("user.home")).resolve(".ck8s");
    }

    private static ImmutableProcessConfiguration.Builder from(ProcessDefinitionConfiguration cfg, ProcessInfo processInfo, ProjectInfo projectInfo) {
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

    private static ProcessInfo processInfo() {
        return ProcessInfo.builder()
                .sessionToken("test")
                .build();
    }

    private static ProjectInfo projectInfo() {
        return ProjectInfo.builder()
                .orgName("Default")
                .build();
    }

    private static void dumpArguments(Map<String, Object> args) {
        ObjectMapper om = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        try {
            System.out.println("Process arguments:");
            System.out.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(args));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
