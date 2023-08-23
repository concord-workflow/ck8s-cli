package dev.ybrig.ck8s.cli.concord.plugin;

import com.walmartlabs.concord.client.v2.ConcordTaskV2;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.plugins.git.GitTask;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.common.processors.DefaultProcessors;
import dev.ybrig.ck8s.cli.common.verify.CheckError;
import dev.ybrig.ck8s.cli.common.verify.Ck8sPayloadVerifier;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named("ck8s")
@SuppressWarnings("unused")
public class Ck8sTask
        implements ReentrantTask
{

    private static final Logger log = LoggerFactory.getLogger(Ck8sTask.class);
    private final Context context;
    private final boolean debug;
    private final FileService fileService;
    private final ConcordTaskV2 concordTask;

    @Inject
    public Ck8sTask(Context context, FileService fileService, ConcordTaskV2 concordTaskV2)
    {
        this.context = context;
        this.debug = context.processConfiguration().debug();
        this.fileService = fileService;
        this.concordTask = concordTaskV2;
    }

    private static void archiveToFile(Path src, Path dest)
    {
        try {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                IOUtils.zip(zip, src);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void prepare(String ck8sRef, String ck8sExtRef) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("ck8sRef", ck8sRef);
        params.put("ck8sExtRef", ck8sExtRef);
        Ck8sTaskParams p = Ck8sTaskParams.of(new MapBackedVariables(params), context.defaultVariables());

        if (Files.notExists(Path.of("ck8s"))) {
            cloneRepo(p.ck8sRepoUrl(), p.ck8sRepoRef(), p.ck8sToken(), "ck8s");
        }

        if (Files.notExists(Path.of("ck8s-ext"))) {
            cloneRepo(p.ck8sExtRepoUrl(), p.ck8sExtRepoRef(), p.ck8sExtToken(), "ck8s-ext");
        }
    }

    @Override
    public TaskResult execute(Variables input)
            throws Exception
    {
        Ck8sTaskParams p = Ck8sTaskParams.of(input, context.defaultVariables());
        if (debug) {
            log.info("Using ck8s '{}' and ck8s-ext '{}'", p.ck8sRepoRef(), p.ck8sExtRepoRef());
        }

        if (Files.notExists(Path.of("ck8s"))) {
            cloneRepo(p.ck8sRepoUrl(), p.ck8sRepoRef(), p.ck8sToken(), "ck8s");
        }

        if (Files.notExists(Path.of("ck8s-ext"))) {
            cloneRepo(p.ck8sExtRepoUrl(), p.ck8sExtRepoRef(), p.ck8sExtToken(), "ck8s-ext");
        }

        Path targetDir = fileService.createTempDirectory("payload");

        Ck8sPath ck8sPath = Ck8sPath.from("ck8s", "ck8s-ext");

        Ck8sPayloadVerifier verifier = new Ck8sPayloadVerifier();
        Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8sPath, targetDir, verifier)
                .includeTests(p.includeTests())
                .debug(debug)
                .build(p.clusterAlias());

        assertNoErrors(ck8sPath, verifier.errors());

        Ck8sPayload payload = Ck8sPayload.builder()
                .ck8sPath(ck8sPath)
                .flows(ck8sFlows)
                .args(p.arguments())
                .concord(Ck8sPayload.Concord.builder().meta(p.meta()).project(p.project()).build())
                .putArgs("flow", p.flow())
                .build();

        payload = new DefaultProcessors().process(payload, p.flow());

        return executeProcess(p.flow(), targetDir, payload, p.suspend());
    }

    @Override
    public TaskResult resume(ResumeEvent resumeEvent)
            throws Exception
    {
        return concordTask.resume(resumeEvent);
    }

    private void cloneRepo(String url, String ref, String token, String dest)
            throws Exception
    {
        Map<String, Object> params = new HashMap<>();
        params.put(GitTask.ACTION_KEY, "clone");
        params.put(GitTask.GIT_URL, url);
        params.put(GitTask.GIT_WORKING_DIR, dest);
        params.put(GitTask.GIT_BASE_BRANCH, ref);
        params.put("shallow", true);
        params.put("useJGit", false);
        params.put("debug", debug);

        Map<String, Object> auth = new HashMap<>();
        params.put("auth", Collections.singletonMap("basic", Collections.singletonMap("token", token)));

        GitTask task = new GitTask((orgName, secret, pwd) -> {
            throw new RuntimeException("unsupported");
        }, context.workingDirectory());

        Map<String, Object> result = task.execute(params, Collections.emptyMap());
        if (!MapUtils.getBoolean(result, GitTask.OK_KEY, false)) {
            throw new RuntimeException("Clone error: " + result);
        }
    }

    private TaskResult executeProcess(String flowName, Path targetDir, Ck8sPayload payload, boolean sync)
            throws Exception
    {
        Path archivePath = targetDir.resolve("payload.zip");
        archiveToFile(payload.flows().location(), archivePath);

        Map<String, Object> input = new HashMap<>();
        input.put("action", "start");
        input.put("payload", archivePath.toString());
        input.put("arguments", payload.args());
        input.put("suspend", sync);
        input.put("sync", sync);
        input.put("debug", debug);
        input.put("org", payload.concord().org());
        input.put("project", payload.concord().project());
        input.put("activeProfiles", payload.concord().activeProfiles());
        input.put("meta", payload.concord().meta());

        if (debug) {
            log.info("Starting new process\nflow: {}, args: {}, concordArgs: {}", flowName, payload.args(), payload.concord());
            log.info("concord task input: {}", input);
        }

        TaskResult result = concordTask.execute(new MapBackedVariables(input));
        if (result instanceof TaskResult.SimpleResult) {
            ((TaskResult.SimpleResult)result).value("flow", flowName);
        }
        return result;
    }

    private void assertNoErrors(Ck8sPath ck8sPath, List<CheckError> errors) {
        boolean hasErrors = false;
        for (CheckError error : errors) {
            log.error("processing '" + ck8sPath.relativize(error.concordYaml()) + ": " + error.message());
            hasErrors = hasErrors || !errors.isEmpty();
        }
        if (hasErrors) {
            throw new RuntimeException("Payload has errors");
        }
    }
}
