package dev.ybrig.ck8s.cli.common.eval;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExpressionEvaluatorTest {

    @Test
    public void testEva() {
        Map<String, Object> vars = Collections.singletonMap("name", "${Concord}");

        // ---
        String str = ExpressionEvaluator.getInstance().eval(vars, "Hello ${name}", String.class);
        assertEquals("Hello ${Concord}", str);
    }

    @Test
    public void testEvalHasVariable() {
        String str = "${hasVariable('x')}";

        // ---

        boolean result = ExpressionEvaluator.getInstance().eval(Collections.emptyMap(), str, Boolean.class);
        assertFalse(result);

        // ---

        Map<String, Object> vars = Collections.singletonMap("x", "x-value");
        result = ExpressionEvaluator.getInstance().eval(vars, str, Boolean.class);
        assertTrue(result);
    }
}
