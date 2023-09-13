package dev.ybrig.ck8s.cli.common.eval.functions;

import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;

import java.lang.reflect.Method;

public final class ThrowFunction {

    public static Method getMethod() {
        try {
            return ThrowFunction.class.getMethod("throwError", String.class);
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    public static Object throwError(String message) {
        throw new UserDefinedException(message);
    }
}
