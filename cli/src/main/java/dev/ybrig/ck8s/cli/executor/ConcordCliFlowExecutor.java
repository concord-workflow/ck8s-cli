package dev.ybrig.ck8s.cli.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.cli.Verbosity;
import com.walmartlabs.concord.cli.runner.*;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.dependencymanager.DependencyManagerRepositories;
import com.walmartlabs.concord.imports.ImportManagerFactory;
import com.walmartlabs.concord.imports.ImportProcessingException;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.model.ProcessDefinitionUtils;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.EventReportingService;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.Runner;
import com.walmartlabs.concord.runtime.v2.runner.guice.ObjectMapperProvider;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.runtime.v2.runner.logging.LogContextThreadGroup;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.runner.logging.SimpleLogger;
import com.walmartlabs.concord.runtime.v2.runner.remote.EventRecordingExecutionListener;
import com.walmartlabs.concord.runtime.v2.runner.remote.TaskCallEventRecordingListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskCallListener;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.wrapper.ProcessDefinitionV2;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.ExecutionListener;
import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.concord.CliConcordProcess;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.concord.ConcordServer;
import dev.ybrig.ck8s.cli.model.ConcordProfile;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static com.walmartlabs.concord.common.ConfigurationUtils.deepMerge;
import static dev.ybrig.ck8s.cli.utils.Ck8sPayloadArchiver.loadConcordYamlFromClasspath;

public class ConcordCliFlowExecutor {

    private static final List<String> FILE_IGNORE_PATTERNS = Arrays.asList(".*\\.pdf$", ".*\\.png$", ".*\\.jpg$");

    private static final String DEFAULT_IMPORTS_SOURCE = "https://github.com";
    private static final String DEFAULT_VERSION = "main";
    private static final String DEFAULT_VAULT_ID = "default";

    private final DependencyManager dependencyManager;

    private final Ck8sPath ck8s;
    private final Verbosity verbosity;
    private final CliApp.SecretsProvider secretsProvider;
    private final ConcordProfile profile;
    private final Path eventsDir;
    private final boolean dryRunMode;
    private final Path targetDir;

