package dev.ybrig.ck8s.cli.executor.verify;

import org.immutables.value.Value;

import java.nio.file.Path;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface CheckError {

    static CheckError of(Path srcConcordYaml, String message) {
        return ImmutableCheckError.of(srcConcordYaml, message);
    }

    @Value.Parameter
    Path concordYaml();

    @Value.Parameter
    String message();
}
