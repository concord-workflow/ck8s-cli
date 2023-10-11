package dev.ybrig.ck8s.cli;

import dev.ybrig.ck8s.cli.actions.ActionType;
import dev.ybrig.ck8s.cli.cfg.CliDefaultParamValuesProvider;
import dev.ybrig.ck8s.cli.completion.ClusterAliasCompletion;
import dev.ybrig.ck8s.cli.completion.FlowCompletion;
import dev.ybrig.ck8s.cli.completion.ProfilesCompletion;
import dev.ybrig.ck8s.cli.op.*;
import dev.ybrig.ck8s.cli.selfupdate.SelfUpdateCommand;
import dev.ybrig.ck8s.cli.sso.GenerateTokenCommand;
import dev.ybrig.ck8s.cli.utils.EnumCompletionCandidates;
import dev.ybrig.ck8s.cli.utils.EnumConverter;
import picocli.AutoComplete;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.util.Objects.nonNull;

@CommandLine.Command(name = "ck8s-cli",
        versionProvider = VersionProvider.class,
        subcommands = {AutoComplete.GenerateCompletion.class, SelfUpdateCommand.class, GenerateTokenCommand.class},
        defaultValueProvider = CliDefaultParamValuesProvider.class)
public class CliApp
        implements Callable<Integer>
{
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

    @CommandLine.Option(names = {"--withInputAssert"}, description = "assert flow call input parameters aat runtime")
    boolean withInputAssert = false;

    @CommandLine.Option(names = {"-V", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity. For example, `-V -V -V` or `-VVV`",
            "-V log flow steps",
            "-VV log task input/output args",
            "-VVV debug logs"})
    boolean[] verbosity = new boolean[0];

    @CommandLine.Option(names = {"-t", "--dry-run"}, description = "Test mode: Only display the command that will be executed")
    boolean testMode = false;

    @CommandLine.Option(names = {"--skip-confirm"}, description = "Skip confirmation prompts and perform the action without user confirmation. Use this option to automate the process without manual approvals")
    boolean skipConfirm = false;

    @CommandLine.Option(names = {"--version"}, versionHelp = true, description = "display version info")
    boolean versionInfoRequested;

    @CommandLine.Option(names = {"--target-root"}, description = "path to target dir")
    Path targetRootPath = Path.of(System.getProperty("java.io.tmpdir")).resolve("ck8s-cli");

    static class CliOperationArgs
    {
        @CommandLine.Option(names = {"-f", "--flow"}, description = "run the specified Concord flow", completionCandidates = FlowCompletion.class)
        String flow;

        @CommandLine.Option(names = {
                "-a"}, description = "actions: ${COMPLETION-CANDIDATES}", completionCandidates = ActionTypeCompletionCandidates.class, converter = ActionTypeConverter.class)
        ActionType actionType;

        @CommandLine.Option(names = {"-l", "--list"}, description = "list cluster names/aliases")
        boolean clusterList = false;
    }

    public FlowExecutorOptionsMixin getFlowExecutorType()
    {
        return flowExecutorType;
    }

    public String getFlow()
    {
        return cliOperationArgs.flow;
    }

    public ActionType getActionType()
    {
        return cliOperationArgs.actionType;
    }

    public CommandLine.Model.CommandSpec getSpec()
    {
        return spec;
    }

    public String getProfile()
    {
        return profile;
    }

    public String getClusterAlias()
    {
        return clusterAlias;
    }

    public Map<String, String> getExtraVars()
    {
        return extraVars;
    }

    public boolean isWithTests()
    {
        return withTests;
    }

    public SecretsProvider getSecretsProvider() {
        return secretsProvider;
    }

    public boolean isWithInputAssert()
    {
        return withInputAssert;
    }

    public boolean isTestMode()
    {
        return testMode;
    }

    public Path getTargetRootPath()
    {
        return targetRootPath;
    }

    public boolean[] getVerbosity()
    {
        return verbosity;
    }

    public Path getCk8sPath()
    {
        return ck8sPathOptions.getCk8sPath();
    }

    public Path getCk8sExtPath()
    {
        return ck8sPathOptions.getCk8sExtPath();
    }

    public boolean isSkipConfirm() {
        return skipConfirm;
    }

    @Override
    public Integer call()
    {
        return resolveOperation()
                .execute(new CliOperationContext(this));
    }

    private CliOperation resolveOperation()
    {
        if (cliOperationArgs != null) {
            if (nonNull(cliOperationArgs.actionType)) {
                return new ScriptActionOperation();
            }
            if (cliOperationArgs.clusterList) {
                return new ClusterListOperation();
            }
            if (nonNull(cliOperationArgs.flow)) {
                if ("cluster".equals(cliOperationArgs.flow)
                        && "local".equals(clusterAlias)) {
                    return new LocalClusterOperation();
                }
                return new RunFlowOperation();
            }
        }

        return new DefaultOperation();
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

    public enum SecretsProvider {
        local
    }
}
