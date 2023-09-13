package dev.ybrig.ck8s.cli.common.eval.functions;

import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.eval.ThreadLocalEvalVariables;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

public final class HasNonNullVariableFunction {

    public static Method getMethod() {
        try {
            return HasNonNullVariableFunction.class.getMethod("hasVariable", String.class);
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean hasVariable(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        Map<String, Object> variables = ThreadLocalEvalVariables.get();

        Object value = null;
        String[] path = name.split("\\.");
        if (path.length == 1) {
            value = variables.get(name);
        } else {
            Object maybeMap = variables.get(path[0]);
            if (!(maybeMap instanceof Map)) {
                return false;
            }
            String[] p = Arrays.copyOfRange(path, 1, path.length);
            if (MapUtils.has((Map<String, Object>)maybeMap, p)) {
                value = MapUtils.get((Map<String, Object>)maybeMap, p);
            }
        }
        return value != null;
    }
}
