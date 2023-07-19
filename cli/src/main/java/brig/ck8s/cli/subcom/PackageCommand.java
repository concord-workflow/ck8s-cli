package brig.ck8s.cli.subcom;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.VersionProvider;
import brig.ck8s.cli.common.Ck8sFlowBuilder;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.common.processors.ConcordProcessors;
import brig.ck8s.cli.model.ClusterInfo;
import brig.ck8s.cli.op.CliOperationContext;
import picocli.CommandLine;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static brig.ck8s.cli.op.ClusterListOperation.getClusterList;
import static java.util.Objects.nonNull;

@CommandLine.Command(name = "package",
        description = {"Generate Concord access token and save it to the configuration"}
)
public class PackageCommand
        implements Callable<Integer>
{

    private static final List<String> INPUT_PARAMS_ASSERT_DEPENDENCIES = List.of(
            "mvn://com.walmartlabs.concord.plugins.basic:input-params-assert:1.102.1-SNAPSHOT");

    @CommandLine.ParentCommand
    private CliApp cliApp;

    @CommandLine.Option(
            names = {"--dest-file"},
            description = "concord instance profile name")
    String packageFileTarget;

    public static Ck8sPayload createCk8sPayload(CliOperationContext cliOperationContext, String flowName, Map<String, String> extraArgs, boolean withTests, String... clusterAliases)
    {
        CliApp cliApp = cliOperationContext.cliApp();

        List<String> deps = new ArrayList<>();
        if (cliApp.isWithInputAssert()) {
            deps.addAll(INPUT_PARAMS_ASSERT_DEPENDENCIES);
        }

        Ck8sFlowBuilder ck8sFlowBuilder = Ck8sFlowBuilder
                .builder(cliOperationContext.ck8sPath(), cliApp.getTargetRootPath())
                .includeTests(withTests)
                .withDependencies(deps)
                .debug(cliOperationContext.verbosity().verbose())
                .withClusterAlias(clusterAliases);

        Path ck8sPackagePath = cliApp.getCk8sPackagePath();
        if (nonNull(ck8sPackagePath)) {
            ck8sFlowBuilder.withCk8sPackage(ck8sPackagePath);
        }

        Path packagePath = ck8sFlowBuilder.build();
        Ck8sPayload ck8sPayload = Ck8sPayload.builder()
                .clusterAliases(clusterAliases)
                .location(packagePath)
                .putArgs("ck8sCliVersion", VersionProvider.getCliVersion())
                .putAllArgs(extraArgs)
                .flow(flowName)
                .build();

        return new ConcordProcessors()
                .process(ck8sPayload);
    }

    @Override
    public Integer call()
    {
        CliOperationContext cliOperationContext = new CliOperationContext(cliApp);
        String[] clusterAliases = getClusterList(cliOperationContext.ck8sPath())
                .values()
                .stream()
                .map(ClusterInfo::alias)
                .toArray(String[]::new);
        Ck8sPayload ck8sPayload = createCk8sPayload(
                cliOperationContext,
                null,
                cliApp.getExtraVars(),
                // We always add tests when we build package
                true,
                clusterAliases);

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
