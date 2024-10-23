package dev.ybrig.ck8s.cli.concord;

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.ProcessV2Api;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class RemoteConcordProcess extends ConcordProcess {

    private static final long ERROR_DELAY = 5000;
    private static final long REQUEST_DELAY = 20000;

    private static final Set<ProcessEntry.StatusEnum> FINAL_STATUSES = new HashSet<>(Arrays.asList(
            ProcessEntry.StatusEnum.FINISHED,
            ProcessEntry.StatusEnum.CANCELLED,
            ProcessEntry.StatusEnum.FAILED,
            ProcessEntry.StatusEnum.TIMED_OUT
    ));

    private final ApiClient client;

    public RemoteConcordProcess(ApiClient client, UUID instanceId) {
        super(instanceId);
        this.client = client;
    }

    @Override
    public void streamLogs(ExecutorService executor) {
        Future<?> f = executor.submit(new ProcessLogStreamer(client, instanceId));
        try {
            f.get();
        } catch (Exception e) {
            throw new RuntimeException("Stream logs error: " + e.getMessage());
        }
    }

    @Override
    public void waitEnded(long waitTimeout) {
        long started = System.currentTimeMillis();

        LogUtils.info("Waiting for process to finish (timeout {} seconds)", waitTimeout / 1000);
        while (!Thread.currentThread().isInterrupted() && (started + waitTimeout > System.currentTimeMillis())) {
            try {
                ProcessV2Api processV2Api = new ProcessV2Api(client);
                ProcessEntry e = processV2Api.getProcess(instanceId, Collections.emptySet());
                ProcessEntry.StatusEnum s = e.getStatus();
                if (FINAL_STATUSES.contains(s)) {
                    LogUtils.info("Process {} is completed with status {}", instanceId, s);
                    break;
                }

                LogUtils.info("\t Process status {}", s);

                sleep(REQUEST_DELAY);
            } catch (Exception e) {
                LogUtils.info("Wait error {}. Retrying in {}ms...", e.getMessage(), ERROR_DELAY);
                sleep(ERROR_DELAY);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
