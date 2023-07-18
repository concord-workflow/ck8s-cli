package brig.ck8s.cli.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class IOUtils
{

    private IOUtils()
    {
    }

    public static boolean deleteRecursively(Path p)
    {
        if (!Files.exists(p)) {
            return false;
        }

        try {
            if (!Files.isDirectory(p)) {
                Files.delete(p);
                return true;
            }

            Files.walkFileTree(p, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException
                {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException
                {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            return true;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(InputStream input)
    {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
