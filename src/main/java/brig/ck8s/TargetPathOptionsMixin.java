package brig.ck8s;

import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command
@SuppressWarnings("unused")
public class TargetPathOptionsMixin extends BaseMixin<TargetPathOptionsMixin> {

    private Path targetRootPath = Path.of(System.getProperty("user.dir")).resolve("target");

    @CommandLine.Option(names = {"--target-root"}, description = "path to target dir")
    public void setTargetRootPath(Path targetRootPath) {
        rootMixin().targetRootPath = targetRootPath;
    }

    public Path getTargetRootPath() {
        return rootMixin().targetRootPath;
    }
}
