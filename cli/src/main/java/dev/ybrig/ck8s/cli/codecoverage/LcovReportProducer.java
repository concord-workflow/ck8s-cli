package dev.ybrig.ck8s.cli.codecoverage;

import com.walmartlabs.concord.client2.ProcessEventEntry;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LcovReportProducer {

    private static final Logger log = LoggerFactory.getLogger(LcovReportProducer.class);

    private final Map<String, Stats> statsPerFile = new HashMap<>();

    public void init(ProcessDefinition processDefinition) {
        for (var fd : processDefinition.flows().entrySet()) {
            var fileName = fd.getValue().location().fileName();

            var stats = statsPerFile.computeIfAbsent(fileName, k -> new Stats());
            stats.init(fd.getKey(), fd.getValue().location().lineNum());
        }
    }

    public void onEvents(List<ProcessEventEntry> events) {
        for (var event : events) {
            if ("post".equals(ProcessEventData.phase(event))) {
                continue;
            }

            var fileName = ProcessEventData.fileName(event);
            var lineNum = ProcessEventData.line(event);
            if (lineNum == null) {
                continue;
            }

            var stats = statsPerFile.get(fileName);
            if (stats == null) {
                log.warn("Can't find definitions for {}", fileName);
                continue;
            }

            stats.onLineExecuted(lineNum);

            var flowCallName = ProcessEventData.flowCallName(event);
            if (flowCallName != null) {
                var statsForFlow = findStatsForFlow(flowCallName);
                if (statsForFlow != null) {
                    statsForFlow.onFlowCall(flowCallName);
                }
            }
            var processDefinitionId = ProcessEventData.processDefinitionId(event);
            if (processDefinitionId != null) {
                // as we do not have call step for entry point
                var statsForFlow = findStatsForFlow(processDefinitionId);
                if (statsForFlow != null) {
                    statsForFlow.markCalled(processDefinitionId);
                }
            }
        }
    }

    public void produce(Path baseDir) throws IOException {
        var reportName = baseDir.resolve("coverage.info");

        try (var writer = Files.newBufferedWriter(reportName, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (var statsEntry : statsPerFile.entrySet()) {
                // TN (Test Name):
                // Optional. Can be left empty or used to specify the name of the test.
                writer.write("TN:");
                writer.newLine();

                // SF (Source File Path):
                // Specifies the path to the source file for which the coverage data is provided.
                writer.write("SF:" + statsEntry.getKey());
                writer.newLine();

                var stats = statsEntry.getValue();
                for (var flowEntry : stats.flowLocationByName.entrySet()) {
                    // FN (Function):
                    // The start line and name of the function in the source file.
                    writer.write(String.format("FN:%d,%s", flowEntry.getValue(), flowEntry.getKey()));
                    writer.newLine();
                }

                for (var execEntry : stats.flowExecCountByName.entrySet()) {
                    // FNDA (Function Data):
                    // The number of times the function was executed, followed by the function name.
                    writer.write(String.format("FNDA:%d,%s", execEntry.getValue(), execEntry.getKey()));
                    writer.newLine();
                }

                // Function summary
                // FNF (Functions Found):
                // The number of functions found in the file.
                writer.write("FNF:" + stats.flowLocationByName.size());
                writer.newLine();

                // FNH (Functions Hit):
                // The number of functions that were executed at least once.
                writer.write("FNH:" + stats.flowExecCountByName.size());
                writer.newLine();

                // Line coverage data
                // DA (Data Array):
                // Line number and the execution count for that line.
                for (var e : stats.stepExecCountByLineNumber.entrySet()) {
                    writer.write(String.format("DA:%d,%d", e.getKey(), e.getValue()));
                    writer.newLine();
                }

                // Branch coverage data
                // BRDA (Branch Data Array):
                // Line number, block number, branch number, and the execution count for branches.
                //
                // Branch summary
                // BRF (Branches Found):
                // The total number of branches found.
                //
                // BRH (Branches Hit):
                // The number of branches that were taken.

                // line summary
                // LF (Lines Found):
                // The total number of lines found in the source file.
                writer.write("LF:7");
                writer.newLine();
                // LH (Lines Hit):
                // The number of lines that were executed at least once.
                writer.write("LH:6");
                writer.newLine();

                // End of record
                writer.write("end_of_record");
                writer.newLine();
            }
        }
    }

    private Stats findStatsForFlow(String flow) {
        for (var stats : statsPerFile.entrySet()) {
            if (stats.getValue().containsFlow(flow)) {
                return stats.getValue();
            }
        }
        log.warn("Can't find stats for {} flow", flow);
        return null;
    }

    private static class Stats {

        private final Map<String, Integer> flowLocationByName = new HashMap<>();

        private final Map<String, Integer> flowExecCountByName = new HashMap<>();

        private final Map<Integer, Integer> stepExecCountByLineNumber = new HashMap<>();

        public void init(String flowName, int location) {
            flowLocationByName.put(flowName, location);
//            flowExecCountByName.put(flowName, 0);
            stepExecCountByLineNumber.put(location, 0);
        }

        public void onLineExecuted(int lineNum) {
            stepExecCountByLineNumber.compute(lineNum, (k, v) -> v == null ? 1 : v + 1);
        }

        public void onFlowCall(String flow) {
            assertContainsFlow(flow);

            flowExecCountByName.compute(flow, (k, v) -> v == null ? 1 : v + 1);
            onLineExecuted(flowLocationByName.get(flow));
        }

        public void markCalled(String flow) {
            assertContainsFlow(flow);

            var prev = flowExecCountByName.putIfAbsent(flow, 1);
            if (prev == null) {
                onLineExecuted(flowLocationByName.get(flow));
            }
        }

        public boolean containsFlow(String flow) {
            return flowLocationByName.containsKey(flow);
        }

        private void assertContainsFlow(String flow) {
            if (!flowLocationByName.containsKey(flow)) {
                throw new IllegalArgumentException("Trying update stats for flow without flow defintiion " + flow);
            }
        }
    }
}
