package dev.ybrig.ck8s.cli.common.verify;

import dev.ybrig.ck8s.cli.common.Ck8sFlowBuilderListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Ck8sPayloadVerifier implements Ck8sFlowBuilderListener {

    private final List<Ck8sPayloadChecker> checkers = Arrays.asList(
//            new DuplicateConcordArgsCheck(), // we should ignore args with the same values...
            new DuplicateConcordYamlCheck(),
            new DuplicateConcordFlowCheck(),
            new UndefinedFlowCallCheck()
    );

    @Override
    public void beforeConcordYamlAdd(Path src, Path dest) {
        for (Ck8sPayloadChecker checker : checkers) {
            checker.process(src);
        }
    }

    public List<CheckError> errors() {
        List<CheckError> results = new ArrayList<>();
        for (Ck8sPayloadChecker checker : checkers) {
            results.addAll(checker.errors());
        }
        return results;
    }
}