    public ConcordCliFlowExecutor(Ck8sPath ck8s,
                                  Verbosity verbosity,
                                  CliApp.SecretsProvider secretsProvider,
                                  boolean offlineMode,
                                  String concordProfile,
                                  Path eventsDir,
                                  boolean dryRunMode,
                                  Path targetDir) {

        try {
            this.dependencyManager = new DependencyManager(getDependencyManagerConfiguration(offlineMode));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.ck8s = ck8s;
        this.verbosity = verbosity;
        this.secretsProvider = secretsProvider;

        // TODO: "default" is a default value for profile, but if profile is undefined we want to use local Concord server...
        if ("default".equals(concordProfile)) {
            this.profile = ConcordProfile.builder()
                    .alias("default")
                    .baseUrl("http://localhost:8001")
                    .apiKey("any")
                    .build();
        } else {
            this.profile = CliConfigurationProvider.getConcordProfile(concordProfile);
        }

        this.eventsDir = eventsDir;
        this.dryRunMode = dryRunMode;
        this.targetDir = targetDir;
    }

    public ConcordProcess execute(String clusterAlias, String flowName, Map<String, Object> userArguments, List<String> activeProfiles) {
        try {
            return _execute(clusterAlias, flowName, userArguments, activeProfiles);
        } catch (Exception e) {
            if (verbosity.verbose()) {
                LogUtils.error("", e);
            } else {
                LogUtils.error("{}", e.getMessage());
            }
            return null;
        }
    }

    private ConcordProcess _execute(String clusterAlias, String flowName, Map<String, Object> userArguments, List<String> activeProfiles)
            throws Exception {

        if (!activeProfiles.isEmpty()) {
            LogUtils.info("Active profiles: {}", String.join(", ", activeProfiles));
        }

        // prepare payload
        prepareWorkspace(ck8s, targetDir, clusterAlias);

        var processDefinition = loadProcessDefinition(targetDir, verbosity.verbose());
        if (processDefinition == null) {
            return null;
        }

        // prepare configuration
        var overlayCfg = ProcessDefinitionUtils.getProfilesOverlayCfg(new ProcessDefinitionV2(processDefinition), activeProfiles);

        var overlayDeps = prepareDependencies(processDefinition, overlayCfg, activeProfiles);

        var instanceId = UUID.randomUUID();

        var args = prepareArgs(ck8s, instanceId, overlayCfg, flowName, clusterAlias, userArguments);

        Mapper.yamlMapper().write(targetDir.resolve("input.yaml"), args);

        if (verbosity.verbose()) {
            dumpArguments(userArguments);
        }

        ProcessConfiguration cfg = from(processDefinition.configuration(), processInfo(activeProfiles), projectInfo(MapUtils.assertString(args, "clusterRequest.organization.name")))
                .instanceId(instanceId)
                .dryRun(dryRunMode)
                .arguments(args)
                .debug(verbosity.verbose())
                .build();

        ObjectMapper om = ObjectMapperProvider.getInstance();
        args.put(Constants.Request.PROCESS_INFO_KEY, om.convertValue(cfg.processInfo(), Map.class));
        args.put(Constants.Request.PROJECT_INFO_KEY, om.convertValue(cfg.projectInfo(), Map.class));

        if (!verbosity.verbose()) {
            LogUtils.info("Resolving process dependencies...");
        }

        var t1 = System.currentTimeMillis();
        var dependencies = new DependencyResolver(dependencyManager, false)
                .resolveDeps(JobDependencies.get(ck8s, overlayDeps, args));

        if (!verbosity.verbose()) {
            System.out.println("Dependency resolution took " + (System.currentTimeMillis() - t1) + "ms");
        }

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .api(ApiConfiguration.builder()
                        .baseUrl(profile.baseUrl())
                        .build())
                .dependencies(dependencies)
                .debug(processDefinition.configuration().debug())
                .build();

        // default task vars path
        var defaultTaskVars = Paths.get(System.getProperty("user.home")).resolve(".ck8s").resolve("default-task-vars.json");

        var secretStoreDir = ck8sHome().resolve("secrets");
        var vaultDir = ck8sHome().resolve("vaults");
        var injector = new InjectorFactory(new WorkingDirectory(targetDir),
                runnerCfg,
                () -> cfg,
                new ProcessDependenciesModule(targetDir, runnerCfg.dependencies(), cfg.debug()),
                new CliServicesModule(secretStoreDir, targetDir, defaultTaskVars, new VaultProvider(vaultDir, DEFAULT_VAULT_ID), dependencyManager, verbosity) {
                    @Override
                    protected void configure() {
                        bind(ApiClient.class).toProvider(ApiClientProvider.class);
                        bind(RunnerLogger.class).to(Logger.class);
                        super.configure();
                    }
                },
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ConcordProfile.class).toInstance(profile);
                    }
                },
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        var executionListeners = Multibinder.newSetBinder(binder(), ExecutionListener.class);
                        if (verbosity.logTaskParams()) {
                            executionListeners.addBinding().toInstance(new FlowCallParamsLogger());
                        }

