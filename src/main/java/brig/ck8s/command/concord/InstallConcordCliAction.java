package brig.ck8s.command.concord;

import brig.ck8s.utils.LogUtils;
import io.quarkus.logging.Log;

import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

public class InstallConcordCliAction {

    private static final String CONCORD_CLI_URL = "https://repo.maven.apache.org/maven2/com/walmartlabs/concord/concord-cli/1.98.1/concord-cli-1.98.1-executable.jar";

    public void perform() {
        Path dest = Path.of(System.getProperty("user.home")).resolve("bin").resolve("concord-cli");
        if (Files.exists(dest)) {
            LogUtils.info("concord-cli already exists: {}", dest.toAbsolutePath());
            return;
        }

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
             FileChannel destChannel = FileChannel.open(dest, WRITE, CREATE)) {

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
        
        Log.info("done");
    }
}
