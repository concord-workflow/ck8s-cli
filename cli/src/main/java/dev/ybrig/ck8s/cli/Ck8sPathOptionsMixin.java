package dev.ybrig.ck8s.cli;

import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command
@SuppressWarnings("unused")
public class Ck8sPathOptionsMixin
        extends BaseMixin<Ck8sPathOptionsMixin> {
    private Path ck8sPath = Path.of(System.getProperty("user.dir"));

    private Path ck8sExtPath = null;

    public Path getCk8sPath() {
        return rootMixin().ck8sPath;
    }

    @CommandLine.Option(names = {"--ck8s-root"}, description = "path to ck8s dir")
    public void setCk8sPath(Path ck8sPath) {
        rootMixin().ck8sPath = ck8sPath;
    }

    public Path getCk8sExtPath() {
        return rootMixin().ck8sExtPath;
    }

    @CommandLine.Option(names = {"--ck8s-ext-root"}, description = "path to ck8s-ext dir")
    public void setCk8sExtPath(Path ck8sExtPath) {
        rootMixin().ck8sExtPath = ck8sExtPath;
    }

    @Override
    public String toString() {
        return "Ck8sPathOptionsMixin{" +
                "ck8sPath=" + ck8sPath +
                ", ck8sExtPath=" + ck8sExtPath +
                '}';
    }
}
