package brig.ck8s.executor;

import brig.ck8s.model.ConcordConfiguration;
import brig.ck8s.utils.Mapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConcordConfigurationProvider {

    public static ConcordConfiguration get(String profile) {
        Path cfgPath = Path.of(System.getProperty("user.home")).resolve(".ck8s").resolve("concord-config.yaml");
        if (!Files.exists(cfgPath)) {
            try (InputStream in = ConcordConfigurationProvider.class.getResourceAsStream("/templates/default-concord-config.yaml")) {
                List<ConcordConfiguration> profiles = Mapper.yamlMapper().read(in, new TypeReference<List<ConcordConfiguration>>(){});
                return profiles.stream()
                        .filter(p -> profile.equals(p.alias()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Can't find profile '" + profile + "' in concord configuration. Create '" + cfgPath + "' and add profile '" + profile + "' into it"));
            } catch (IOException e) {
                throw new RuntimeException("Can't load default concord config. This is most likely a bug.");
            }
        }

        try {
            List<ConcordConfiguration> profiles = Mapper.yamlMapper().read(cfgPath, new TypeReference<List<ConcordConfiguration>>(){});
            return profiles.stream()
                    .filter(p -> profile.equals(p.alias()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Can't find profile '" + profile + "' in concord configuration: '" + cfgPath + "'"));
        } catch (Exception e) {
            throw new RuntimeException("Can't load concord configuration from '" + cfgPath + "': " + e.getMessage());
        }
    }
}
