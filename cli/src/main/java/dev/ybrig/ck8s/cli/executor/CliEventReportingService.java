package dev.ybrig.ck8s.cli.executor;

import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.runtime.v2.runner.EventReportingService;
import com.walmartlabs.concord.svm.ExecutionListener;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import dev.ybrig.ck8s.cli.common.Mapper;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public class CliEventReportingService implements EventReportingService, ExecutionListener {

    private final Path outputFile;

    private volatile boolean isFirstEvent = true;

    public CliEventReportingService(Path eventsDir, UUID instanceId) {
        try {
            Files.createDirectories(eventsDir);
        } catch (IOException e) {
            throw new RuntimeException("can't create event dir " + eventsDir, e);
        }
        this.outputFile = eventsDir.resolve(instanceId + ".events.json");
    }

    @Override
    public void beforeProcessStart(Runtime runtime, State state) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (Files.size(outputFile) == 0) {
                out.write("[".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LogUtils.error("Error writing to events file '{}': {}", outputFile, e.getMessage(), e);
        }
    }

    @Override
    public void beforeProcessResume(Runtime runtime, State state) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (Files.size(outputFile) == 0) {
                out.write("[".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LogUtils.error("Error writing to events file '{}': {}", outputFile, e.getMessage(), e);
        }
    }

    @Override
    public synchronized void report(ProcessEventRequest processEventRequest) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (!isFirstEvent) {
                out.write(",".getBytes(StandardCharsets.UTF_8));
            }

            Mapper.jsonMapper().write(out, processEventRequest);
            isFirstEvent = false;
        } catch (IOException e) {
            LogUtils.error("Error writing to events file '{}': {}", outputFile, e.getMessage(), e);
        }
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        endEventsFile(outputFile);
    }

    @Override
    public void onProcessError(Runtime runtime, State state, Exception e) {
        endEventsFile(outputFile);
    }

    private static void endEventsFile(Path outputFile) {
        try (var out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            out.write("]".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LogUtils.error("Error writing to events file '{}': {}", outputFile, e.getMessage());
        }
    }
}
