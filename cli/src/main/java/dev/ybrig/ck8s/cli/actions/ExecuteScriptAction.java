package dev.ybrig.ck8s.cli.actions;

import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.common.CliCommand;
import dev.ybrig.ck8s.cli.op.CliOperationContext;
import dev.ybrig.ck8s.cli.utils.TempPath;

import java.io.IOException;
import java.io.InputStream;
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

    public int perform(CliOperationContext cliOperationContext, String functionName) {
        return perform(cliOperationContext, functionName, Collections.emptyMap());
    }

    public int perform(CliOperationContext cliOperationContext, String functionName, Map<String, String> extraEnv) {
        try (var script = TempPath.createFile("main");
             var call = TempPath.createFile("call")) {

            createScript("/scripts/ck8s-bash.sh", script.path());
            createScript("/scripts/call.sh", call.path());

            Map<String, String> env = new HashMap<>();
            env.put("CK8S_COMPONENTS", ck8s.ck8sComponents().toString());
            env.putAll(extraEnv);

            List<String> scriptArgs = new ArrayList<>();
            scriptArgs.add(script.path().toAbsolutePath().toString());
            scriptArgs.add(functionName);

            return executeScript(call.path(), scriptArgs, env);
        } catch (IOException e) {
            throw new RuntimeException("Can't read script for " + functionName + ". This is most likely a bug: " + e.getMessage());
        }
    }

    private static void createScript(String scriptName, Path path) {
        try (var is = ExecuteScriptAction.class.getResourceAsStream(scriptName)) {
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
            var result = CliCommand.withRedirectStd(args, path.getParent(), env).execute();
            return result.code();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static void makeExecutable(Path path)
            throws IOException {
        var permissions = Files.getPosixFilePermissions(path);
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(path, permissions);
    }
}
