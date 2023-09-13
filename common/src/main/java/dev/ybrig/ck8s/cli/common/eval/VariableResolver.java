package dev.ybrig.ck8s.cli.common.eval;

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.el.ELContext;
import javax.el.ELResolver;
import java.beans.FeatureDescriptor;
import java.util.Iterator;
import java.util.Map;

public class VariableResolver extends ELResolver {

    private final Variables variables;

    public VariableResolver(Variables variables) {
        this.variables = variables;
    }

    public VariableResolver(Map<String, Object> variables) {
        this.variables = new MapBackedVariables(variables);
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return Object.class;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return Object.class;
    }

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base == null && property instanceof String) {
            String k = (String) property;

            if (variables.has(k)) {
                context.setPropertyResolved(true);
                return variables.get(k);
            }
        }

        return null;
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
    }
}
