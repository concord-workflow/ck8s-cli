package brig.ck8s;

import brig.ck8s.actions.ActionType;
import brig.ck8s.actions.BootstrapLocalClusterAction;
import brig.ck8s.actions.ClusterListAction;
import brig.ck8s.actions.ExecuteScriptAction;
import brig.ck8s.concord.Ck8sFlowBuilder;
import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.executor.FlowExecutor;
import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.EnumCompletionCandidates;
import brig.ck8s.utils.EnumConverter;
import brig.ck8s.utils.LogUtils;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.AutoComplete;
import picocli.CommandLine;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@QuarkusMain
@TopCommand
@CommandLine.Command(name = "ck8s-cli",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        subcommands = {AutoComplete.GenerateCompletion.class})
public class CliApp implements Callable<Integer>, QuarkusApplication {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    CommandLine.IFactory factory;

    @CommandLine.Mixin
    Ck8sPathOptionsMixin ck8sPathOptions;

    @CommandLine.Mixin
    TargetPathOptionsMixin targetPathOptions;

    @CommandLine.Mixin
    FlowExecutorOptionsMixin flowExecutorType;

    @CommandLine.Option(names = {"-v", "--vars"}, description = "additional process variables")
    Map<String, String> extraVars = new LinkedHashMap<>();

    @CommandLine.Option(names = {"-f", "--flow"}, description = "run the specified Concord flow")
    String flow;

    @CommandLine.Option(names = {"-c", "--cluster"}, description = "alias of the cluster (this will find the right Concord YAML)")
    String clusterAlias;

    @CommandLine.Option(names = {"-l", "--list"}, description = "list cluster names/aliases")
    boolean clusterList = false;

    @CommandLine.Option(names = {"--withTests"}, description = "include test flows to concord payload")
    boolean withTests = false;

    @CommandLine.Option(names = {"-a"},description = "actions: ${COMPLETION-CANDIDATES}", completionCandidates = ActionTypeCompletionCandidates.class, converter = ActionTypeConverter.class)
    ActionType actionType;

    @CommandLine.Option(names = {"--verbose"}, description = "verbose output")
    boolean verbose = false;

    @Override
    public Integer call() {
        Ck8sPath ck8s = new Ck8sPath(ck8sPathOptions.getCk8sPath(), ck8sPathOptions.getCk8sExtPath());
        if (verbose) {
            LogUtils.info("Using ck8s path: {}", ck8s.ck8sDir());
            if (ck8s.ck8sExtDir() != null) {
                LogUtils.info("Using ck8s-ext path: {}", ck8s.ck8sExtDir());
            }

            LogUtils.info("Using target path: {}", targetPathOptions.getTargetRootPath());
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
                default -> throw new IllegalArgumentException("Unknown action type: " + actionType);
            }
        }

        if (clusterList) {
            return new ClusterListAction(ck8s).perform();
        }

        if (clusterAlias != null) {
            if ("local".equals(clusterAlias) && "cluster".equals(flow)) {
                return new BootstrapLocalClusterAction().perform();
            }

            Path payloadLocation = new Ck8sFlowBuilder(ck8s, targetPathOptions.getTargetRootPath())
                    .includeTests(withTests)
                    .build(clusterAlias);

            Ck8sPayload payload = Ck8sPayload.builder()
                    .location(payloadLocation)
                    .putAllArgs(extraVars)
                    .flow(flow)
                    .build();

            return new FlowExecutor().execute(flowExecutorType.getType(), payload, verbose);
        }

        spec.commandLine().usage(System.out);

        return -1;
    }

    @Override
    public int run(String... args) {
        CommandLine cmd = new CommandLine(this, factory)
                .setCaseInsensitiveEnumValuesAllowed(true);

        // hide generate-completion subcommand from usage help
        CommandLine gen = cmd.getSubcommands().get("generate-completion");
        gen.getCommandSpec().usageMessage().hidden(true);

        return cmd.execute(args);
    }

    static class ActionTypeCompletionCandidates extends EnumCompletionCandidates<ActionType> {

        public ActionTypeCompletionCandidates() {
            super(ActionType.class);
        }
    }

    static class ActionTypeConverter extends EnumConverter<ActionType> {

        public ActionTypeConverter() {
            super(ActionType.class);
        }
    }

}