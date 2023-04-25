package brig.ck8s.cfg;

import brig.ck8s.model.ConcordConfiguration;
import brig.ck8s.utils.MapUtils;
import brig.ck8s.utils.Mapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConcordConfigurationProvider {

    public static ConcordConfiguration get(String profile) {
        List<ConcordConfiguration> profiles = loadFromCfg();
        if (profiles.isEmpty()) {
            profiles = loadFromOldCfg();
        }
        if (profiles.isEmpty()) {
            profiles = loadFromDefaults();
        }

        return profiles.stream()
                .filter(p -> profile.equals(p.alias()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find profile '" + profile + "' in configuration"));
    }

    @SuppressWarnings("unchecked")
    private static List<ConcordConfiguration> loadFromCfg() {
        Map<String, Object> cfgPath = CliConfigurationProvider.loadCfg();
        if (cfgPath.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<Object> concordObject = MapUtils.get(cfgPath, "concord", Collections.emptyList(), List.class);
            return Mapper.yamlMapper().convertValue(concordObject, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Can't load concord configuration from '" + cfgPath + "': " + e.getMessage());
        }
    }

    private static List<ConcordConfiguration> loadFromOldCfg() {
        Path cfgPath = Path.of(System.getProperty("user.home")).resolve(".ck8s").resolve("concord-config.yaml");
        if (!Files.exists(cfgPath)) {
            return Collections.emptyList();
        }

        try {
            return Mapper.yamlMapper().read(cfgPath, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Can't load concord configuration from '" + cfgPath + "': " + e.getMessage());
        }
    }

    private static List<ConcordConfiguration> loadFromDefaults() {
        try (InputStream in = ConcordConfigurationProvider.class.getResourceAsStream("/templates/default-concord-config.yaml")) {
            return Mapper.yamlMapper().read(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException("Can't load default concord config. This is most likely a bug.");
        }
    }
}
