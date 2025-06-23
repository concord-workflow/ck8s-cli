package dev.ybrig.ck8s.cli.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public final class IOUtils {

    public static boolean deleteRecursively(Path p) {
        if (!Files.exists(p)) {
            return false;
        }

        try {
            if (!Files.isDirectory(p)) {
                Files.delete(p);
                return true;
            }

            Files.walkFileTree(p, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copy(Path src, Path dst, String[] skipContents, CopyOption... options) throws IOException {
        _copy(src, src, dst, List.of(skipContents), options);
    }

    public static void copy(Path src, Path dst, List<String> skipContents, CopyOption... options) throws IOException {
        _copy(src, src, dst, skipContents, options);
    }

    public static String toString(InputStream input) {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private IOUtils() {
    }

    private static void _copy(Path root, Path src, Path dst, List<String> ignorePattern, CopyOption... options) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir != src && anyMatch(src.relativize(dir).toString(), ignorePattern)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file != src && anyMatch(src.relativize(file).toString(), ignorePattern)) {
                    return FileVisitResult.CONTINUE;
                }

                var a = file;
                var b = dst.resolve(src.relativize(file));

                var parent = b.getParent();
                if (!Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                if (Files.isSymbolicLink(file)) {
                    var link = Files.readSymbolicLink(file);
                    var target = file.getParent().resolve(link).normalize();

                    if (!target.startsWith(root)) {
                        throw new IOException("Symlinks outside the base directory are not supported: " + file + " -> " + target);
                    }

                    if (Files.notExists(target)) {
                        // missing target
                        return FileVisitResult.CONTINUE;
                    }

                    Files.createSymbolicLink(b, link);
                    return FileVisitResult.CONTINUE;
                }

                Files.copy(a, b, options);

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean anyMatch(String what, List<String> patterns) {
        if (patterns == null) {
            return false;
        }

        return patterns.stream().anyMatch(what::matches);
    }

}
