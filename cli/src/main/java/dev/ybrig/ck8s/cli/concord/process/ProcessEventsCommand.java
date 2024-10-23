package dev.ybrig.ck8s.cli.concord.process;

import com.walmartlabs.concord.client2.*;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.Mapper;
import dev.ybrig.ck8s.cli.concord.CliApiClientFactory;
import dev.ybrig.ck8s.cli.model.CliConfiguration;
import dev.ybrig.ck8s.cli.model.ConcordProfile;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "process-events",
        description = {"Concord process events"}
)
public class ProcessEventsCommand implements Callable<Integer> {

    @CommandLine.Option(required = true, names = {"-p", "--profile"}, description = "concord instance profile name")
    String profile;

    @CommandLine.Option(names = {"--connect-timeout"}, description = "connection timeout")
    long connectTimeout = 30;

    @CommandLine.Option(required = true, names = {"--events-dir"}, description = "where to store events")
    Path eventsDir = null;

    @CommandLine.Parameters(index = "0", description = "Process ID")
    String instanceId;

    @Override
    public Integer call() throws Exception {
        ConcordProfile instanceProfile = CliConfigurationProvider.getConcordProfile(profile);

        UUID processId = UUID.fromString(instanceId);

        ApiClient apiClient = CliApiClientFactory.create(instanceProfile, connectTimeout);
        ProcessEventsApi eventsApi = new ProcessEventsApi(apiClient);
        ProcessV2Api processApi = new ProcessV2Api(apiClient);

        var processEntry = processApi.getProcess(processId, Set.of(ProcessDataInclude.CHILDREN_IDS.getValue()));
        grabProcessEvents(eventsApi, processEntry.getInstanceId());
        if (processEntry.getChildrenIds() != null) {
            for (var subprocessId : processEntry.getChildrenIds()) {
                grabProcessEvents(eventsApi, subprocessId);
            }
        }

        return 0;
    }

    private void grabProcessEvents(ProcessEventsApi api, UUID processId) throws Exception {
        List<ProcessEventEntry> allEvents = new ArrayList<>();
        int eventsLimit = 1000;
        Long fromId = null;
        while (true) {
            var events = api.listProcessEvents(processId, "ELEMENT", null, fromId, null, null, true, eventsLimit);
            allEvents.addAll(events);
            if (!events.isEmpty()) {
                fromId = events.get(events.size() - 1).getSeqId();
            } else {
                break;
            }
        }

        LogUtils.info("Loaded {} events for '{}' process", allEvents.size(), processId);

        Path eventsPath = eventsDir.resolve(processId + ".events.json");
        Files.createDirectories(eventsDir);
        Mapper.jsonMapper().write(eventsPath, allEvents);
    }
}
