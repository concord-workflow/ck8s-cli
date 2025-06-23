package dev.ybrig.ck8s.cli.common.verify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Ck8sPayloadVerifier  {

    private final List<Ck8sPayloadChecker> checkers = Arrays.asList(
//            new DuplicateConcordArgsCheck(), // we should ignore args with the same values...
            new DuplicateConcordYamlCheck(),
            new DuplicateConcordFlowCheck(),
            new UndefinedFlowCallCheck()
    );

    public void beforeConcordYamlAdd(Path src, Path dest) {
        for (var checker : checkers) {
            checker.process(src);
        }
    }

    public List<CheckError> errors() {
        List<CheckError> results = new ArrayList<>();
        for (var checker : checkers) {
            results.addAll(checker.errors());
        }
        return results;
    }
}
