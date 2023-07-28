package brig.ck8s.cli.subcom.pack;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.VersionProvider;
import brig.ck8s.cli.common.Ck8sFlowBuilder;
import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.common.Ck8sRepos;
import brig.ck8s.cli.common.processors.ConcordProcessors;
import brig.ck8s.cli.op.CliOperationContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

public class Ck8sPackageBuilder
{
    private static final List<String> INPUT_PARAMS_ASSERT_DEPENDENCIES = List.of(
            "mvn://com.walmartlabs.concord.plugins.basic:input-params-assert:1.102.1-SNAPSHOT");

    public static Ck8sPackageBuilder builder(CliOperationContext cliOperationContext)
    {
        return new Ck8sPackageBuilder(cliOperationContext);
    }

    private final CliOperationContext cliOperationContext;

    private Optional<Boolean> withTests = Optional.empty();
    private Optional<String> flowName = Optional.empty();
    private Optional<String[]> clusterAliases = Optional.empty();
    private Optional<Map<String, String>> extraArgs = Optional.empty();

    public Ck8sPackageBuilder(CliOperationContext cliOperationContext)
    {
        this.cliOperationContext = requireNonNull(cliOperationContext);
    }

    public Ck8sPackageBuilder withOverrideWithTests(boolean overrideTests)
    {
        this.withTests = Optional.of(overrideTests);
        return this;
    }

    public Ck8sPackageBuilder withOverrideExtraArgs(Map<String, String> extraArgs)
    {
        this.extraArgs = Optional.of(extraArgs);
        return this;
    }

    public Ck8sPackageBuilder withOverrideFlowName(String flowName)
    {
        this.flowName = Optional.of(flowName);
        return this;
    }

    public Ck8sPackageBuilder withOverrideClusterAliases(String... clusterAliases)
    {
        this.clusterAliases = Optional.of(clusterAliases);
        return this;
    }

    public Ck8sPayload build()
    {
        CliApp cliApp = cliOperationContext.cliApp();
        Ck8sRepos ck8sPath = cliOperationContext.ck8sPath();

        String[] clusterAliases = this.clusterAliases
                .orElse(new String[] {cliApp.getClusterAlias()});

        String flowName = this.flowName.orElse(cliApp.getFlow());
        Map<String, String> extraArgs = this.extraArgs.orElse(cliApp.getExtraVars());
        Boolean withTests = this.withTests.orElse(cliApp.isWithTests());

        List<String> dependencies = new ArrayList<>();
        if (cliApp.isWithInputAssert()) {
            dependencies.addAll(INPUT_PARAMS_ASSERT_DEPENDENCIES);
        }

        Ck8sFlowBuilder ck8sFlowBuilder = Ck8sFlowBuilder
                .builder(ck8sPath, cliApp.getTargetRootPath())
                .debug(cliOperationContext.verbosity().verbose())
                .withDependencies(dependencies)
                .includeTests(withTests)
                .withClusterAlias(clusterAliases);

        Path ck8sPackagePath = cliApp.getCk8sPackagePath();
        if (nonNull(ck8sPackagePath)) {
            System.out.println("Running using ck8s package: %s".formatted(ck8sPackagePath));
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
}
