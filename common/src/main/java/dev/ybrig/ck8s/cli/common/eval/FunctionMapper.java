package dev.ybrig.ck8s.cli.common.eval;

import java.lang.reflect.Method;
import java.util.Map;

public class FunctionMapper extends javax.el.FunctionMapper {

    private final Map<String, Method> functions;

    public FunctionMapper(Map<String, Method> functions) {
        this.functions = functions;
    }

    @Override
    public Method resolveFunction(String prefix, String localName) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return functions.get(localName);
        }

        return functions.get(prefix + ":" + localName);
    }
}