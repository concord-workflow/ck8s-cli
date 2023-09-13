package dev.ybrig.ck8s.cli.common;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        Object value = get(m, path.split("\\."));
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
        return deepMerge(a, b);
    }

    public static void set(Map<String, Object> m, Object b, String path)
    {
        set(m, b, path.split("\\."));
    }

    public static void delete(Map<String, Object> m, String path)
    {
        delete(m, path.split("\\."));
    }

    public static Object get(Map<String, Object> m, String[] path) {
        int depth = path != null ? path.length : 0;
        return get(m, depth, path);
    }

    @SuppressWarnings("unchecked")
    private static Object get(Map<String, Object> m, int depth, String[] path) {
        if (m == null) {
            return null;
        }

        if (depth == 0) {
            return m;
        }

        for (int i = 0; i < depth - 1; i++) {
            Object v = m.get(path[i]);
            if (v == null) {
                return null;
            }

            if (!(v instanceof Map)) {
                throw new IllegalArgumentException("Invalid data type, expected JSON object, got: " + v.getClass());
            }

            m = (Map<String, Object>) v;
        }

        return m.get(path[depth - 1]);
    }

    @SuppressWarnings("unchecked")
    public static void set(Map<String, Object> a, Object b, String[] path) {
        Object holder = get(a, path.length - 1, path);

        if (holder != null && !(holder instanceof Map)) {
            throw new IllegalArgumentException("Value should be contained in a JSON object: " + String.join("/", path));
        }

        Map<String, Object> m = (Map<String, Object>) holder;

        // TODO automatically create the value holder?
        assert m != null;
        m.put(path[path.length - 1], b);
    }

    @SuppressWarnings("unchecked")
    private static void delete(Map<String, Object> a, String[] path) {
        Object holder = get(a, path.length - 1, path);
        if (holder == null) {
            return;
        }

        if (!(holder instanceof Map)) {
            throw new IllegalArgumentException("Value should be contained in a JSON object: " + String.join("/", path));
        }

        Map<String, Object> m = (Map<String, Object>) holder;
        m.remove(path[path.length - 1]);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepMerge(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> result = new LinkedHashMap<>(a != null ? a : Collections.emptyMap());

        for (String k : b.keySet()) {
            Object av = result.get(k);
            Object bv = b.get(k);

            Object o = bv;
            if (av instanceof Map && bv instanceof Map) {
                o = deepMerge((Map<String, Object>) av, (Map<String, Object>) bv);
            }

            // preserve the order of the keys
            if (result.containsKey(k)) {
                result.replace(k, o);
            } else {
                result.put(k, o);
            }
        }
        return result;
    }

    public static boolean has(Map<String, Object> m, String[] path) {
        if (m == null) {
            return false;
        }

        if (path.length == 0) {
            return false;
        }

        for (int i = 0; i < path.length - 1; i++) {
            Object v = m.get(path[i]);
            if (!(v instanceof Map)) {
                return false;
            }

            m = (Map<String, Object>) v;
        }

        return m.containsKey(path[path.length - 1]);
    }
}
