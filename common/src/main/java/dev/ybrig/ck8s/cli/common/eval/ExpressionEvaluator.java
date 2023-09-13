package dev.ybrig.ck8s.cli.common.eval;

import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import dev.ybrig.ck8s.cli.common.eval.functions.HasNonNullVariableFunction;
import dev.ybrig.ck8s.cli.common.eval.functions.HasVariableFunction;
import dev.ybrig.ck8s.cli.common.eval.functions.OrDefaultFunction;
import dev.ybrig.ck8s.cli.common.eval.functions.ThrowFunction;

import javax.el.*;
import java.lang.reflect.Method;
import java.util.*;

public class ExpressionEvaluator {

    private static final ExpressionEvaluator INSTANCE = new ExpressionEvaluator();

    public static ExpressionEvaluator getInstance() {
        return INSTANCE;
    }

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();
    private final FunctionMapper functionMapper = createFunctionMapper();

    @SuppressWarnings("unchecked")
    public Map<String, Object> evalMap(Map<String, Object> variables, Map<String, Object> value) {
        return eval(variables, value, Map.class);
    }

    public <T> T eval(Map<String, Object> variables, Object value, Class<T> expectedType) {
        return evalValue(variables, value, expectedType);
    }

    @SuppressWarnings("unchecked")
    <T> T evalValue(Map<String, Object> variables, Object value, Class<T> expectedType) {
        if (value == null) {
            return null;
        }

        if (value instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) value;
            if (m.isEmpty()) {
                return expectedType.cast(m);
            }

            Map<String, Object> dst = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                dst.put(evalValue(variables, e.getKey(), String.class), evalValue(variables, e.getValue(), Object.class));
            }
            return expectedType.cast(dst);
        } else if (value instanceof List) {
            List<Object> src = (List<Object>) value;
            if (src.isEmpty()) {
                return expectedType.cast(src);
            }

            ArrayList<Object> dst = new ArrayList<>(src.size());
            for (Object vv : src) {
                dst.add(evalValue(variables, vv, Object.class));
            }
            return expectedType.cast(dst);
        } else if (value instanceof Set) {
            Set<Object> src = (Set<Object>) value;
            if (src.isEmpty()) {
                return expectedType.cast(src);
            }

            Set<Object> dst = new LinkedHashSet<>(src.size());
            for (Object vv : src) {
                dst.add(evalValue(variables, vv, Object.class));
            }

            return expectedType.cast(dst);
        } else if (value.getClass().isArray()) {
            Object[] src = (Object[]) value;
            if (src.length == 0) {
                return expectedType.cast(src);
            }

            for (int i = 0; i < src.length; i++) {
                src[i] = evalValue(variables, src[i], Object.class);
            }

            return expectedType.cast(src);
        } else if (value instanceof String) {
            String s = (String) value;
            if (hasExpression(s)) {
                return evalExpr(variables, s, expectedType);
            }
        }

        return expectedType.cast(value);
    }

    private <T> T evalExpr(Map<String, Object> variables, String expr, Class<T> type) {
        ELResolver resolver = createResolver(variables, expressionFactory);

        StandardELContext sc = new StandardELContext(expressionFactory) {
            @Override
            public ELResolver getELResolver() {
                return resolver;
            }

            @Override
            public FunctionMapper getFunctionMapper() {
                return functionMapper;
            }
        };
        sc.putContext(ExpressionFactory.class, expressionFactory);

        ValueExpression x = expressionFactory.createValueExpression(sc, expr, type);
        try {
            Object v = ThreadLocalEvalVariables.withEvalVariables(variables, () -> x.getValue(sc));
            return type.cast(v);
        } catch (PropertyNotFoundException e) {
            String errorMessage;

            String propName = propertyNameFromException(e);
            if (propName != null) {
                errorMessage = String.format("Can't find a variable %s used in '%s'. " +
                        "Check if it is defined in the current scope. Details: %s", propName, expr, e.getMessage());
            } else {
                errorMessage = String.format("Can't find the specified variable in '%s'. " +
                        "Check if it is defined in the current scope. Details: %s", expr, e.getMessage());
            }

            throw new UserDefinedException(errorMessage);
        } catch (ELException e) {
            throw getExceptionList(e).stream()
                    .filter(i -> i instanceof UserDefinedException)
                    .findAny()
                    .map(i -> (RuntimeException)i)
                    .orElse(e);
        } catch (UserDefinedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("while evaluating expression '" + expr + "'", e);
        }
    }

    private static boolean hasExpression(String s) {
        return s.contains("${");
    }

    private ELResolver createResolver(Map<String, Object> variables,
                                      ExpressionFactory expressionFactory) {

        CompositeELResolver r = new CompositeELResolver();
        r.add(new VariableResolver(variables));
        r.add(expressionFactory.getStreamELResolver());
        r.add(new StaticFieldELResolver());
        r.add(new MapELResolver());
        r.add(new MethodAccessorResolver());
        r.add(new ResourceBundleELResolver());
        r.add(new ListELResolver());
        r.add(new ArrayELResolver());
        r.add(new BeanELResolver());
        return r;
    }

    private static FunctionMapper createFunctionMapper() {
        Map<String, Method> functions = new HashMap<>();
        functions.put("hasVariable", HasVariableFunction.getMethod());
        functions.put("hasNonNullVariable", HasNonNullVariableFunction.getMethod());
        functions.put("orDefault", OrDefaultFunction.getMethod());
        functions.put("throw", ThrowFunction.getMethod());
        return new FunctionMapper(functions);
    }

    private static final String PROP_NOT_FOUND_EL_MESSAGE = "ELResolver cannot handle a null base Object with identifier ";

    private static String propertyNameFromException(PropertyNotFoundException e) {
        if (e.getMessage() == null) {
            return null;
        }

        if (e.getMessage().startsWith(PROP_NOT_FOUND_EL_MESSAGE)) {
            return e.getMessage().substring(PROP_NOT_FOUND_EL_MESSAGE.length());
        }

        return null;
    }

    private static List<Throwable> getExceptionList(Throwable e) {
        List<Throwable> list = new ArrayList<>();
        while (e != null && !list.contains(e)) {
            list.add(e);
            e = e.getCause();
        }
        return list;
    }
}
