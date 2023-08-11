package dev.ybrig.ck8s.cli.common.processors;

import org.immutables.value.Value;

import java.util.Collections;
import java.util.List;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface ProcessorsContext {

    String flowName();

    @Value.Default
    default List<String> defaultDependencies() {
        return Collections.emptyList();
    }

    static ImmutableProcessorsContext.Builder builder() {
        return ImmutableProcessorsContext.builder();
    }
}
