package dev.ybrig.ck8s.cli.executor;

import dev.ybrig.ck8s.cli.utils.ExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class MvnJsonProvider {

    public Path get() {
        var cfgPath = Path.of(System.getProperty("user.home")).resolve(".ck8s").resolve("mvn.json");
        if (Files.exists(cfgPath)) {
            return cfgPath;
        }

        installFromTemplate(cfgPath);
        return cfgPath;
    }

    private void installFromTemplate(Path cfgPath) {
        if (Files.notExists(cfgPath.getParent())) {
            try {
                Files.createDirectories(cfgPath.getParent());
            } catch (Exception e) {
                ExceptionUtils.throwError("Error creating concord-cli directory: ", e);
            }
        }

        try (var in = MvnJsonProvider.class.getResourceAsStream("/templates/mvn.json")) {
            if (in == null) {
                throw new RuntimeException("Can't find mvn.json template. This is most likely a bug.");
            }

            var content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            content = content.replace("<USER_HOME>", System.getProperty("user.home"));

            Files.writeString(cfgPath, content, CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            ExceptionUtils.throwError("Can't load default concord config. This is most likely a bug: ", e);
        }
    }
}
