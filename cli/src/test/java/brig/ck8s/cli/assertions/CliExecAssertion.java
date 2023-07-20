package brig.ck8s.cli.assertions;

import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.lineSeparator;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CliExecAssertion
{
    private final CliExec cliExec;

    public CliExecAssertion(CliExec cliExec)
    {
        this.cliExec = cliExec;
    }

    public CliExecAssertion assertExitCode(int expectedExitCode)
    {
        return assertExitCode(
                code -> expectedExitCode == code,
                code -> "Expected exit code: %s but got: %s".formatted(expectedExitCode, code));
    }

    public CliExecAssertion asserErrEmpty()
    {
        Optional<String> err = cliExec.getErr();
        if (err.isPresent()) {
            Assertions.fail("CLI wrote to Err stream, but should not: \n" + err.get());
        }
        return this;
    }

    public CliExecAssertion assertExitCode(Predicate<Integer> exitCodePredicate, Function<Integer, String> errorMessage)
    {
        Integer exitCode = cliExec.getExitCode();
        Optional<String> out = cliExec.getOut();
        Optional<String> err = cliExec.getErr();
        assertTrue(exitCodePredicate.test(exitCode),
                errorMessage.apply(exitCode) + "\n" +
                        "Output:\n"
                        + (out.isPresent() ? out.get() : "") + "\n" +
                        "Error:\n"
                        + (err.isPresent() ? err.get() : ""));
        return this;
    }

    public CliExecAssertion assertOutContainsMatchingLine(String expectedLineRegexp)
    {
        Optional<String> out = cliExec.getOut();
        assertTrue(out.isPresent(), "No output stream found");
        assertStreamContainsLine("out", out.get(), expectedLineRegexp);
        return this;
    }

    public CliExecAssertion assertErrContainsMatchingLine(String expectedLineRegexp)
    {
        Optional<String> err = cliExec.getErr();
        assertTrue(err.isPresent(), "No error stream found");
        assertStreamContainsLine("error", err.get(), expectedLineRegexp);
        return this;
    }

    private void assertStreamContainsLine(String steamName, String streamContents, String expectedLineRegexp)
    {
        assertTrue(!streamContents.trim().isEmpty(), "Stream: %s is empty".formatted(steamName));
        Pattern linePattern = Pattern.compile(expectedLineRegexp);
        Optional<Matcher> matchingLine = Arrays.asList(streamContents.split(lineSeparator()))
                .stream()
                .map(linePattern::matcher)
                .filter(Matcher::matches)
                .findFirst();
        assertTrue(
                matchingLine.isPresent(),
                "Stream: %1$s does not contain line matching: %2$s\n%1$s\n%3$s"
                        .formatted(steamName, expectedLineRegexp, streamContents));
    }
}
