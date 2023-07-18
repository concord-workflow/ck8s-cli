package brig.ck8s.cli;

import brig.ck8s.cli.actions.ActionType;
import brig.ck8s.cli.cfg.CliDefaultParamValuesProvider;
import brig.ck8s.cli.completion.ClusterAliasCompletion;
import brig.ck8s.cli.completion.FlowCompletion;
import brig.ck8s.cli.completion.ProfilesCompletion;
import brig.ck8s.cli.op.CliOperation;
import brig.ck8s.cli.op.CliOperationContext;
import brig.ck8s.cli.op.ClusterListOperation;
import brig.ck8s.cli.op.DefaultOperation;
import brig.ck8s.cli.op.LocalClusterOperation;
import brig.ck8s.cli.op.RunFlowOperation;
import brig.ck8s.cli.op.ScriptActionOperation;
import brig.ck8s.cli.selfupdate.SelfUpdateCommand;
import brig.ck8s.cli.sso.GenerateTokenCommand;
import brig.ck8s.cli.utils.EnumCompletionCandidates;
import brig.ck8s.cli.utils.EnumConverter;
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

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
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

    @CommandLine.Option(names = {"--withInputAssert"}, description = "assert flow call input parameters aat runtime")
    boolean withInputAssert = false;

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
    Path targetRootPath = Path.of(System.getProperty("user.dir")).resolve("target");

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

    @Override
    public Integer call()
    {
        return resolveOperation()
                .execute(new CliOperationContext(this));
    }

    private CliOperation resolveOperation()
    {
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
}
