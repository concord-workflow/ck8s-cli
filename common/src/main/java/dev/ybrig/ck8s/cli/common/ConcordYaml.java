package dev.ybrig.ck8s.cli.common;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface ConcordYaml {

    @Value.Default
    default Map<String, Object> meta() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> arguments() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> requirements() {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> exclusive() {
        return Collections.emptyMap();
    }

    String entryPoint();

    @Value.Default
    default boolean debug() {
        return false;
    }

    default void write(Path location) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("runtime", "concord-v2");
        configuration.put("debug", debug());
        configuration.put("meta", meta());
        configuration.put("arguments", arguments());
        configuration.put("requirements", requirements());
        configuration.put("entryPoint", entryPoint());
        configuration.put("exclusive", exclusive());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configuration", configuration);
        Mapper.yamlMapper().write(location.resolve("concord.yml"), map);
    }

    static ImmutableConcordYaml.Builder builder() {
        return ImmutableConcordYaml.builder();
    }
}