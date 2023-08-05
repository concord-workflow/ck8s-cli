package brig.ck8s.cli.common.verify;

import org.immutables.value.Value;

import java.nio.file.Path;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface CheckError {

    @Value.Parameter
    Path concordYaml();

    @Value.Parameter
    String message();

    static CheckError of(Path srcConcordYaml, String message) {
        return ImmutableCheckError.of(srcConcordYaml, message);
    }
}
