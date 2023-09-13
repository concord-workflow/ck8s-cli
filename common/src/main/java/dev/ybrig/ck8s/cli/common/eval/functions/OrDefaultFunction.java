package dev.ybrig.ck8s.cli.common.eval.functions;

import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.eval.ThreadLocalEvalVariables;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

public final class OrDefaultFunction {

    public static Method getMethod() {
        try {
            return OrDefaultFunction.class.getMethod("orDefault", String.class, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T orDefault(String variableName, T defaultValue) {
        boolean has = HasVariableFunction.hasVariable(variableName);
        if (!has) {
            return defaultValue;
        }

        return (T) getValue(variableName);
    }

    @SuppressWarnings("unchecked")
    private static Object getValue(String variableName) {
        Map<String, Object> variables = ThreadLocalEvalVariables.get();

        String[] path = variableName.split("\\.");
        if (path.length == 1) {
            return variables.get(variableName);
        } else {
            Object maybeMap = variables.get(path[0]);
            if (!(maybeMap instanceof Map)) {
                throw new IllegalStateException("Expected a map. This is most likely a bug");
            }
            return MapUtils.get((Map<String, Object>) maybeMap, Arrays.copyOfRange(path, 1, path.length));
        }
    }
}
