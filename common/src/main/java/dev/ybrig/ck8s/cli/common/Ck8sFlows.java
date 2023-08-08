package dev.ybrig.ck8s.cli.common;

import org.immutables.value.Value;

import java.nio.file.Path;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sFlows {

    String clusterAlias();

    Path location();

    default Path rootConcordYaml() {
        return location().resolve("concord.yml");
    }

    default Path flowsPath() {
        return location().resolve("concord");
    }

    static ImmutableCk8sFlows.Builder builder() {
        return ImmutableCk8sFlows.builder();
    }
}
