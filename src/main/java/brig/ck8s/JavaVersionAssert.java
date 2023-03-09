package brig.ck8s;

import brig.ck8s.utils.CliCommand;
import brig.ck8s.utils.TempPath;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class JavaVersionAssert {

    public static void assertAtLeast(int minMajorVersion) {
        try (TempPath workDir = TempPath.createDir("ck8s-cli-version");
             InputStream is = JavaVersionAssert.class.getResourceAsStream("/JavaFeatureVersion.class")) {
            if (is == null) {
                throw new RuntimeException("Can't read version class. This is most likely a bug.");
            }

            Path packageDir = workDir.path().resolve("brig").resolve("ck8s");
            Files.createDirectories(packageDir);
            Files.copy(is, packageDir.resolve("JavaFeatureVersion.class"), StandardCopyOption.REPLACE_EXISTING);

            List<String> args = new ArrayList<>();
            args.add("java");
            args.add("-cp");
            args.add(".");
            args.add("brig.ck8s.JavaFeatureVersion");

            String versionStr = CliCommand.grabOut(args, workDir.path());
            int version = Integer.parseInt(versionStr.trim());
            if (minMajorVersion >= version) {
                throw new RuntimeException("Java version " + minMajorVersion + "+ required in PATH. Current version: \n" + getJavaVersion(workDir.path()));
            }
        } catch (ExecutionException e) {
            throw new RuntimeException("Can't get java version from PATH: " + e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getJavaVersion(Path workDir) {
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("--version");

        try {
            return CliCommand.grabOut(args, workDir).trim();
        } catch (Exception e) {
            throw new RuntimeException("Error getting java version: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        JavaVersionAssert.assertAtLeast(99);
    }
}
