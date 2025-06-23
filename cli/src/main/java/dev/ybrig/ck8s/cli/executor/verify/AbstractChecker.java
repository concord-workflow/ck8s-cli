package dev.ybrig.ck8s.cli.executor.verify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractChecker implements Ck8sPayloadChecker {

    private final List<CheckError> errors = new ArrayList<>();

    @Override
    public List<CheckError> errors() {
        return errors;
    }

    protected void addError(Path concordYaml, String message) {
        errors.add(CheckError.of(concordYaml, message));
    }
}
