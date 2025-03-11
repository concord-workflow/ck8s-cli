package dev.ybrig.ck8s.cli.utils;

import com.walmartlabs.concord.common.IOUtils;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.common.Ck8sUtils;
import dev.ybrig.ck8s.cli.common.MapUtils;
import dev.ybrig.ck8s.cli.common.Mapper;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class Ck8sPayloadArchiver {

    public static class Archive implements AutoCloseable {

        private final Path archivePath;
        private final List<Path> pathsToCleanup = new ArrayList<>();

        public Archive(Path path) {
            this.archivePath = path;
        }

        public Path path() {
            return archivePath;
        }

        @Override
        public void close() {
            pathsToCleanup.forEach(Archive::cleanup);
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

    public static Archive archive(Ck8sPath ck8s, String clusterAlias) {
        Path tmp;
        try {
            tmp = IOUtils.createTempFile("payload", ".zip");
        } catch (IOException e) {
            throw new RuntimeException("Error creating process archive file: " + e.getMessage(), e);
        }

        var result = new Archive(tmp);
        try (var zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp))) {
            var concordYaml = ck8s.ck8sDir().resolve("concord.yml");
            if (Files.notExists(concordYaml)) {
                LogUtils.error("Can't find main concord.yml at '{}'. Update your ck8s-ext local copy", concordYaml);
                throw new RuntimeException("Can't find main concord.yml, update your ck8s-ext local copy");
            }

            IOUtils.zipFile(zip, concordYaml, "concord.yml");

            IOUtils.zip(zip, "configs/", ck8s.configs(), FILE_IGNORE_PATTERNS);
            IOUtils.zip(zip, "ck8s-components/", ck8s.ck8sComponents(), FILE_IGNORE_PATTERNS);

            IOUtils.zip(zip, "ck8s-components-tests/", ck8s.ck8sComponentsTests(), FILE_IGNORE_PATTERNS);

            IOUtils.zip(zip, "ck8s-orgs/", ck8s.ck8sOrgDir(), FILE_IGNORE_PATTERNS);
            IOUtils.zip(zip, "ck8s-configs/", ck8s.ck8sConfigs(), FILE_IGNORE_PATTERNS);

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            result.close();
            throw new RuntimeException("Error creating process archive: " + e.getMessage(), e);
        }
    }
}
