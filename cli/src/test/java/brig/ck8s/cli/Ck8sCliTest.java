package brig.ck8s.cli;

import org.junit.jupiter.api.Test;

import static brig.ck8s.cli.Ck8sCliAssertions.assertFailed;

public class Ck8sCliTest {
    @Test
    public void testFailOnListAndActionAtSameTime() {
        assertFailed("-l -a dnsmasq-setup")
                .assertExitCode(2)
                .assertErrContainsMatchingLine(
                        "^Error: -a=<actionType>, --list are mutually exclusive \\(specify only one\\)$");
    }

    @Test
    public void testFailOnListAndRunFlowAtSameTime() {
        assertFailed("-l -f cluster")
                .assertExitCode(2)
                .assertErrContainsMatchingLine(
                        "^Error: --flow=<flow>, --list are mutually exclusive \\(specify only one\\)$");
    }

    @Test
    public void testFailOnActionAndRunFlowAtSameTime() {
        assertFailed("-a dnsmasq-setup -f cluster")
                .assertExitCode(2)
                .assertErrContainsMatchingLine(
                        "^Error: --flow=<flow>, -a=<actionType> are mutually exclusive \\(specify only one\\)$");
    }

    @Test
    public void testNoArgs() {
        assertFailed(null)
                .assertOutContainsMatchingLine(
                        "Usage: ck8s-cli.*");
    }
}
