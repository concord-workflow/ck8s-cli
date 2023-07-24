package brig.ck8s.cli.common;

import com.walmartlabs.concord.common.ConfigurationUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MapUtils
{

    private MapUtils()
    {
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> getList(Map<String, Object> m, String path)
    {
        return get(m, path, Collections.emptyList(), List.class);
    }

    public static <T> List<T> getList(Map<String, Object> m, String path, List<T> defaultValue)
    {
        return get(m, path, defaultValue, List.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Map<String, Object> m, String path, Map<String, Object> defaultValue)
    {
        return get(m, path, defaultValue, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> assertMap(Map<String, Object> m, String path)
    {
        return assertValue(m, path, Map.class);
    }

    public static String assertString(Map<String, Object> m, String path)
    {
        return assertValue(m, path, String.class);
    }

    public static String getString(Map<String, Object> m, String path)
    {
        return getString(m, path, null);
    }

    public static boolean getBoolean(Map<String, Object> m, String path, boolean defaultValue) {
        return get(m, path, defaultValue, Boolean.class);
    }

    public static String getString(Map<String, Object> m, String path, String defaultValue)
    {
        return get(m, path, defaultValue, String.class);
    }

    public static <T> T get(Map<String, Object> m, String path, T defaultValue, Class<T> type)
    {
        Object value = ConfigurationUtils.get(m, path.split("\\."));
        if (value == null) {
            return defaultValue;
        }
        else if (type.isInstance(value)) {
            return type.cast(value);
        }
        else {
            throw new IllegalArgumentException("Invalid value type at '" + path + "', expected: " + type + ", got: " + value.getClass());
        }
    }

    public static <T> T assertValue(Map<String, Object> m, String path, Class<T> type)
    {
        T result = get(m, path, null, type);
        if (result != null) {
            return result;
        }
        else {
            throw new IllegalArgumentException("Mandatory value at '" + path + "' is required");
        }
    }

    public static Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b)
    {
        return ConfigurationUtils.deepMerge(a, b);
    }

    public static void set(Map<String, Object> m, Object b, String path)
    {
        ConfigurationUtils.set(m, b, path.split("\\."));
    }

    public static void delete(Map<String, Object> m, String path)
    {
        ConfigurationUtils.delete(m, path.split("\\."));
    }
}
