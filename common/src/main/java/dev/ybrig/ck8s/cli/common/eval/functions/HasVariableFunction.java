package dev.ybrig.ck8s.cli.common.eval.functions;

import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.eval.ThreadLocalEvalVariables;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

public final class HasVariableFunction {

    public static Method getMethod() {
        try {
            return HasVariableFunction.class.getMethod("hasVariable", String.class);
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

        String[] path = name.split("\\.");
        if (path.length == 1) {
            return variables.containsKey(name);
        } else {
            Object maybeMap = variables.get(path[0]);
            if (!(maybeMap instanceof Map)) {
                return false;
            }
            return MapUtils.has((Map<String, Object>)maybeMap, Arrays.copyOfRange(path, 1, path.length));
        }
    }
}
