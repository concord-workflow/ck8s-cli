package dev.ybrig.ck8s.cli.common;

import org.immutables.value.Value;

import java.nio.file.Path;
import java.util.stream.Stream;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sFlows {

    Path location();

    default Path flowsPath() {
        return location().resolve("concord");
    }

    default Stream<Path> concordYamls() {
        return Ck8sUtils.streamConcordYaml(flowsPath());
    }

    static ImmutableCk8sFlows.Builder builder() {
        return ImmutableCk8sFlows.builder();
    }
}
