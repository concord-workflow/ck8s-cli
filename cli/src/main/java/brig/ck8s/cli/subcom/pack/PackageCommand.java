package brig.ck8s.cli.subcom.pack;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.common.Ck8sRepos;
import brig.ck8s.cli.model.ClusterInfo;
import brig.ck8s.cli.op.CliOperationContext;
import picocli.CommandLine;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static brig.ck8s.cli.op.ClusterListOperation.getClusterList;
import static java.util.Objects.nonNull;

@CommandLine.Command(name = "package",
        description = {"Generate Concord access token and save it to the configuration"}
)
public class PackageCommand
        implements Callable<Integer>
{
    @CommandLine.ParentCommand
    private CliApp cliApp;

    @CommandLine.Option(
            names = {"--dest-file"},
            description = "concord instance profile name")
    String packageFileTarget;

    @CommandLine.Option(
            names = {"--ignore-unstable"},
            description = "ignore unstable ck8s and ck8s-ext src repositories state")
    boolean ignoreUnstable = false;

    @Override
    public Integer call()
    {
        CliOperationContext cliOperationContext = new CliOperationContext(cliApp);
        Ck8sRepos ck8sPath = cliOperationContext.ck8sPath();

        if (!ignoreUnstable && !ck8sPath.ck8sDirRepoStable()) {
            throw new RuntimeException(
                    "Can't build package out of non stable ck8s repository dir: %s"
                            .formatted(ck8sPath.ck8sDir()));
        }

        if (!ignoreUnstable
                && ck8sPath.ck8sExtDir().isPresent()
                && !ck8sPath.ck8sExtDirRepoStable()) {
            throw new RuntimeException(
                    "Can't build package out of non stable ck8s-ext repository dir: %s"
                            .formatted(ck8sPath.ck8sExtDir().get()));
        }

        String[] clusterAliases = getClusterList(ck8sPath)
                .values()
                .stream()
                .map(ClusterInfo::alias)
                .toArray(String[]::new);

        Ck8sPayload ck8sPayload = Ck8sPackageBuilder
                .builder(cliOperationContext)
                // We always add tests when we build package
                .withOverrideWithTests(true)
                .withOverrideClusterAliases(clusterAliases)
                .build();

        Path packageTarget;
        if (nonNull(packageFileTarget)) {
            packageTarget = Path.of(packageFileTarget);
        }
        else {
            packageTarget = cliApp.getTargetRootPath().resolve("package.zip");
        }

        Path packageDir = packageTarget.getParent();
        FileOutputStream packageFile;

        try {
            if (!Files.exists(packageDir)) {
                Files.createDirectories(packageDir);
            }
            packageFile = new FileOutputStream(packageTarget.toFile());
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to create package location directory %s.".formatted(packageDir));
        }

        try {
            ck8sPayload.createArchive(packageFile);
            packageFile.flush();
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to create package file: " + packageTarget.toAbsolutePath(), e);
        }
        System.out.println("Create Ck8s package: %s".formatted(packageTarget.toAbsolutePath()));
        return 0;
    }
}
