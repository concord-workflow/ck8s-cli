package brig.ck8s.command.test;

import brig.ck8s.Ck8sPathOptionsMixin;
import brig.ck8s.FlowExecutorOptionsMixin;
import brig.ck8s.TargetPathOptionsMixin;
import brig.ck8s.concord.Ck8sFlowBuilder;
import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.executor.FlowExecutor;
import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.LogUtils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "test", description = "Run ck8s tests")
@SuppressWarnings("unused")
public class TestCommand implements Runnable {

    static class EntryPointFlowComponentNamesGroup {

        @CommandLine.Option(names = {"--entryPoint"}, description = "ck8s flows entry point")
        String entryPoint;

        @CommandLine.Option(names = {"-f", "--flow"}, description = "run the specified Concord flow")
        String flow;

        @CommandLine.Parameters(description = "component name for test")
        List<String> componentNames = Collections.emptyList();
    }

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin
    Ck8sPathOptionsMixin ck8sPathOptions;

    @CommandLine.Mixin
    TargetPathOptionsMixin targetPathOptions;

    @CommandLine.Mixin
    FlowExecutorOptionsMixin flowExecutorType;

    @CommandLine.Option(names = {"-c", "--cluster"}, required = true, description = "alias of the cluster")
    String clusterAlias;

    @CommandLine.ArgGroup(multiplicity = "1")
    EntryPointFlowComponentNamesGroup entryPointOrComponentNamesGroup;

    @CommandLine.Option(names = {"-v", "--vars"}, description = "additional process variables")
    Map<String, String> extraVars = new LinkedHashMap<>();

    @CommandLine.Option(names = {"--verbose"}, description = "verbose output")
    boolean verbose = false;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    boolean helpRequested = false;

    @Override
    public void run() {
        Ck8sPath ck8s = new Ck8sPath(ck8sPathOptions.getCk8sPath(), ck8sPathOptions.getCk8sExtPath());
        if (verbose) {
            LogUtils.info("Using ck8s path: {}", ck8s.ck8sDir());
            if (ck8s.ck8sExtDir() != null) {
                LogUtils.info("Using ck8s-ext path: {}", ck8s.ck8sExtDir());
            }

            LogUtils.info("Using target path: {}", targetPathOptions.getTargetRootPath());
        }

        if (!entryPointOrComponentNamesGroup.componentNames.isEmpty()) {
            startWithComponents(ck8s, entryPointOrComponentNamesGroup.componentNames);
            return;
        }

        Path payloadLocation = new Ck8sFlowBuilder(ck8s, targetPathOptions.getTargetRootPath())
                .includeTests()
                .build(clusterAlias);

        Ck8sPayload payload;
        if (entryPointOrComponentNamesGroup.entryPoint != null) {
            payload = payloadWithEntryPoint(payloadLocation, entryPointOrComponentNamesGroup.entryPoint);
        } else {
            payload = buildWithFlow(payloadLocation, entryPointOrComponentNamesGroup.flow);
        }

        if (verbose) {
            dump(payload);
        }

        new FlowExecutor().execute(flowExecutorType.getType(), payload, verbose);
    }

    private Ck8sPayload payloadWithEntryPoint(Path payloadLocation, String entryPoint) {
        return Ck8sPayload.builder()
                .location(payloadLocation)
                .entryPoint(entryPoint)
                .putAllArgs(extraVars)
                .build();
    }

    private Ck8sPayload buildWithFlow(Path payloadLocation, String flow) {
        return Ck8sPayload.builder()
                .location(payloadLocation)
                .flow(flow)
                .putAllArgs(extraVars)
                .build();
    }

    private void startWithComponents(Ck8sPath ck8s, List<String> componentNames) {
        for (String componentName : componentNames) {
            LogUtils.info("Executing tests for '{}'", componentName);
            Path payloadLocation = new Ck8sFlowBuilder(ck8s, targetPathOptions.getTargetRootPath())
                    .includeTests()
                    .build(clusterAlias);

            Ck8sPayload payload = Ck8sPayload.builder()
                    .location(payloadLocation)
                    .flow(componentName + "Tests")
                    .putAllArgs(extraVars)
                    .build();

            if (verbose) {
                dump(payload);
            }

            new FlowExecutor().execute(flowExecutorType.getType(), payload, verbose);
        }
    }

    private static void dump(Ck8sPayload payload) {
        LogUtils.info("Using entryPoint: '{}'", payload.entryPoint());
        LogUtils.info("Using arguments: '{}'", payload.args());
    }
}
