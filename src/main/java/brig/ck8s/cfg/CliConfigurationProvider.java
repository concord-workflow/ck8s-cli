package brig.ck8s.cfg;

import brig.ck8s.utils.MapUtils;
import brig.ck8s.utils.Mapper;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

public class CliConfigurationProvider implements CommandLine.IDefaultValueProvider {

    private static final Map<String, String> mapping = Map.of(
            "--target-root", "targetDir",
            "--ck8s-root", "ck8sDir",
            "--ck8s-ext-root", "ck8sExtDir"
    );

    private Map<String, Object> properties;

    @Override
    public String defaultValue(CommandLine.Model.ArgSpec argSpec) throws Exception {
        if (!argSpec.isOption()) {
            return null;
        }

        if (properties == null) {
            properties = loadCfg();
        }

        String propertiesPath = mapping.get(((CommandLine.Model.OptionSpec)argSpec).longestName());
        if (propertiesPath == null) {
            return null;
        }

        return MapUtils.getString(properties, propertiesPath);
    }

    public static Map<String, Object> loadCfg() {
        Path propPath = Paths.get(System.getProperty("user.home")).resolve(".ck8s").resolve("ck8s-cli.yaml");
        if (!Files.exists(propPath)) {
            return Collections.emptyMap();
        }

        return Mapper.yamlMapper().readMap(propPath);
    }
}
