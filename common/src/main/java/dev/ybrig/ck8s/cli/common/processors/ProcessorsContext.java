package dev.ybrig.ck8s.cli.common.processors;

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface ProcessorsContext {

    @Value.Default
    default String defaultOrg() {
        return "Default";
    }

    @Nullable
    String defaultProject();

    String flowName();

    @Value.Default
    default List<String> defaultDependencies() {
        return Collections.emptyList();
    }

    static ImmutableProcessorsContext.Builder builder() {
        return ImmutableProcessorsContext.builder();
    }
}
