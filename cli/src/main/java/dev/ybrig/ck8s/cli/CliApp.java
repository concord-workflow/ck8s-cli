package dev.ybrig.ck8s.cli;

import dev.ybrig.ck8s.cli.actions.ActionType;
import dev.ybrig.ck8s.cli.cfg.CliDefaultParamValuesProvider;
import dev.ybrig.ck8s.cli.completion.ClusterAliasCompletion;
import dev.ybrig.ck8s.cli.completion.ProfilesCompletion;
import dev.ybrig.ck8s.cli.concord.process.ProcessEventsCommand;
import dev.ybrig.ck8s.cli.executor.ExecutorType;
import dev.ybrig.ck8s.cli.forms.ServeFormsCommand;
import dev.ybrig.ck8s.cli.op.*;
import dev.ybrig.ck8s.cli.selfupdate.SelfUpdateCommand;
import dev.ybrig.ck8s.cli.sso.GenerateTokenCommand;
import dev.ybrig.ck8s.cli.utils.EnumCompletionCandidates;
import dev.ybrig.ck8s.cli.utils.EnumConverter;
import picocli.AutoComplete;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Objects.nonNull;

@CommandLine.Command(name = "ck8s-cli",
        versionProvider = VersionProvider.class,
        subcommands = {
                AutoComplete.GenerateCompletion.class,
                SelfUpdateCommand.class,
                GenerateTokenCommand.class,
                ServeFormsCommand.class,
                ProcessEventsCommand.class},
        defaultValueProvider = CliDefaultParamValuesProvider.class)
