package dev.ybrig.ck8s.cli.common.processors;

import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Profile;
import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.Ck8sUtils;
import dev.ybrig.ck8s.cli.common.MapUtils;

import java.util.Collections;
import java.util.Map;

public class ProfileProcessor implements PayloadProcessor
{

    @Override
    public Ck8sPayload process(ProcessorsContext context, Ck8sPayload payload)
    {
        ProcessDefinition pd = Ck8sUtils.assertYaml(payload.flows().flowsPath(), context.flowName());

        Map<String, Object> flowConcordArgs = Collections.singletonMap("project", payload.concord().project());
        Profile profile = pd.profiles().get("remote");
        if (profile != null) {
            flowConcordArgs = profile.configuration().arguments();
        }

        Map<String, Object> clusterConfiguration = Ck8sUtils.clusterConfiguration(payload.ck8sPath(), payload.flows().clusterAlias());
        boolean projectPerCluster = context.projectPerCluster();

        Ck8sPayload.Concord concordArgs = Ck8sPayload.Concord.builder().from(payload.concord())
                .org(orgName(projectPerCluster, payload, context.defaultOrg()))
                .project(projectName(context, projectPerCluster, clusterConfiguration, flowConcordArgs, context.defaultProject()))
                .activeProfiles(MapUtils.getList(flowConcordArgs, "activeProfiles", Collections.emptyList()))
                .build();

        String globalExclusiveGroup = MapUtils.getString(flowConcordArgs, "globalExclusiveGroup", "");

        return Ck8sPayload.builder().from(payload)
                .concord(concordArgs)
                .putArgs("globalExclusiveGroup", globalExclusiveGroup)
                .build();
    }

    private static String orgName(boolean projectPerCluster, Ck8sPayload payload, String defaultOrg) {
        if (projectPerCluster) {
            return Ck8sUtils.orgName(payload.ck8sPath(), payload.flows().clusterAlias());
        }
        return defaultOrg;
    }

    private static String projectName(ProcessorsContext context, boolean projectPerCluster, Map<String, Object> clusterConfiguration, Map<String, Object> flowConcordArgs, String defaultProject) {
        if (projectPerCluster) {
            if (context.clientClusterAlias() != null) {
                return context.clientClusterAlias();
            }

            String projectName = MapUtils.getString(clusterConfiguration, "clusterGroup.alias");
            if (projectName == null) {
                projectName = MapUtils.getString(clusterConfiguration, "alias");
            }
            return projectName;
        }

        return MapUtils.getString(flowConcordArgs, "project", defaultProject);
    }
}
