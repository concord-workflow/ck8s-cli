package brig.ck8s;

import picocli.CommandLine;

import java.io.IOException;
import java.util.Properties;

public class VersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        Properties props = new Properties();
        try {
            props.load(VersionProvider.class.getClassLoader().getResourceAsStream("project.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String[]{props.getProperty("project.version")};
    }
}