public class CliApp
        implements Callable<Integer> {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin
    Ck8sPathOptionsMixin ck8sPathOptions;

    @CommandLine.Mixin
    FlowExecutorOptionsMixin flowExecutorType;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    CliOperationArgs cliOperationArgs;

    @CommandLine.Option(names = {"-p", "--profile"}, description = "concord instance profile name", completionCandidates = ProfilesCompletion.class)
    String profile = "default";

    @CommandLine.Option(names = {"-c",
            "--cluster"}, description = "alias of the cluster (this will find the right Concord YAML)", completionCandidates = ClusterAliasCompletion.class)
    String clusterAlias;

    @CommandLine.Option(names = {"-v", "--vars"}, description = "additional process variables")
    Map<String, String> extraVars = new LinkedHashMap<>();

    @CommandLine.Option(names = {"--withTests"}, description = "include test flows to concord payload")
    boolean withTests = false;

    @CommandLine.Option(names = {"--secretsProvider"}, description = "secrets provider")
    SecretsProvider secretsProvider;

    @CommandLine.Option(names = {"--active-profiles", "--flow-profiles"}, description = "Concord active profiles", converter = ActiveProfilesConverter.class)
    List<String> activeProfiles = List.of();

    @CommandLine.Option(names = {"-V", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity. For example, `-V -V -V` or `-VVV`",
            "-V log flow steps",
            "-VV log task input/output args",
            "-VVV debug logs"})
    boolean[] verbosity = new boolean[0];

    @CommandLine.Option(names = {"--skip-confirm"}, description = "Skip confirmation prompts and perform the action without user confirmation. Use this option to automate the process without manual approvals")
    boolean skipConfirm = false;

    @CommandLine.Option(names = {"--version"}, versionHelp = true, description = "display version info")
    boolean versionInfoRequested;

    @CommandLine.Option(names = {"--target-root"}, description = "path to target dir")
    Path targetRootPath = Path.of(System.getProperty("java.io.tmpdir")).resolve("ck8s-cli");

    @CommandLine.Option(names = {"--with-local-deps"}, description = "Do not resole dependencies from remote, use local cached")
    boolean withLocalDependencies = false;

    @CommandLine.Option(names = {"--connect-timeout"}, description = "Connection timeout")
    long connectTimeout = 120;

    @CommandLine.Option(names = {"--read-timeout"}, description = "Read timeout")
    long readTimeout = 120;

    @CommandLine.Option(names = {"--events-dir"}, description = "Where t store events")
    Path eventsDir = null;

    @CommandLine.Option(names = {"--wait"}, description = "Wait N seconds till process finished")
    Integer waitSeconds = null;

    @CommandLine.Option(names = {"--stream-logs"}, description = "Stream process logs")
    boolean streamLogs = false;

    @CommandLine.Option(names = {"--dry-run"}, description = "Execute flows in dry run mode")
    boolean dryRunMode = false;

    @CommandLine.Option(names = {"--project"}, description = "Concord project")
    String project;

    @CommandLine.Option(names = {"--ck8sRef"}, description = "ck8s GH ref")
    String ck8sRef;

    @CommandLine.Option(names = {"-m", "--meta"}, description = "additional process meta")
    Map<String, String> meta = new LinkedHashMap<>();

    public ExecutorType getFlowExecutorType() {
        if (flowExecutorType == null) {
            return null;
        }

        return flowExecutorType.getType();
    }

    public String getFlow() {
        return cliOperationArgs.flow;
    }

    public ActionType getActionType() {
        return cliOperationArgs.actionType;
    }

    public CommandLine.Model.CommandSpec getSpec() {
        return spec;
    }

    public String getProfile() {
        return profile;
    }

    public String getClusterAlias() {
        return clusterAlias;
    }

    public Map<String, String> getExtraVars() {
        return extraVars;
    }

    public boolean isWithTests() {
        return withTests;
    }

    public SecretsProvider getSecretsProvider() {
        return secretsProvider;
    }

    public Path getTargetRootPath() {
        return targetRootPath;
    }

    public boolean[] getVerbosity() {
        return verbosity;
    }

    public Path getCk8sPath() {
        return ck8sPathOptions.getCk8sPath();
    }

    public Path getCk8sExtPath() {
        return ck8sPathOptions.getCk8sExtPath();
    }

    public boolean isSkipConfirm() {
        return skipConfirm;
    }

    public List<String> getActiveProfiles() {
        return activeProfiles;
    }

    public boolean isWithLocalDependencies() {
        return withLocalDependencies;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public long getReadTimeout() {
        return readTimeout;
    }

    public Path getEventsDir() {
        return eventsDir;
    }

    public Integer getWaitSeconds() {
        return waitSeconds;
    }

    public boolean isStreamLogs() {
        return streamLogs;
    }

    public boolean isDryRunMode() {
        return dryRunMode;
    }

    @Override
    public Integer call() {
        return resolveOperation()
                .execute(new CliOperationContext(this));
    }

    public String getProject() {
        return project;
    }

    public String getCk8sRef() {
        return ck8sRef;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    private CliOperation resolveOperation() {
        if (cliOperationArgs != null) {
            if (nonNull(cliOperationArgs.actionType)) {
                return new ScriptActionOperation();
            }

            if (cliOperationArgs.clusterList) {
                return new ClusterListOperation();
            }

            if (nonNull(cliOperationArgs.flow)) {
                if (clusterAlias == null) {
                    throw new CommandLine.ParameterException(new CommandLine(this), "Missing required option: '--cluster=<clusterAlias>'");
                }
                if (getFlowExecutorType() == null) {
                    throw new CommandLine.ParameterException(new CommandLine(this), "Missing required option: '--flow-executor=<flowExecutor>'");
                }

                return switch (getFlowExecutorType()) {
                    case REMOTE -> new RemoteRunFlowOperation();
                    case CONCORD_CLI -> new LocalRunFlowOperation();
                };
            }
        }

        return new DefaultOperation();
    }

    public enum SecretsProvider {
        local
    }

    static class CliOperationArgs {
        @CommandLine.Option(names = {"-f", "--flow"}, description = "run the specified Concord flow")
        String flow;

        @CommandLine.Option(names = {
                "-a"}, description = "actions: ${COMPLETION-CANDIDATES}", completionCandidates = ActionTypeCompletionCandidates.class, converter = ActionTypeConverter.class)
        ActionType actionType;

        @CommandLine.Option(names = {"-l", "--list"}, description = "list cluster names/aliases")
        boolean clusterList = false;
    }

    static class ActionTypeCompletionCandidates
            extends EnumCompletionCandidates<ActionType> {

        public ActionTypeCompletionCandidates() {
            super(ActionType.class);
        }
    }

    static class ActionTypeConverter
            extends EnumConverter<ActionType> {

        public ActionTypeConverter() {
            super(ActionType.class);
        }
    }

    static class ActiveProfilesConverter implements CommandLine.ITypeConverter<List<String>> {

        @Override
        public List<String> convert(String value) {
            return Arrays.asList(value.split(","));
        }
    }
}
