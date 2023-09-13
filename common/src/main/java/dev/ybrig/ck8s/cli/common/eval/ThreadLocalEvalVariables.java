package dev.ybrig.ck8s.cli.common.eval;

import java.util.Map;
import java.util.concurrent.Callable;

public class ThreadLocalEvalVariables {

    public static <T> T withEvalVariables(Map<String, Object> variables, Callable<T> callable) throws RuntimeException {
        set(variables);
        try {
            return callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            clear();
        }
    }

    private static final ThreadLocal<Map<String, Object>> value = new ThreadLocal<>();

    public static Map<String, Object> get() {
        return value.get();
    }

    private static void set(Map<String, Object> ctx) {
        value.set(ctx);
    }

    private static void clear() {
        value.remove();
    }
}
