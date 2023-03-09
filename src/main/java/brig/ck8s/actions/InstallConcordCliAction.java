package brig.ck8s.actions;

import brig.ck8s.executor.ConcordConfigurationProvider;
import brig.ck8s.utils.LogUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.StandardOpenOption.*;

public class InstallConcordCliAction {

    private static final String VERSION = "1.99.0";

    private static final String CONCORD_CLI_URL = String.format("https://repo.maven.apache.org/maven2/com/walmartlabs/concord/concord-cli/%s/concord-cli-%s-executable.jar", VERSION, VERSION);

    public static Path getCliPath() {
        return Path.of(System.getProperty("user.home")).resolve("bin").resolve("concord-cli");
    }

    public int perform() {
        installCli();

        installMvnConf();

        LogUtils.info("done");

        return 0;
    }

    private static void installCli() {
        Path dest = getCliPath();

        if (Files.notExists(dest.getParent())) {
            try {
                Files.createDirectories(dest.getParent());
            } catch (Exception e) {
                throw new RuntimeException("Error creating concord-cli directory: " + e.getMessage());
            }
        }

        LogUtils.info("Installing concord-cli to {}", dest);

        try (InputStream is = new URL(CONCORD_CLI_URL).openStream();
             ReadableByteChannel sourceChannel = Channels.newChannel(is);
             FileChannel destChannel = FileChannel.open(dest, WRITE, CREATE, TRUNCATE_EXISTING)) {

            destChannel.transferFrom(sourceChannel, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            throw new RuntimeException("Error downloading concord-cli: " + e.getMessage());
        }

        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        try {
            Files.setPosixFilePermissions(dest, perms);
        } catch (Exception e) {
            LogUtils.error("Error setting permissions to concord-cli: " + e.getMessage());
        }
    }

    private void installMvnConf() {
        Path cfgPath = Path.of(System.getProperty("user.home")).resolve(".concord").resolve("mvn.json");
        if (Files.notExists(cfgPath.getParent())) {
            try {
                Files.createDirectories(cfgPath.getParent());
            } catch (Exception e) {
                throw new RuntimeException("Error creating concord-cli directory: " + e.getMessage());
            }
        }

        if (!Files.exists(cfgPath)) {
            try (InputStream in = ConcordConfigurationProvider.class.getResourceAsStream("/templates/mvn.json")) {
                if (in == null) {
                    throw new RuntimeException("Can't find mvn.json template. This is most likely a bug.");
                }

                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                content = content.replace("<USER_HOME>", System.getProperty("user.home"));

                LogUtils.info("Installing concord mvn config to {}", cfgPath);

                Files.writeString(cfgPath, content);
            } catch (IOException e) {
                throw new RuntimeException("Can't load default concord config. This is most likely a bug.");
            }
        }
    }
}
