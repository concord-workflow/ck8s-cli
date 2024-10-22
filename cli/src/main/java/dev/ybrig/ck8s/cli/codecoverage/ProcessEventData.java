package dev.ybrig.ck8s.cli.codecoverage;

import com.walmartlabs.concord.client2.ProcessEventEntry;
import dev.ybrig.ck8s.cli.common.MapUtils;

public class ProcessEventData {

    public static String phase(ProcessEventEntry entry) {
        return MapUtils.getString(entry.getData(), "phase");
    }

    public static String fileName(ProcessEventEntry event) {
        return MapUtils.getString(event.getData(), "fileName");
    }

    public static Integer line(ProcessEventEntry event) {
        Number lineNum = MapUtils.get(event.getData(), "line", null, Number.class);
        if (lineNum == null) {
            return null;
        }
        return lineNum.intValue();
    }

    public static boolean isFlowCall(ProcessEventEntry event) {
        String description = MapUtils.getString(event.getData(), "description");
        if (description == null) {
            return false;
        }

        return description.startsWith("Flow call: ");
    }

    public static String flowCallName(ProcessEventEntry event) {
        String description = MapUtils.getString(event.getData(), "description");
        if (description == null) {
            return null;
        }
        if (description.startsWith("Flow call: ")) {
            return description.substring("Flow call: ".length());
        }

        return null;
    }

    public static String processDefinitionId(ProcessEventEntry event) {
        return MapUtils.getString(event.getData(), "processDefinitionId");
    }
}