                        if (eventsDir != null) {
                            var reportingService = new CliEventReportingService(eventsDir, instanceId);

                            bind(EventReportingService.class).toInstance(reportingService);
                            executionListeners.addBinding().to(EventRecordingExecutionListener.class);
                            executionListeners.addBinding().toInstance(reportingService);

                            var taskCallListeners = Multibinder.newSetBinder(binder(), TaskCallListener.class);
                            taskCallListeners.addBinding().to(TaskCallEventRecordingListener.class);
                        }
                    }
                })
                .create();

        var runner = injector.getInstance(Runner.class);

        if (cfg.debug()) {
            LogUtils.info("Available tasks: " + injector.getInstance(TaskProviders.class).names());
        }

        if ("default".equals(profile.alias())) {
            ConcordServer.start();
        }

        try {
            runner.start(cfg, processDefinition, args);
        } catch (Exception e) {
            if (verbosity.verbose()) {
                LogUtils.error("{}", e.getMessage(), e);
            } else {
                LogUtils.error("{}", e.getMessage());
            }
            return null;
        }

        return new CliConcordProcess(instanceId);
    }

    private ProcessDefinition loadProcessDefinition(Path workspaceDir, boolean verbose) throws IOException {
        var repoCacheDir = Paths.get(System.getProperty("user.home")).resolve(".ck8s").resolve("repo-cache");
        var importManager = new ImportManagerFactory(dependencyManager,
                new CliRepositoryExporter(repoCacheDir), Collections.emptySet())
                .create();

        ProjectLoaderV2.Result loadResult;
        try {
            loadResult = new ProjectLoaderV2(importManager)
                    .load(workspaceDir, new CliImportsNormalizer(DEFAULT_IMPORTS_SOURCE, verbose, DEFAULT_VERSION), verbose ? new CliImportsListener() : null);
        } catch (ImportProcessingException e) {
            var om = new ObjectMapper();
            LogUtils.error("while processing import {}: {}", om.writeValueAsString(e.getImport()), e.getMessage());
            return null;
        } catch (Exception e) {
            LogUtils.error("while loading {}", workspaceDir, e);
            return null;
        }

        return loadResult.getProjectDefinition();
    }

    private static void prepareWorkspace(Ck8sPath ck8s, Path target, String clusterAlias) {
        try {
            IOUtils.deleteRecursively(target);
        } catch (Exception e) {
            throw new RuntimeException("Can't delete target '" + target + "': " + e.getMessage());
        }

        try {
            Files.createDirectories(target);

            var concordYaml = ck8s.ck8sDir().resolve("concord.yml");
            if (!Files.exists(concordYaml)) {
                concordYaml = loadConcordYamlFromClasspath();
            }
            var targetConcordYaml = target.resolve("concord.yml");
            IOUtils.copy(concordYaml, targetConcordYaml, FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);

            // TODO: remove ME!!!!
            var c = Mapper.yamlMapper().readMap(targetConcordYaml);
            var clusterRequest = Ck8sUtils.buildClusterRequest(ck8s, clusterAlias);
            MapUtils.set(c, clusterRequest, "configuration.arguments.clusterRequest");
            Mapper.yamlMapper().write(targetConcordYaml, c);
            //

            IOUtils.copy(ck8s.configs(), target.resolve("configs"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            IOUtils.copy(ck8s.ck8sComponents(), target.resolve("ck8s-components"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            IOUtils.copy(ck8s.ck8sComponentsTests(), target.resolve("ck8s-components-tests"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            IOUtils.copy(ck8s.ck8sOrgDir(), target.resolve("ck8s-orgs"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            IOUtils.copy(ck8s.ck8sConfigs(), target.resolve("ck8s-configs"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Error creating process workspace: " + e.getMessage(), e);
        }
    }

    private static List<String> prepareDependencies(ProcessDefinition processDefinition, Map<String, Object> overlayCfg, List<String> activeProfiles) {
        var deps = new ArrayList<String>(MapUtils.getList(overlayCfg, Constants.Request.DEPENDENCIES_KEY, List.of()));

        // "extraDependencies" are additive: ALL extra dependencies from ALL ACTIVE profiles are added to the list
        var extraDeps = activeProfiles.stream()
                .flatMap(profileName -> Stream.ofNullable(processDefinition.profiles().get(profileName)))
                .flatMap(profile -> profile.configuration().extraDependencies().stream())
                .toList();
        deps.addAll(extraDeps);

        return deps;
    }

    private Map<String, Object> prepareArgs(Ck8sPath ck8s,
                                            UUID instanceId,
                                            Map<String, Object> overlayCfg,
                                            String flowName, String clusterAlias,
                                            Map<String, Object> userArguments) {
        var overlayArgs = MapUtils.getMap(overlayCfg, Constants.Request.ARGUMENTS_KEY, Map.of());
        var clusterRequestArg = Map.<String, Object>of(Ck8sConstants.Arguments.CLUSTER_REQUEST, Ck8sUtils.buildClusterRequest(ck8s, clusterAlias));

        var defaultArgs = new LinkedHashMap<String, Object>();
        defaultArgs.put(Ck8sConstants.Arguments.CONCORD_URL, "https://concord.local.localhost");
        defaultArgs.put(Ck8sConstants.Arguments.LOCAL_CONCORD_CLI, true);
        defaultArgs.put(Ck8sConstants.Arguments.FLOW, flowName);
        defaultArgs.put(Ck8sConstants.Arguments.CLIENT_CLUSTER, clusterAlias);
        defaultArgs.put(Ck8sConstants.Arguments.INPUT_ARGS, userArguments);

        var args = new LinkedHashMap<>(deepMerge(deepMerge(overlayArgs, clusterRequestArg), userArguments));
        args.putAll(defaultArgs);

        if (secretsProvider != null) {
            ConfigurationUtils.set(args, secretsProvider.name(), "clusterRequest", "secretsProvider");
        }

        args.put(Constants.Context.TX_ID_KEY, instanceId.toString());
        args.put(Constants.Context.WORK_DIR_KEY, targetDir.toAbsolutePath().toString());
        args.put(Constants.Request.INITIATOR_KEY, Map.of("displayName", "cli"));
        return args;
    }

    private static DependencyManagerConfiguration getDependencyManagerConfiguration(boolean offlineMode) {
        var cfgFile = new MvnJsonProvider().get();
        var depsCacheDir = ck8sHome().resolve("depsCache");
        return DependencyManagerConfiguration.builder().from(DependencyManagerConfiguration.of(depsCacheDir, DependencyManagerRepositories.get(cfgFile)))
                .offlineMode(offlineMode)
                .build();
    }

    private static Path ck8sHome() {
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

    private static ProcessInfo processInfo(List<String> activeProfiles) {
        return ProcessInfo.builder()
                .activeProfiles(activeProfiles)
                .build();
    }

    private static void dumpArguments(Map<String, Object> args) {
        var om = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        try {
            LogUtils.info("Process arguments:\n{}", om.writerWithDefaultPrettyPrinter().writeValueAsString(args));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ProjectInfo projectInfo(String orgName) {
        return ProjectInfo.builder()
                .orgName(orgName)
                .build();
    }

    public static class ApiClientProvider implements Provider<ApiClient> {
        private final ApiClientFactory clientFactory;
        private final ConcordProfile profile;

        @Inject
        public ApiClientProvider(ApiClientFactory clientFactory, ConcordProfile profile) {
            this.clientFactory = clientFactory;
            this.profile = profile;
        }

        public ApiClient get() {
            return this.clientFactory.create(ApiClientConfiguration.builder()
                    .baseUrl(profile.baseUrl())
                    .apiKey(profile.apiKey())
                    .build());
        }
    }

    private static class Logger extends SimpleLogger {

        @Override
        public Long createSegment(String segmentName, UUID correlationId) {
            return 0L;
        }

        @Override
        public void withContext(LogContext context, Runnable runnable) {
            ThreadGroup threadGroup = new LogContextThreadGroup(context);
            executeInThreadGroup(threadGroup, "thread-" + context.segmentName(), runnable);
        }

        private static void executeInThreadGroup(ThreadGroup group, String threadName, Runnable runnable) {
            var executor = Executors.newSingleThreadExecutor(new ThreadGroupAwareThreadFactory(group, threadName));
            var result = executor.submit(runnable);
            try {
                result.get();
            } catch (InterruptedException e) { // NOSONAR
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                var cause = e.getCause();
                if (cause != null) {
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else {
                        throw new RuntimeException(cause);
                    }
                }

                throw new RuntimeException(e);
            }
        }

        private record ThreadGroupAwareThreadFactory(ThreadGroup group, String threadName) implements ThreadFactory {

            public Thread newThread(Runnable r) {
                return new Thread(this.group, r, this.threadName);
            }
        }
    }
}
