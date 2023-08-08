package dev.ybrig.ck8s.cli.common;

import java.nio.file.Path;

public interface Ck8sFlowBuilderListener {

    void beforeConcordYamlAdd(Path src, Path dest);
}
