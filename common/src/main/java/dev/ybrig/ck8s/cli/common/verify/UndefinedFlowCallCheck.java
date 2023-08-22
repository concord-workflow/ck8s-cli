package dev.ybrig.ck8s.cli.common.verify;

import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class UndefinedFlowCallCheck extends AbstractChecker {

    private final ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());

    private final Set<String> allFlows = new HashSet<>();
    private final Map<Path, List<FlowCall>> undefinedFlowCalls = new HashMap<>();

    @Override
    public void process(Path concordYaml) {
        ProjectLoaderV2.Result result;
        try {
            result = loader.loadFromFile(concordYaml);
        } catch (Exception e) {
            addError(concordYaml, "Can't parse yaml: " + e.getMessage());
            return;
        }

        ProcessDefinition processDefinition = result.getProjectDefinition();
        Map<String, List<Step>> flows = processDefinition.flows();
        allFlows.addAll(flows.keySet());

        List<FlowCall> flowCalls = new ArrayList<>();
        for (Map.Entry<String, List<Step>> e : flows.entrySet()) {
            collectFlowCallSteps(e.getValue(), flowCalls);
        }

        List<FlowCall> plainFlowCalls = flowCalls.stream().filter(f -> !isExpression(f.getFlowName())).collect(Collectors.toList());
        undefinedFlowCalls.put(concordYaml, plainFlowCalls);

        for(Iterator<Map.Entry<Path, List<FlowCall>>> it = undefinedFlowCalls.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Path, List<FlowCall>> entry = it.next();

            entry.getValue().removeIf(f -> allFlows.contains(f.getFlowName()));

            if(entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    @Override
    public List<CheckError> errors() {
        List<CheckError> errors = super.errors();
        for (Map.Entry<Path, List<FlowCall>> e : undefinedFlowCalls.entrySet()) {
            for (FlowCall flowCall : e.getValue()) {
                errors.add(CheckError.of(e.getKey(), "Call to undefined flow '" + flowCall.getFlowName() + "'"));
            }
        }
        return super.errors();
    }

    private static void collectFlowCallSteps(List<Step> steps, List<FlowCall> flowCallSteps) {
        if (steps == null) {
            return;
        }

        for (Step step : steps) {
            if (step instanceof FlowCall) {
                flowCallSteps.add((FlowCall) step);
            } else if (step instanceof GroupOfSteps) {
                collectFlowCallSteps(((GroupOfSteps) step).getSteps(), flowCallSteps);
            } else if (step instanceof ParallelBlock) {
                collectFlowCallSteps(((ParallelBlock) step).getSteps(), flowCallSteps);
            } else if (step instanceof IfStep) {
                collectFlowCallSteps(((IfStep) step).getThenSteps(), flowCallSteps);
                collectFlowCallSteps(((IfStep) step).getElseSteps(), flowCallSteps);
            }
        }
    }

    private static boolean isExpression(String s) {
        int i = s.indexOf("${");
        return i >= 0 && s.indexOf("}", i) > i;
    }
}
