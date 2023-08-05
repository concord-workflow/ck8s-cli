package brig.ck8s.cli.common.verify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractChecker implements Ck8sPayloadChecker{

    private final List<CheckError> errors = new ArrayList<>();

    protected void addError(Path concordYaml, String message) {
        errors.add(CheckError.of(concordYaml, message));
    }

    @Override
    public List<CheckError> errors() {
        return errors;
    }
}
