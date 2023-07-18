package brig.ck8s.cli.cfg;

import brig.ck8s.cli.model.CliConfiguration;
import brig.ck8s.cli.model.ConcordProfile;
import brig.ck8s.cli.common.Mapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class CliConfigurationProvider
{

    private static final List<Supplier<CliConfiguration>> providers = List.of(new MainProvider(), new OldCfgProvider(), new DefaultsProvider());
    private static final MainProvider provider = new MainProvider();
    private static CliConfiguration cfg;

    public static CliConfiguration get()
    {
        if (cfg != null) {
            return cfg;
        }

        cfg = providers.stream()
                .map(Supplier::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return cfg;
    }

    public static ConcordProfile getConcordProfile(String profile)
    {
        return get().concordProfiles().stream()
                .filter(p -> profile.equals(p.alias()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find profile '" + profile + "' in configuration"));
    }

    public static void replace(CliConfiguration newCfg)
    {
        cfg = newCfg;
        provider.save(cfg);
    }

    private static class MainProvider
            implements Supplier<CliConfiguration>
    {

        @Override
        public CliConfiguration get()
        {
            Path path = Paths.get(System.getProperty("user.home")).resolve(".ck8s").resolve("ck8s-cli.yaml");
            if (!Files.exists(path)) {
                return null;
            }

            try {
                return Mapper.yamlMapper().read(path, new TypeReference<>()
                {
                });
            }
            catch (Exception e) {
                throw new RuntimeException("Can't load configuration from '" + path + "': " + e.getMessage());
            }
        }

        public void save(CliConfiguration cfg)
        {
            Path path = Paths.get(System.getProperty("user.home")).resolve(".ck8s").resolve("ck8s-cli.yaml");

            try {
                Files.writeString(path, Mapper.yamlMapper().writeAsString(cfg), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            catch (Exception e) {
                throw new RuntimeException("Can't save configuration to '" + path + "': " + e.getMessage());
            }
        }
    }

    private static class OldCfgProvider
            implements Supplier<CliConfiguration>
    {

        @Override
        public CliConfiguration get()
        {
            Path path = Path.of(System.getProperty("user.home")).resolve(".ck8s").resolve("concord-config.yaml");
            if (!Files.exists(path)) {
                return null;
            }

            try {
                List<ConcordProfile> profiles = Mapper.yamlMapper().read(path, new TypeReference<>()
                {
                });
                return CliConfiguration.builder()
                        .concordProfiles(profiles)
                        .build();
            }
            catch (Exception e) {
                throw new RuntimeException("Can't load configuration from '" + path + "': " + e.getMessage());
            }
        }
    }

    private static class DefaultsProvider
            implements Supplier<CliConfiguration>
    {

        @Override
        public CliConfiguration get()
        {
            try (InputStream in = CliConfigurationProvider.class.getResourceAsStream("/templates/default-ck8s-cli-config.yaml")) {
                return Mapper.yamlMapper().read(in, new TypeReference<>() {});
            }
            catch (Exception e) {
                throw new RuntimeException("Can't load default config. This is most likely a bug.");
            }
        }
    }
}
