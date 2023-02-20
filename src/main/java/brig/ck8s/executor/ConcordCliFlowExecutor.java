package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;
import brig.ck8s.utils.CliCommand;
import brig.ck8s.utils.LogUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConcordCliFlowExecutor {

    private final boolean verbose;

    public ConcordCliFlowExecutor(boolean verbose) {
        this.verbose = verbose;
    }

    public int execute(Ck8sPayload payload) {
        if (verbose) {
            LogUtils.info("using concord cli");
            dumpCliVersion(payload.location());
            dumpJavaVersion(payload.location());
        }

        List<String> args = new ArrayList<>();
        args.add("concord-cli");
        args.add("run");
        args.add(".");
        args.add("-c");
        args.add("--entry-point");
        args.add(payload.entryPoint());
        if (verbose) {
            args.add("--verbose");
        }
        payload.args().forEach((k, v) -> addArg(k, v, args));
        addArg("concordUrl", "https://localhost", args);
        addArg("clusterRequest.localCluster", "true", args);
        addArg("clusterRequest.localConcord", "true", args);

        if (verbose) {
            LogUtils.info("concord-cli working directory: {}", payload.location());
            LogUtils.info("concord-cli cmd: {}", args);
        }

        try {
            CliCommand.Result result = new CliCommand(args, payload.location(), Collections.emptyMap()).execute();
            if (result.getCode() != 0) {
                LogUtils.error(result.getStderr());
            }

            return result.getCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addArg(String key, String value, List<String> args) {
        args.add("-e");
        args.add(key + "=" + value);
    }

    private static void dumpCliVersion(Path workDir) {
        List<String> args = new ArrayList<>();
        args.add("concord-cli");
        args.add("--version");

        try {
            CliCommand.Result result = new CliCommand(args, workDir, Collections.emptyMap()).execute();
            if (result.getCode() != 0) {
                LogUtils.error(result.getStderr());
            }
        } catch (Exception e) {
            LogUtils.error("Error getting cli version: " + e.getMessage());
        }
    }

    private static void dumpJavaVersion(Path workDir) {
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("--version");

        try {
            CliCommand.Result result = new CliCommand(args, workDir, Collections.emptyMap()).execute();
            if (result.getCode() != 0) {
                LogUtils.error(result.getStderr());
            }
        } catch (Exception e) {
            LogUtils.error("Error getting java version: " + e.getMessage());
        }
    }
}
