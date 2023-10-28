package dev.ybrig.ck8s.cli.common;

import org.immutables.value.Value;

import java.util.Collections;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sPayload {

    @Value.Default
    default boolean debug() {
        return false;
    }

    @Value.Default
    default Map<String, Object> arguments() {
        return Collections.emptyMap();
    }

    Ck8sFlows ck8sFlows();

    static ImmutableCk8sPayload.Builder builder() {
        return ImmutableCk8sPayload.builder();
    }
}
