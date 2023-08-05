package brig.ck8s.cli.common.verify;

import java.nio.file.Path;
import java.util.List;

public interface Ck8sPayloadChecker {

    void process(Path concordYaml);

    List<CheckError> errors();
}
