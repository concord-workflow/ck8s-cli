package dev.ybrig.ck8s.cli.common;

import java.io.IOException;
import java.util.Properties;

public final class VersionProvider {

    public static String get() {
        var props = new Properties();
        try {
            props.load(VersionProvider.class.getClassLoader().getResourceAsStream("dev/ybrig/ck8s/cli/common/project.properties"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props.getProperty("project.version");
    }

    private VersionProvider() {
    }
}
