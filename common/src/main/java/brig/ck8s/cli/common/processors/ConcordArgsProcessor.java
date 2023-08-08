package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.common.Ck8sUtils;
import brig.ck8s.cli.common.MapUtils;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.util.Collections;
import java.util.Map;

import static brig.ck8s.cli.common.Ck8sPayload.*;

public class ConcordArgsProcessor implements PayloadProcessor
{

    @Override
    public Ck8sPayload process(String flowName, Ck8sPayload payload)
    {
        ProcessDefinition pd = Ck8sUtils.findYaml(payload.flows().flowsPath(), flowName);
        if (pd == null) {
            return payload;
        }

        Map<String, Object> flowConcordArgs = MapUtils.getMap(pd.configuration().arguments(), "concord", Collections.emptyMap());

        Concord concordArgs = Concord.builder().from(payload.concord())
                .org(Ck8sUtils.orgName(payload.ck8sPath(), payload.flows().clusterAlias()))
                .project(projectName(payload.flows().clusterAlias(), flowConcordArgs))
                .activeProfiles(MapUtils.getList(flowConcordArgs, "activeProfiles", Collections.emptyList()))
                .build();

        String globalExclusiveGroup = MapUtils.getString(flowConcordArgs, "globalExclusiveGroup", "");

        return Ck8sPayload.builder().from(payload)
                .concord(concordArgs)
                .putArgs("hasGlobalLock", !globalExclusiveGroup.trim().isEmpty())
                .putArgs("globalExclusiveGroup", globalExclusiveGroup)
                .build();
    }

    static String projectName(String clusterAlias, Map<String, Object> flowConcordArgs) {
        String projectName = clusterAlias;
        String flowProjectName = MapUtils.getString(flowConcordArgs, "project");
        if (flowProjectName != null) {
            projectName = projectName + "-" + flowProjectName;
        }
        return projectName;
    }
}
