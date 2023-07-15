package brig.ck8s.concord;

import brig.ck8s.utils.LogUtils;
import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.Call;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;

import java.lang.reflect.Type;
import java.util.*;

public class ProcessLogStreamer
        implements Runnable
{

    private static final String PATH_TEMPLATE = "/api/v1/process/%s/log";
    private static final long ERROR_DELAY = 5000;
    private static final long REQUEST_DELAY = 3000;
    private static final long RANGE_INCREMENT = 1024;
    private static final Type BYTE_ARRAY_TYPE = new TypeToken<byte[]>()
    {
    }.getType();
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
        Set<String> auths = client.getAuthentications().keySet();
        String[] authNames = auths.toArray(new String[0]);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Map<String, String> headers = new HashMap<>();
                headers.put("Range", "bytes=" + rangeStart + "-" + (rangeEnd != null ? rangeEnd : ""));

                String path = String.format(PATH_TEMPLATE, instanceId);
                Call c = client.buildCall(path, "GET", new ArrayList<>(), new ArrayList<>(), null, headers, new HashMap<>(), authNames, null);

                byte[] ab = client.<byte[]>execute(c, BYTE_ARRAY_TYPE).getData();
                if (ab.length > 0) {
                    String data = new String(ab);
                    for (String line : data.split("\n")) {
                        System.out.print("[PROCESS] ");
                        System.out.println(line);
                    }

                    rangeStart += ab.length;
                    rangeEnd = rangeStart + RANGE_INCREMENT;
                }
                else {
                    ProcessApi processApi = new ProcessApi(client);
                    ProcessEntry e = processApi.get(instanceId);
                    StatusEnum s = e.getStatus();
                    if (FINAL_STATUSES.contains(s)) {
                        LogUtils.info("Process {} is completed, stopping the log streaming...", instanceId);
                        break;
                    }
                }

                sleep(REQUEST_DELAY);
            }
            catch (ApiException e) {
                LogUtils.info("Error while streaming the process' ({}) log: {}. Retrying in {}ms...", instanceId, e.getMessage(), ERROR_DELAY);
                sleep(ERROR_DELAY);
            }
        }
    }
}
