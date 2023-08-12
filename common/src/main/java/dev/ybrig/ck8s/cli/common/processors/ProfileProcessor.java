package dev.ybrig.ck8s.cli.common.processors;

import com.walmartlabs.concord.runtime.v2.model.Profile;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.Ck8sUtils;
import dev.ybrig.ck8s.cli.common.MapUtils;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.lang.reflect.Array;
import java.util.*;

public class ProfileProcessor implements PayloadProcessor
{

    @Override
    public Ck8sPayload process(ProcessorsContext context, Ck8sPayload payload)
    {
        ProcessDefinition pd = Ck8sUtils.assertYaml(payload.flows().flowsPath(), context.flowName());

        Map<String, Object> flowConcordArgs = Collections.emptyMap();
        Profile profile = pd.profiles().get("remote");
        if (profile != null) {
            flowConcordArgs = profile.configuration().arguments();
        }

        Ck8sPayload.Concord concordArgs = Ck8sPayload.Concord.builder().from(payload.concord())
                .org(orgName(payload))
                .project(projectName(payload.flows().clusterAlias(), flowConcordArgs))
                .activeProfiles(MapUtils.getList(flowConcordArgs, "activeProfiles", Collections.emptyList()))
                .build();

        String globalExclusiveGroup = MapUtils.getString(flowConcordArgs, "globalExclusiveGroup", "");

        return Ck8sPayload.builder().from(payload)
                .concord(concordArgs)
                .putArgs("globalExclusiveGroup", globalExclusiveGroup)
                .build();
    }

    private static final Set<String> defaultOrgAlias = new HashSet<>(Arrays.asList("ci", "local"));

    static String orgName(Ck8sPayload payload) {
        if (defaultOrgAlias.contains(payload.flows().clusterAlias())) {
            return "Default";
        }
        return Ck8sUtils.orgName(payload.ck8sPath(), payload.flows().clusterAlias());
    }

    static String projectName(String clusterAlias, Map<String, Object> flowConcordArgs) {
        String projectName = clusterAlias;
        String flowProjectName = MapUtils.getString(flowConcordArgs, "project");
        if (flowProjectName != null) {
            if (defaultOrgAlias.contains(clusterAlias)) {
                projectName = flowProjectName;
            } else {
                projectName = projectName + "-" + flowProjectName;
            }
        }
        return projectName;
    }
}
