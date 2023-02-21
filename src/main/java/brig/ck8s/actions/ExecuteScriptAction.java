package brig.ck8s.actions;

import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.CliCommand;
import brig.ck8s.utils.LogUtils;
import com.walmartlabs.concord.common.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

public class ExecuteScriptAction {

    private final Ck8sPath ck8s;

    public ExecuteScriptAction(Ck8sPath ck8s) {
        this.ck8s = ck8s;
    }

    public int perform(String functionName) {
        try (TempFile script = TempFile.create("main");
             TempFile call = TempFile.create("call")) {

            createScript("/scripts/ck8s-bash.sh", script.path);
            createScript("/scripts/call.sh", call.path);

            Map<String, String> env = new HashMap<>();
            env.put("CK8S_COMPONENTS", ck8s.ck8sComponents().toString());

            List<String> scriptArgs = new ArrayList<>();
            scriptArgs.add(script.path.toAbsolutePath().toString());
            scriptArgs.add(functionName);

            return executeScript(call.path, scriptArgs, env);
        } catch (IOException e) {
            throw new RuntimeException("Can't read script for " + functionName + ". This is most likely a bug: " + e.getMessage());
        }
    }

    private static void createScript(String scriptName, Path path) {
        try (InputStream is = ExecuteScriptAction.class.getResourceAsStream(scriptName)) {
            if (is == null) {
                throw new RuntimeException("Can't read script " + scriptName + ". This is most likely a bug.");
            }

            Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);

            makeExecutable(path);
        } catch (IOException e) {
            throw new RuntimeException("Can't read script " + scriptName + ". This is most likely a bug: " + e.getMessage());
        }
    }

    private static int executeScript(Path path, List<String> scriptArgs, Map<String, String> env) {
        List<String> args = new ArrayList<>();
        args.add(path.toAbsolutePath().toString());
        args.addAll(scriptArgs);

        try {
            CliCommand.Result result = new CliCommand(args, path.getParent(), env, in -> new CliCommand.StreamReader(false, in) {
                @Override
                public String call() throws Exception {
                    in.transferTo(System.out);
                    return "";
                }
            }).execute();
            if (result.getCode() != 0) {
                LogUtils.error(result.getStderr());
            }

            return result.getCode();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static void makeExecutable(Path path) throws IOException {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(path, permissions);
    }

    static class TempFile implements AutoCloseable {

        static TempFile create(String prefix) throws IOException {
            return new TempFile(Files.createTempFile("ck8s-cli-script", null));
        }

        private final Path path;

        TempFile(Path path) {
            this.path = path;
        }

        @Override
        public void close() {
            if (path == null) {
                return;
            }

            try {
                if (Files.isDirectory(path)) {
                    IOUtils.deleteRecursively(path);
                } else {
                    Files.deleteIfExists(path);
                }
            } catch (IOException e) {
                LogUtils.warn("cleanup ['{}'] -> error: {}", path, e.getMessage());
            }
        }
    }
}
