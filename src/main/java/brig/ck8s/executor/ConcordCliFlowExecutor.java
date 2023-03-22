package brig.ck8s.executor;

import brig.ck8s.JavaVersionAssert;
import brig.ck8s.actions.InstallConcordCliAction;
import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.utils.CliCommand;
import brig.ck8s.utils.LogUtils;
import brig.ck8s.utils.MapUtils;
import brig.ck8s.utils.Mapper;

import java.nio.file.Path;
import java.util.*;

public class ConcordCliFlowExecutor {

    private final boolean verbose;

    public ConcordCliFlowExecutor(boolean verbose) {
        this.verbose = verbose;
    }

    public int execute(Ck8sPayload payload) {
        if (verbose) {
             LogUtils.info("Using concord cli: {}", getCliVersion(payload.location()));
            LogUtils.info("Using java version: {}", JavaVersionAssert.getJavaVersion(payload.location()));
        }

        List<String> args = new ArrayList<>();
        args.add(InstallConcordCliAction.getCliPath().toString());
        args.add("run");
        args.add(".");
        args.add("-c");
        args.add("--entry-point");
        args.add(payload.entryPoint());
        if (verbose) {
            args.add("--verbose");
        }

        addArg("AWS_PROFILE", MapUtils.getString(clusterRequest(payload), "account"), args);
        payload.args().forEach((k, v) -> addArg(k, v, args));
        addArg("concordUrl", "https://localhost", args);
        addArg("processInfo.sessionToken", "concord-cli-session-token", args);
        addArg("projectInfo.orgName", "Default", args);
        addArg("clusterRequest.localCluster", "true", args);
        addArg("clusterRequest.localConcord", "true", args);

        if (verbose) {
            LogUtils.info("concord-cli working directory: {}", payload.location());
            LogUtils.info("concord-cli cmd: {}", args);
        }

        Map<String, String> env = new HashMap<>();
        env.put("AWS_PROFILE", MapUtils.getString(clusterRequest(payload), "account"));

        try {
            CliCommand.Result result = CliCommand.withRedirectStd(args, payload.location(), env).execute();
            return result.code();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addArg(String key, String value, List<String> args) {
        args.add("-e");
        args.add(key + "=" + value);
    }

    private static String getCliVersion(Path workDir) {
        List<String> args = new ArrayList<>();
        args.add("concord-cli");
        args.add("--version");

        try {
            return CliCommand.grabOut(args, workDir).trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Object> clusterRequest(Ck8sPayload payload) {
        Map<String, Object> yaml = Mapper.yamlMapper().readMap(payload.location().resolve("concord.yml"));
        return MapUtils.getMap(yaml, "configuration.arguments.clusterRequest", Collections.emptyMap());
    }
}
