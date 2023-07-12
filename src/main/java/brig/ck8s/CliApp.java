package brig.ck8s;

import brig.ck8s.actions.*;
import brig.ck8s.cfg.CliConfigurationProvider;
import brig.ck8s.cfg.CliDefaultParamValuesProvider;
import brig.ck8s.completion.ClusterAliasCompletion;
import brig.ck8s.completion.FlowCompletion;
import brig.ck8s.completion.ProfilesCompletion;
import brig.ck8s.concord.Ck8sFlowBuilder;
import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.executor.FlowExecutor;
import brig.ck8s.model.ConcordProfile;
import brig.ck8s.selfupdate.SelfUpdateCommand;
import brig.ck8s.sso.GenerateTokenCommand;
import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.EnumCompletionCandidates;
import brig.ck8s.utils.EnumConverter;
import brig.ck8s.utils.LogUtils;
import com.walmartlabs.concord.cli.Verbosity;
import picocli.AutoComplete;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "ck8s-cli",
        versionProvider = VersionProvider.class,
        subcommands = {AutoComplete.GenerateCompletion.class, SelfUpdateCommand.class, GenerateTokenCommand.class},
        defaultValueProvider = CliDefaultParamValuesProvider.class)
public class CliApp
        implements Callable<Integer>
{

    private static final Set<String> flowPatternsToConfirm = Set.of("(?i).*delete.*", "(?i).*reinstall.*");
    private static final Set<String> confirmInput = Set.of("y", "yes");

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin
    Ck8sPathOptionsMixin ck8sPathOptions;

    @CommandLine.Mixin
    FlowExecutorOptionsMixin flowExecutorType;

    @CommandLine.Option(names = {"-v", "--vars"}, description = "additional process variables")
    Map<String, String> extraVars = new LinkedHashMap<>();

    @CommandLine.Option(names = {"-f", "--flow"}, description = "run the specified Concord flow", completionCandidates = FlowCompletion.class)
    String flow;

    @CommandLine.Option(names = {"-c",
            "--cluster"}, description = "alias of the cluster (this will find the right Concord YAML)", completionCandidates = ClusterAliasCompletion.class)
    String clusterAlias;

    @CommandLine.Option(names = {"-l", "--list"}, description = "list cluster names/aliases")
    boolean clusterList = false;

    @CommandLine.Option(names = {"--withTests"}, description = "include test flows to concord payload")
    boolean withTests = false;

    @CommandLine.Option(names = {"--withInputAssert"}, description = "assert flow call input parameters aat runtime")
    boolean withInputAssert= false;

    @CommandLine.Option(names = {
            "-a"}, description = "actions: ${COMPLETION-CANDIDATES}", completionCandidates = ActionTypeCompletionCandidates.class, converter = ActionTypeConverter.class)
    ActionType actionType;

    @CommandLine.Option(names = {"-p", "--profile"}, description = "concord instance profile name", completionCandidates = ProfilesCompletion.class)
    String profile = "default";

    @CommandLine.Option(names = {"-V", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity. For example, `-V -V -V` or `-VVV`",
            "-V log flow steps",
            "-VV log task input/output args",
            "-VVV debug logs"})
    boolean[] verbosity = new boolean[0];

    @CommandLine.Option(names = {"-t"}, description = "Test mode: Only display the command that will be executed")
    boolean testMode = false;

    @CommandLine.Option(names = {"--version"}, versionHelp = true, description = "display version info")
    boolean versionInfoRequested;

    @CommandLine.Option(names = {"--target-root"}, description = "path to target dir")
    private Path targetRootPath = Path.of(System.getProperty("user.dir")).resolve("target");

    @Override
    public Integer call()
    {
        Verbosity verbosity = new Verbosity(this.verbosity);

        Ck8sPath ck8s = new Ck8sPath(ck8sPathOptions.getCk8sPath(), ck8sPathOptions.getCk8sExtPath());
        if (verbosity.verbose()) {
            LogUtils.info("Using ck8s path: {}", ck8s.ck8sDir());
            if (ck8s.ck8sExtDir() != null) {
                LogUtils.info("Using ck8s-ext path: {}", ck8s.ck8sExtDir());
            }

            LogUtils.info("Using target path: {}", targetRootPath);
            LogUtils.info("Test mode: {}", testMode);
        }

        if (actionType != null) {
            ExecuteScriptAction scriptAction = new ExecuteScriptAction(ck8s);

            switch (actionType) {
                case UP -> {
                    return scriptAction.perform("ck8sUp");
                }
                case DOWN -> {
                    return scriptAction.perform("ck8sDown");
                }
                case DNSMASQ_SETUP -> {
                    return scriptAction.perform("dnsmasqSetup");
                }
                case DNSMASQ_RESTART -> {
                    return scriptAction.perform("dnsmasqRestart");
                }
                case DOCKER_REGISTRY -> {
                    return scriptAction.perform("ck8sDockerRegistry");
                }
                case INSTALL_CONCORD -> {
                    return scriptAction.perform("installConcord");
                }
                case REINSTALL_CONCORD_AGENT_POOL -> {
                    return scriptAction.perform("reinstallConcordAgentPool");
                }
                case CONSOLE -> {
                    ConcordProfile concordCfg = CliConfigurationProvider.getConcordProfile(profile);
                    Map<String, String> params = new HashMap<>();
                    params.put("CONCORD_URL", concordCfg.baseUrl());
                    params.put("CONCORD_ADMIN_TOKEN", concordCfg.apiKey());
                    return scriptAction.perform("ck8sConsole", params);
                }
                case AWS_KUBE_CONFIG -> {
                    return new AwsKubeconfigAction(ck8s, scriptAction).perform();
                }
                default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
            }
        }

        if (clusterList) {
            return new ClusterListAction(ck8s).perform();
        }

        if (clusterAlias != null) {
            if ("local".equals(clusterAlias) && "cluster".equals(flow)) {
                return new BootstrapLocalClusterAction(ck8s, targetRootPath, profile).perform();
            }

            boolean needConfirmation = flowPatternsToConfirm.stream().anyMatch(flow::matches);
            if (needConfirmation) {
                String msg = String.format("Are you sure you want to execute '%s' on '%s' cluster? (y/N): ", flow, clusterAlias);
                System.out.print(msg);

                try (Scanner input = new Scanner(System.in)) {
                    String confirm = input.nextLine();
                    if (!confirmInput.contains(confirm)) {
                        return -1;
                    }
                }
            }

            List<String> deps = Collections.emptyList();
            if (withInputAssert) {
                deps = List.of("mvn://com.walmartlabs.concord.plugins.basic:input-params-assert:1.102.1-SNAPSHOT");
            }

            Path payloadLocation = new Ck8sFlowBuilder(ck8s, targetRootPath)
                    .includeTests(withTests)
                    .withDependencies(deps)
                    .debug(verbosity.verbose())
                    .build(clusterAlias);

            Ck8sPayload payload = Ck8sPayload.builder()
                    .location(payloadLocation)
                    .putAllArgs(extraVars)
                    .flow(flow)
                    .build();

            return new FlowExecutor().execute(flowExecutorType.getType(), payload, profile, verbosity, testMode);
        }

        spec.commandLine().usage(System.out);

        return -1;
    }

    static class ActionTypeCompletionCandidates
            extends EnumCompletionCandidates<ActionType>
    {

        public ActionTypeCompletionCandidates()
        {
            super(ActionType.class);
        }
    }

    static class ActionTypeConverter
            extends EnumConverter<ActionType>
    {

        public ActionTypeConverter()
        {
            super(ActionType.class);
        }
    }
}