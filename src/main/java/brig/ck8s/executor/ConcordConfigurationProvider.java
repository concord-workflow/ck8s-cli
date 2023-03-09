package brig.ck8s.executor;

import brig.ck8s.model.ConcordConfiguration;
import brig.ck8s.utils.YamlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConcordConfigurationProvider {

    public static ConcordConfiguration get() {
        Path cfgPath = Path.of(System.getProperty("user.home")).resolve(".ck8s-cli").resolve("concord-config.yaml");
        if (!Files.exists(cfgPath)) {
            try (InputStream in = ConcordConfigurationProvider.class.getResourceAsStream("/templates/default-concord-config.yaml")) {
                return YamlMapper.read(in, ConcordConfiguration.class);
            } catch (IOException e) {
                throw new RuntimeException("Can't load default concord config. This is most likely a bug.");
            }
        }

        try {
            return YamlMapper.read(cfgPath, ConcordConfiguration.class);
        } catch (Exception e) {
            throw new RuntimeException("Can't load concord configuration from '" + cfgPath + "': " + e.getMessage());
        }
    }
}
