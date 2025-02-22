package dev.ybrig.ck8s.cli.op;

import com.walmartlabs.concord.cli.Verbosity;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.sdk.Constants;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.common.verify.CheckError;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.*;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RunFlowOperationV2
        implements CliOperation
{

    private static final Set<String> flowPatternsToConfirm = Set.of("(?i).*delete.*", "(?i).*reinstall.*");
    private static final Set<String> confirmInput = Set.of("y", "yes");
    private static final String[] FILE_IGNORE_PATTERNS = new String[] {".*\\.pdf$", ".*\\.png$", ".*\\.jpg$"};

    public Integer execute(CliOperationContext cliOperationContext)
    {
        var cliApp = cliOperationContext.cliApp();
        var flow = cliApp.getFlow();
        var clientCluster = cliApp.getClusterAlias();

        var needConfirmation = !cliOperationContext.cliApp().isSkipConfirm()
                && flowPatternsToConfirm.stream()
                .anyMatch(flow::matches) ;
        if (needConfirmation) {
            var msg = String.format("Are you sure you want to execute '%s' on '%s' cluster? (y/N): ", flow, clientCluster);
            System.out.print(msg);

            try (var input = new Scanner(System.in)) {
                if (input.hasNextLine()) {
                    var confirm = input.nextLine();
                    if (!confirmInput.contains(confirm)) {
                        return -1;
                    }
                } else {
                    return -1;
                }
            }
        }
        var ck8s = cliOperationContext.ck8sPath();

        var clusterRequest = Ck8sUtils.buildClusterRequest(ck8s, clientCluster);
        var orgName = MapUtils.assertString(clusterRequest, "organization.name");
        var projectName = cliOperationContext.cliApp().getProject();
        if (projectName == null) {
            projectName = String.format("%-3s", MapUtils.assertString(clusterRequest, "clusterGroup.alias")).replace(' ', '_');
        }

        var args = new HashMap<String, Object>(cliApp.getExtraVars());
        args.put(Ck8sConstants.Arguments.FLOW, flow);
        args.put(Ck8sConstants.Arguments.CLIENT_CLUSTER, clientCluster);

        var debug = new Verbosity(cliApp.getVerbosity()).verbose();
        var activeProfiles = cliApp.getActiveProfiles();

        var request = new HashMap<String, Object>();
        request.put(Constants.Multipart.REPO_NAME, "ck8s");

        var ck8sRef = cliOperationContext.cliApp().getCk8sRef();
        if (ck8sRef != null) {
            request.put(Ck8sConstants.Request.CK8S_REF, ck8sRef);
        }

        TemporaryPath archive = null;
        if (ck8sRef == null) {
            archive = archive(ck8s, cliApp.isWithTests());
            request.put("archive", archive);
        }

        var profile = CliConfigurationProvider.getConcordProfile(cliApp.getProfile());
        var executor = new RemoteFlowExecutorV2(profile.baseUrl(), profile.apiKey(), cliApp.getConnectTimeout(), cliApp.getReadTimeout(), cliApp.isDryRunMode());

        ConcordProcess process;
        try {
            process = executor.execute(request, orgName, projectName, args, debug, activeProfiles);
        } finally {
            if (archive != null) {
                archive.close();
            }
        }

//        Ck8sPayloadVerifier verifier = new Ck8sPayloadVerifier();
//
//        Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8s, cliApp.getTargetRootPath(), verifier, cliOperationContext.cliApp().getClusterAlias())
//                .includeTests(cliApp.isWithTests())
//                .build();
//
//        assertNoErrors(ck8s, verifier.errors());

        if (process == null) {
            return -1;
        }

        if (cliApp.isStreamLogs()) {
            ExecutorService executorService = Executors.newCachedThreadPool();
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

    private void assertNoErrors(Ck8sPath ck8sPath, List<CheckError> errors) {
        boolean hasErrors = false;
        for (CheckError error : errors) {
            LogUtils.error("processing '" + ck8sPath.relativize(error.concordYaml()) + ": " + error.message());
            hasErrors = hasErrors || !errors.isEmpty();
        }
        if (hasErrors) {
            throw new RuntimeException("Payload has errors");
        }
    }

    private static TemporaryPath tempFile(String prefix, String suffix) {
        try {
            return IOUtils.tempFile(prefix, suffix);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TemporaryPath archive(Ck8sPath ck8s, boolean withTests) {
        var tmp = tempFile("payload", ".zip");
        try (var zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp.path()))) {
            IOUtils.zipFile(zip, ck8s.ck8sDir().resolve("concord.yaml"), "concord.yaml");
            IOUtils.zip(zip, "ck8s-components/", ck8s.ck8sComponents(), FILE_IGNORE_PATTERNS);

            if (withTests) {
                IOUtils.zip(zip, "ck8s-components-tests/", ck8s.ck8sComponentsTests(), FILE_IGNORE_PATTERNS);
            }

            IOUtils.zip(zip, "ck8s-orgs/", ck8s.ck8sOrgDir(), FILE_IGNORE_PATTERNS);
            IOUtils.zip(zip, "ck8s-configs/", ck8s.ck8sConfigs(), FILE_IGNORE_PATTERNS);

            return tmp;
        } catch (IOException e) {
            throw new RuntimeException("Error creating process archive: " + e.getMessage(), e);
        }
    }
}
