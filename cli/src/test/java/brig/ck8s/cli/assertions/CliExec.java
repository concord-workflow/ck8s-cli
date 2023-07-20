package brig.ck8s.cli.assertions;

import org.mockito.Mockito;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.spy;

/**
 * This class requires JVM to set
 * --add-opens java.base/java.lang=ALL-UNNAMED
 */
class CliExec
        implements AutoCloseable
{
    private final static Unsafe unsafe;
    private final static Object runtimeFieldBase;
    private final static long runtimeFieldOffset;

    static {
        try {
            Field theUnsafeFiled = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeFiled.setAccessible(true);
            unsafe = (Unsafe) theUnsafeFiled.get(null);
        }
        catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to grab Unsafe from system classes", ex);
        }
        try {
            Field accessibleRuntimeFiled = Runtime.class.getDeclaredField("currentRuntime");
            accessibleRuntimeFiled.setAccessible(true);
            runtimeFieldBase = unsafe.staticFieldBase(accessibleRuntimeFiled);
            runtimeFieldOffset = unsafe.staticFieldOffset(accessibleRuntimeFiled);
        }
        catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to grab runtime field from system classes", ex);
        }
    }

    private PrintStream originalOut;
    private ByteArrayOutputStream out;

    private PrintStream originalErr;
    private ByteArrayOutputStream err;

    private Runtime oryginalRuntime;
    private AtomicReference<Optional<Integer>> exitCode
            = new AtomicReference<>(Optional.empty());

    public CliExec()
    {
        this.originalOut = System.out;
        this.out = new ByteArrayOutputStream();

        this.originalErr = System.err;
        this.err = new ByteArrayOutputStream();

        redirectStdStreams(
                new PrintStream(this.out, true, UTF_8),
                new PrintStream(this.err, true, UTF_8));

        this.oryginalRuntime = Runtime.getRuntime();
        Runtime interceptedRuntime = spy(oryginalRuntime);
        Mockito.doAnswer(
                        invocationOnMock -> {
                            this.exitCode.set(
                                    Optional.of((Integer) invocationOnMock.getArguments()[0]));
                            return null;
                        })
                .when(interceptedRuntime)
                .exit(anyInt());
        overrideRuntime(interceptedRuntime);
    }

    Integer getExitCode()
    {
        return exitCode.get().orElse(-1);
    }

    Optional<String> getOut()
    {
        return streamToString(out);
    }

    Optional<String> getErr()
    {
        return streamToString(err);
    }

    public CliExecAssertion runCommand(Runnable commandRun)
    {
        commandRun.run();
        return new CliExecAssertion(this);
    }

    @Override
    public void close()
    {
        redirectStdStreams(this.originalOut, this.originalErr);
        overrideRuntime(this.oryginalRuntime);
    }

    private Optional<String> streamToString(ByteArrayOutputStream outputStream)
    {
        return Optional.of(outputStream)
                .filter(s -> s.toByteArray().length > 0)
                .map(s -> new String(s.toByteArray(), UTF_8))
                .map(s -> Optional
                        .of(s.indexOf(SecurityException.class.getName()))
                        .filter(indexOf -> indexOf > -1)
                        .map(indexOf -> s.substring(0, indexOf))
                        .orElse(s));
    }

    private void redirectStdStreams(PrintStream out, PrintStream err)
    {
        System.setOut(out);
        System.setErr(err);
    }

    private void overrideRuntime(Runtime runtime)
    {
        unsafe.putObject(runtimeFieldBase, runtimeFieldOffset, runtime);
    }
}
