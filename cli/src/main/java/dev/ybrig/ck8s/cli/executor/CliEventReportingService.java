package dev.ybrig.ck8s.cli.executor;

import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.runtime.v2.runner.EventReportingService;
import dev.ybrig.ck8s.cli.common.Mapper;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class CliEventReportingService implements EventReportingService {

    private final Path outputFile;

    public CliEventReportingService(Path eventsDir, UUID instanceId) {
        try {
            Files.createDirectories(eventsDir);
        } catch (IOException e) {
            throw new RuntimeException("can't create event dir " + eventsDir, e);
        }
        this.outputFile = eventsDir.resolve(instanceId + ".events.json");
    }

    @Override
    public synchronized void report(ProcessEventRequest processEventRequest) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            Mapper.jsonMapper().write(out, processEventRequest);
        } catch (IOException e) {
            LogUtils.error("Error writing to events file '{}': {}", outputFile, e.getMessage());
        }
    }
}
