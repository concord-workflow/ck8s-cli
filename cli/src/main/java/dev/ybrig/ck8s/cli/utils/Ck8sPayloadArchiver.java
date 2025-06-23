package dev.ybrig.ck8s.cli.utils;

import com.walmartlabs.concord.common.IOUtils;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Ck8sPayloadArchiver {

    public static class Archive implements AutoCloseable {

        private final Path archivePath;

        public Archive(Path path) {
            this.archivePath = path;
        }

        public Path path() {
            return archivePath;
        }

        @Override
        public void close() {
            cleanup(archivePath);
        }

        private static void cleanup(Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LogUtils.warn("archive cleanup ['{}'] -> error: {}", path, e.getMessage());
            }
        }
    }

    private static final String[] FILE_IGNORE_PATTERNS = new String[]{".*\\.pdf$", ".*\\.png$", ".*\\.jpg$"};

    public static void prepareWorkspace(Ck8sPath ck8s, Path target) {
        try {
            IOUtils.deleteRecursively(target);
        } catch (Exception e) {
            throw new RuntimeException("Can't delete target '" + target + "': " + e.getMessage());
        }

        try {
            Files.createDirectories(target);

            dev.ybrig.ck8s.cli.common.IOUtils.copy(ck8s.concordYaml(), target.resolve("concord.yml"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            dev.ybrig.ck8s.cli.common.IOUtils.copy(ck8s.configs(), target.resolve("configs"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            dev.ybrig.ck8s.cli.common.IOUtils.copy(ck8s.ck8sComponents(), target.resolve("ck8s-components"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            dev.ybrig.ck8s.cli.common.IOUtils.copy(ck8s.ck8sComponentsTests(), target.resolve("ck8s-components-tests"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            dev.ybrig.ck8s.cli.common.IOUtils.copy(ck8s.ck8sOrgDir(), target.resolve("ck8s-orgs"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
            dev.ybrig.ck8s.cli.common.IOUtils.copy(ck8s.ck8sConfigs(), target.resolve("ck8s-configs"), FILE_IGNORE_PATTERNS, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Error creating process workspace: " + e.getMessage(), e);
        }
    }

    public static Archive archive(Ck8sPath ck8s) {
        Path tmp;
        try {
            tmp = IOUtils.createTempFile("payload", ".zip");
        } catch (IOException e) {
            throw new RuntimeException("Error creating process archive file: " + e.getMessage(), e);
        }

        var result = new Archive(tmp);
        try (var zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp))) {

            IOUtils.zipFile(zip, ck8s.concordYaml(), "concord.yml");

            IOUtils.zip(zip, "configs/", ck8s.configs(), FILE_IGNORE_PATTERNS);
            IOUtils.zip(zip, "ck8s-components/", ck8s.ck8sComponents(), FILE_IGNORE_PATTERNS);

            IOUtils.zip(zip, "ck8s-components-tests/", ck8s.ck8sComponentsTests(), FILE_IGNORE_PATTERNS);

            IOUtils.zip(zip, "ck8s-orgs/", ck8s.ck8sOrgDir(), FILE_IGNORE_PATTERNS);
            IOUtils.zip(zip, "ck8s-configs/", ck8s.ck8sConfigs(), FILE_IGNORE_PATTERNS);

            return result;
        } catch (Exception e) {
            result.close();
            throw new RuntimeException("Error creating process archive: " + e.getMessage(), e);
        }
    }
}
