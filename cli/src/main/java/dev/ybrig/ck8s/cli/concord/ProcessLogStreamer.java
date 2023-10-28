package dev.ybrig.ck8s.cli.concord;

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.client2.ProcessV2Api;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import java.io.InputStream;
import java.util.*;

public class ProcessLogStreamer
        implements Runnable
{

    private static final long ERROR_DELAY = 5000;
    private static final long REQUEST_DELAY = 3000;
    private static final long RANGE_INCREMENT = 1024;

    private static final Set<StatusEnum> FINAL_STATUSES = new HashSet<>(Arrays.asList(
            StatusEnum.FINISHED,
            StatusEnum.CANCELLED,
            StatusEnum.FAILED,
            StatusEnum.TIMED_OUT
    ));

    private final ApiClient client;
    private final UUID instanceId;

    private long rangeStart = 0L;
    private Long rangeEnd;

    public ProcessLogStreamer(ApiClient client, UUID instanceId)
    {
        this.client = client;
        this.instanceId = instanceId;
    }

    private static void sleep(long ms)
    {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run()
    {
        ProcessApi processApi = new ProcessApi(client);
        ProcessV2Api processV2Api = new ProcessV2Api(client);

        while (!Thread.currentThread().isInterrupted()) {
            try (InputStream is = processApi.getProcessLog(instanceId, "bytes=" + rangeStart + "-" + (rangeEnd != null ? rangeEnd : ""))) {
                byte[] ab = is.readAllBytes();

                if (ab.length > 0) {
                    String data = new String(ab);
                    for (String line : data.split("\n")) {
                        System.out.print("[PROCESS] ");
                        System.out.println(line);
                    }

                    rangeStart += ab.length;
                    rangeEnd = rangeStart + RANGE_INCREMENT;
                } else {
                    ProcessEntry e = processV2Api.getProcess(instanceId, Collections.emptySet());
                    StatusEnum s = e.getStatus();
                    if (FINAL_STATUSES.contains(s)) {
                        LogUtils.info("Process {} is completed, stopping the log streaming...", instanceId);
                        break;
                    }
                }

                sleep(REQUEST_DELAY);
            } catch (Exception e) {
                LogUtils.info("Error while streaming the process' ({}) log: {}. Retrying in {}ms...", instanceId, e.getMessage(), ERROR_DELAY);
                sleep(ERROR_DELAY);
            }
        }
    }
}
