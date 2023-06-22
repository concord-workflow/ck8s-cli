package brig.ck8s.actions;

import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.CliCommand;
import brig.ck8s.utils.TempPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExecuteScriptAction
{

    private final Ck8sPath ck8s;

    public ExecuteScriptAction(Ck8sPath ck8s)
    {
        this.ck8s = ck8s;
    }

    private static void createScript(String scriptName, Path path)
    {
        try (InputStream is = ExecuteScriptAction.class.getResourceAsStream(scriptName)) {
            if (is == null) {
                throw new RuntimeException("Can't read script " + scriptName + ". This is most likely a bug.");
            }

            Files.copy(is, path, StandardCopyOption.REPLACE_EXISTING);

            makeExecutable(path);
        }
        catch (IOException e) {
            throw new RuntimeException("Can't read script " + scriptName + ". This is most likely a bug: " + e.getMessage());
        }
    }

    private static int executeScript(Path path, List<String> scriptArgs, Map<String, String> env)
    {
        List<String> args = new ArrayList<>();
        args.add(path.toAbsolutePath().toString());
        args.addAll(scriptArgs);

        try {
            CliCommand.Result result = CliCommand.withRedirectStd(args, path.getParent(), env).execute();
            return result.code();
        }
        catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static void makeExecutable(Path path)
            throws IOException
    {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
        permissions.add(PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(path, permissions);
    }

    public int perform(String functionName)
    {
        return perform(functionName, Collections.emptyMap());
    }

    public int perform(String functionName, Map<String, String> extraEnv)
    {
        try (TempPath script = TempPath.createFile("main");
                TempPath call = TempPath.createFile("call")) {

            createScript("/scripts/ck8s-bash.sh", script.path());
            createScript("/scripts/call.sh", call.path());

            Map<String, String> env = new HashMap<>();
            env.put("CK8S_COMPONENTS", ck8s.ck8sComponents().toString());
            env.putAll(extraEnv);

            List<String> scriptArgs = new ArrayList<>();
            scriptArgs.add(script.path().toAbsolutePath().toString());
            scriptArgs.add(functionName);

            return executeScript(call.path(), scriptArgs, env);
        }
        catch (IOException e) {
            throw new RuntimeException("Can't read script for " + functionName + ". This is most likely a bug: " + e.getMessage());
        }
    }
}
