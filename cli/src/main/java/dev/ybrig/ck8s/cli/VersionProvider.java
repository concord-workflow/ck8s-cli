package dev.ybrig.ck8s.cli;

import dev.ybrig.ck8s.cli.common.CliCommand;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Arrays;

public class VersionProvider
        implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[]{"ck8s-cli version: " + dev.ybrig.ck8s.cli.common.VersionProvider.get(), javaVersion()};
    }

    private static String javaVersion() {
        try {
            var result = CliCommand.saveOut(Arrays.asList("java", "--version"), Path.of(System.getProperty("user.dir")))
                    .execute();
            return result.stdout();
        } catch (Exception e) {
            throw new RuntimeException("Can't get java version: " + e.getMessage());
        }
    }
}
