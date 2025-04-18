package dev.ybrig.ck8s.cli.op;

import com.walmartlabs.concord.cli.Verbosity;
import com.walmartlabs.concord.sdk.Constants;
import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.Ck8sConstants;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.common.Ck8sUtils;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.RemoteFlowExecutorV2;
import dev.ybrig.ck8s.cli.utils.Ck8sPayloadArchiver;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static dev.ybrig.ck8s.cli.op.RunFlowOperationUtils.needsConfirmation;

public class RemoteRunFlowOperation implements CliOperation {

    public Integer execute(CliOperationContext cliOperationContext) {
        var cliApp = cliOperationContext.cliApp();
        var clientCluster = cliApp.getClusterAlias();

        if (needsConfirmation(cliApp, cliApp.getFlow(), clientCluster)) {
            return -1;
        }

        var ck8s = cliOperationContext.ck8sPath();
        var request = prepareRequest(cliApp, ck8s);

        var profile = CliConfigurationProvider.getConcordProfile(cliApp.getProfile());
        var executor = new RemoteFlowExecutorV2(profile.baseUrl(), profile.apiKey(), cliApp.getConnectTimeout(), cliApp.getReadTimeout());

        ConcordProcess process;
        try (var archive = prepareArchiveIfRequired(cliApp, ck8s, request)) {
            process = executor.execute(request);
        }

        if (cliApp.isStreamLogs()) {
            var executorService = Executors.newCachedThreadPool();
            try {
                process.streamLogs(executorService);
            } finally {
                executorService.shutdownNow();
            }
        }

        if (cliApp.getWaitSeconds() != null && cliApp.getWaitSeconds() > 0) {
            process.waitEnded(cliApp.getWaitSeconds() * 1000);
        }

        return 0;
    }

    private static String projectName(CliApp cliApp, Map<String, Object> clusterRequest) {
        var result = cliApp.getProject();
        if (result == null) {
            result = String.format("%-3s", MapUtils.assertString(clusterRequest, "clusterGroup.alias")).replace(' ', '_');
        }
        return result;
    }

    private static Map<String, Object> prepareArgs(CliApp cliApp) {
        var args = new HashMap<String, Object>(cliApp.getExtraVars());
        args.put(Ck8sConstants.Arguments.FLOW, cliApp.getFlow());
        args.put(Ck8sConstants.Arguments.CLIENT_CLUSTER, cliApp.getClusterAlias());
        return args;
    }

    private static Map<String, Object> prepareRequest(CliApp cliApp, Ck8sPath ck8s) {
        var request = new HashMap<String, Object>();

        var clusterRequest = Ck8sUtils.buildClusterRequest(ck8s, cliApp.getClusterAlias());

        // org
        var orgName = MapUtils.assertString(clusterRequest, "organization.name");
        request.put(Constants.Multipart.ORG_NAME, orgName);

        //project
        var projectName = projectName(cliApp, clusterRequest);
        request.put(Constants.Multipart.PROJECT_NAME, projectName);

        // branch
        var ck8sRef = cliApp.getCk8sRef();
        if (ck8sRef != null) {
            // repo
            request.put(Constants.Multipart.REPO_NAME, Ck8sConstants.DEFAULT_REPO_NAME);

            request.put(Constants.Request.REPO_BRANCH_OR_TAG, cliApp.getCk8sRef());
        }

        // debug
        var debug = new Verbosity(cliApp.getVerbosity()).verbose();
        request.put(Constants.Request.DEBUG_KEY, debug);

        var requestParams = new HashMap<String, Object>();

        // active profiles
        var activeProfiles = cliApp.getActiveProfiles();
        if (activeProfiles != null && !activeProfiles.isEmpty()) {
            requestParams.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);
        }

        // args
        var args = prepareArgs(cliApp);
        requestParams.put(Constants.Request.ARGUMENTS_KEY, args);

        // dry-run mode
        requestParams.put(Constants.Request.DRY_RUN_MODE_KEY, cliApp.isDryRunMode());

        if (cliApp.getMeta() != null) {
            requestParams.put(Constants.Request.META, cliApp.getMeta());
        }

        request.put("request", requestParams);

        return request;
    }

    private static Ck8sPayloadArchiver.Archive prepareArchiveIfRequired(CliApp cliApp, Ck8sPath ck8s, Map<String, Object> request) {
        if (cliApp.getCk8sRef() != null) {
            return null;
        }

        var archive = Ck8sPayloadArchiver.archive(ck8s, cliApp.getClusterAlias());
        request.put("archive", archive.path());
        return archive;
    }
}
