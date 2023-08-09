package dev.ybrig.ck8s.cli.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CliCommand
{

    private final Path workDir;
    private final List<String> args;
    private final Map<String, String> envars;
    private final StreamReader stdoutReader;
    private final StreamReader stderrReader;

    public CliCommand(List<String> args, Path workDir, Map<String, String> envars, StreamReader stdoutReader, StreamReader stderrReader)
    {
        this.workDir = workDir;
        this.args = args;
        this.envars = envars;
        this.stdoutReader = stdoutReader;
        this.stderrReader = stderrReader;
    }

    public static CliCommand withRedirectStd(List<String> args, Path workDir)
    {
        return withRedirectStd(args, workDir, Collections.emptyMap());
    }

    public static CliCommand withRedirectStd(List<String> args, Path workDir, Map<String, String> env)
    {
        return new CliCommand(args, workDir, env, RedirectStreamReader.toStdout(), RedirectStreamReader.toStderr());
    }

    public static CliCommand saveOut(List<String> args, Path workDir)
    {
        return new CliCommand(args, workDir, Collections.emptyMap(), SaveStreamReader.instance(), SaveStreamReader.instance());
    }

    public static String grabOut(List<String> args, Path workDir)
            throws Exception
    {
        CliCommand cmd = saveOut(args, workDir);
        Result result = cmd.execute();
        if (result.code() != 0) {
            throw new ExecutionException(result.stderr(), null);
        }
        return result.stdout();
    }

    public Result execute()
            throws Exception
    {
        return execute(stdoutReader, stderrReader, Executors.newCachedThreadPool());
    }

    public Result execute(StreamReader stdoutReader, StreamReader stderrReader, ExecutorService executor)
            throws Exception
    {
        ProcessBuilder pb = new ProcessBuilder(args).directory(workDir.toFile());
        pb.environment().putAll(envars);

        Process p = pb.start();
        Future<String> stdout = executor.submit(() -> stdoutReader.read(p.getInputStream()));
        Future<String> stderr = executor.submit(() -> stderrReader.read(p.getErrorStream()));
        int code = p.waitFor();
        executor.shutdown();
        return new Result(code, stdout.get(), stderr.get());
    }

    public interface StreamReader
    {

        String read(InputStream is)
                throws IOException;
    }

    public static class RedirectStreamReader
            implements StreamReader
    {

        private final OutputStream outputStream;

        public RedirectStreamReader(OutputStream outputStream)
        {
            this.outputStream = outputStream;
        }

        public static StreamReader toStdout()
        {
            return new RedirectStreamReader(System.out);
        }

        public static StreamReader toStderr()
        {
            return new RedirectStreamReader(System.err);
        }

        @Override
        public String read(InputStream is)
                throws IOException
        {
            is.transferTo(outputStream);
            return null;
        }
    }

    public static class SaveStreamReader
            implements StreamReader
    {

        private static final StreamReader INSTANCE = new SaveStreamReader();

        public static StreamReader instance()
        {
            return INSTANCE;
        }

        @Override
        public String read(InputStream in)
                throws IOException
        {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
            }
            return sb.toString();
        }
    }

    public static final class Result {
        private final int code;
        private final String stdout;
        private final String stderr;

        public Result(int code, String stdout, String stderr) {
            this.code = code;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int code() {
            return code;
        }

        public String stdout() {
            return stdout;
        }

        public String stderr() {
            return stderr;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Result) obj;
            return this.code == that.code &&
                    Objects.equals(this.stdout, that.stdout) &&
                    Objects.equals(this.stderr, that.stderr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, stdout, stderr);
        }

        @Override
        public String toString() {
            return "Result[" +
                    "code=" + code + ", " +
                    "stdout=" + stdout + ", " +
                    "stderr=" + stderr + ']';
        }

        }
}