package dev.ybrig.ck8s.cli.common.eval;

import javax.el.ELContext;
import javax.el.ELResolver;
import java.beans.FeatureDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

public class MethodAccessorResolver extends ELResolver {

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base != null && property instanceof String) {
            String k = (String) property;

            try {
                Method m = base.getClass().getDeclaredMethod(k);
                Object value = m.invoke(base);
                context.setPropertyResolved(true);
                return value;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                return null;
            }
        }

        return null;
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
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
    }
}
