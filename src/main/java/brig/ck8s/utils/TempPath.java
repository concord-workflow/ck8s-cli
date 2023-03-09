package brig.ck8s.utils;

import com.walmartlabs.concord.common.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TempPath implements AutoCloseable {

    public static TempPath createDir(String prefix) throws IOException {
        return new TempPath(Files.createTempDirectory(prefix));
    }

    public static TempPath createFile(String prefix) throws IOException {
        return new TempPath(Files.createTempFile(prefix, null));
    }

    private final Path path;

    TempPath(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        if (path == null) {
            return;
        }

        try {
            if (Files.isDirectory(path)) {
                IOUtils.deleteRecursively(path);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            LogUtils.warn("cleanup ['{}'] -> error: {}", path, e.getMessage());
        }
    }
}
