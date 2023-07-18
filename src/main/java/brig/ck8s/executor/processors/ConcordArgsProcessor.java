package brig.ck8s.executor.processors;

import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.utils.Ck8sUtils;
import brig.ck8s.utils.MapUtils;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.util.Collections;
import java.util.Map;

public class ConcordArgsProcessor implements PayloadProcessor
{

    @Override
    public Ck8sPayload process(Ck8sPayload payload)
    {
        String flowName = payload.flowName();
        if (flowName == null) {
            return payload;
        }

        ProcessDefinition pd = Ck8sUtils.findYaml(payload.flowsPath(), flowName);
        if (pd == null) {
            return payload;
        }

        Map<String, Object> concordArgs = MapUtils.getMap(pd.configuration().arguments(), "concord", Collections.emptyMap());
        String globalExclusiveGroup = MapUtils.getString(concordArgs, "globalExclusiveGroup", "");

        return Ck8sPayload.builder().from(payload)
                .concord(concordArgs)
                .putArgs("hasGlobalLock", !globalExclusiveGroup.trim().isEmpty())
                .putArgs("globalExclusiveGroup", globalExclusiveGroup)
                .build();
    }
}
