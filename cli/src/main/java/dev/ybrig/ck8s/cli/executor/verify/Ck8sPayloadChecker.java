package dev.ybrig.ck8s.cli.executor.verify;

import java.nio.file.Path;
import java.util.List;

public interface Ck8sPayloadChecker {

    void process(Path concordYaml);

    List<CheckError> errors();
}
