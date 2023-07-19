package brig.ck8s.cli;

import com.walmartlabs.concord.common.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static brig.ck8s.cli.Ck8sCliAssertions.assertFailed;
import static brig.ck8s.cli.Ck8sCliAssertions.assertRunAction;
import static brig.ck8s.cli.Ck8sCliAssertions.assertSuccess;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.isRegularFile;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ck8sCliTest
{
    @Test
    public void testClusterList()
    {
        assertSuccess("-l")
                .assertOutContainsMatchingLine(
                        "^\s+Alias\s+Name\s+Region\s+Server\s+Path\s+$");
    }

    @Test
    public void testFailOnListAndActionAtSameTime()
    {
        assertFailed("-l -a dnsmasq-setup")
                .assertExitCode(2)
                .assertErrContainsMatchingLine(
                        "^Error: -a=<actionType>, --list are mutually exclusive \\(specify only one\\)$");
    }

    @Test
    public void testFailOnListAndRunFlowAtSameTime()
    {
        assertFailed("-l -f cluster")
                .assertExitCode(2)
                .assertErrContainsMatchingLine(
                        "^Error: --flow=<flow>, --list are mutually exclusive \\(specify only one\\)$");
    }

    @Test
    public void testFailOnActionAndRunFlowAtSameTime()
    {
        assertFailed("-a dnsmasq-setup -f cluster")
                .assertExitCode(2)
                .assertErrContainsMatchingLine(
                        "^Error: --flow=<flow>, -a=<actionType> are mutually exclusive \\(specify only one\\)$");
    }

    @Test
    public void testRunUpAction()
    {
        assertRunAction("up", "ck8sUp");
    }

    @Test
    public void testRunDownAction()
    {
        assertRunAction("down", "ck8sDown");
    }

    @Test
    public void testRunDockerRegistryAction()
    {
        assertRunAction("docker-registry", "ck8sDockerRegistry");
    }

    @Test
    public void testRunDnsMasqSetupAction()
    {
        assertRunAction("dnsmasq-setup", "dnsmasqSetup");
    }

    @Test
    public void testRunDnsMasqRestartAction()
    {
        assertRunAction("dnsmasq-restart", "dnsmasqRestart");
    }

    @Test
    public void testRunInstallConcordAction()
    {
        assertRunAction("install-concord", "installConcord");
    }

    @Test
    public void testRunReinstallConcordAction()
    {
        assertRunAction("reinstall-concord-agent-pool", "reinstallConcordAgentPool");
    }

    @Test
    public void testRunCk8sConsoleAction()
    {
        assertRunAction("console", "ck8sConsole");
    }

    @Test
    public void testRunAwsKubeConfAction()
    {
        assertRunAction("aws-kube-config", "awsKubeconfig");
    }

    @Test
    public void testLocalCluster()
    {
        assertSuccess("-c local -f cluster --dry-run "
                + "--ck8s-root ./src/test/resources/ck8s")
                .assertOutContainsMatchingLine("Executing action: ck8sDown")
                .assertOutContainsMatchingLine("Executing action: ck8sUp")
                .assertOutContainsMatchingLine("Executing action: assertLocalCluster");
    }

    @Test
    public void testRunFlow()
    {
        assertSuccess("-f show -c local -p default --dry-run "
                + "--ck8s-root ./src/test/resources/ck8s")
                .assertOutContainsMatchingLine(
                        "Running flow: show on cluster: local with profile: default");
    }

    @Test
    public void testTokenOperation()
    {
        assertSuccess("--dry-run token -p default")
                .assertOutContainsMatchingLine(
                        "Generating OIDC token for profile: default");
    }

    @Test
    public void testNoArgs()
    {
        assertFailed(null)
                .assertOutContainsMatchingLine(
                        "Usage: ck8s-cli.*");
    }

    @Test
    public void testPackage()
            throws IOException
    {
        String ck8sPath = "./src/test/resources/ck8s";
        String ck8sExtPath = "./src/test/resources/ck8s-ext";
        assertSuccess(
                "--ck8s-root " + ck8sPath + " "
                        + "--ck8s-ext-root " + ck8sExtPath + " "
                        + "package --dest-file ./target/package.zip")
                .assertOutContainsMatchingLine("Create Ck8s package: .*");

        Path packageFile = Path.of("./target/package.zip");
        assertTrue(
                isRegularFile(packageFile),
                "Missing package.zip");

        Path unpackedPackage = Path.of("./target/package");
        IOUtils.deleteRecursively(unpackedPackage);
        IOUtils.unzip(packageFile, unpackedPackage);

        assertTrue(
                isRegularFile(unpackedPackage.resolve("local-concord.yml")),
                "Missing local cluster concord.yml");
        assertTrue(
                isDirectory(unpackedPackage.resolve("concord")),
                "Missing package concord dir");
        assertTrue(
                isDirectory(unpackedPackage.resolve("ck8s-components")),
                "Missing package ck8s-components dir");
        assertTrue(
                isDirectory(unpackedPackage.resolve("ck8s-components-tests")),
                "Missing package ck8s-components dir");

        assertSuccess("-f show -c local -p default --dry-run "
                + "--ck8s-root " + ck8sPath + " "
                + "--ck8s-ext-root " + ck8sExtPath + " "
                + "--package " + packageFile)
                .assertOutContainsMatchingLine(
                        "Running flow: show on cluster: local " +
                                "with profile: default " +
                                "and package: ./target/package.zip");
    }
}
