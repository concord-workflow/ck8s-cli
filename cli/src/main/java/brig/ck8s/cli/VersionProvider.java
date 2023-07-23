package brig.ck8s.cli;

import brig.ck8s.cli.common.CliCommand;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public class VersionProvider
        implements CommandLine.IVersionProvider
{

    public static String getCliVersion()
    {
        Properties props = new Properties();
        try {
            props.load(VersionProvider.class.getClassLoader().getResourceAsStream("project.properties"));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props.getProperty("project.version");
    }

    private static String javaVersion()
    {
        try {
            CliCommand.Result result = CliCommand.saveOut(Arrays.asList("java", "--version"), Path.of(System.getProperty("user.dir")))
                    .execute();
            return result.stdout();
        }
        catch (Exception e) {
            throw new RuntimeException("Can't get java version: " + e.getMessage());
        }
    }

    @Override
    public String[] getVersion()
    {
        return new String[] {"ck8s-cli version: " + getCliVersion(), javaVersion()};
    }
}
