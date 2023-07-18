package brig.ck8s.cli.cfg;

import brig.ck8s.cli.common.MapUtils;
import brig.ck8s.cli.common.Mapper;
import com.fasterxml.jackson.core.type.TypeReference;
import picocli.CommandLine;

import java.util.Map;

public class CliDefaultParamValuesProvider
        implements CommandLine.IDefaultValueProvider
{

    private static final Map<String, String> mapping = Map.of(
            "--target-root", "targetDir",
            "--ck8s-root", "ck8sDir",
            "--ck8s-ext-root", "ck8sExtDir"
    );

    private Map<String, Object> properties;

    @Override
    public String defaultValue(CommandLine.Model.ArgSpec argSpec)
    {
        if (!argSpec.isOption()) {
            return null;
        }

        if (properties == null) {
            properties = Mapper.yamlMapper().convertValue(CliConfigurationProvider.get(), new TypeReference<>()
            {
            });
        }

        String propertiesPath = mapping.get(((CommandLine.Model.OptionSpec) argSpec).longestName());
        if (propertiesPath == null) {
            return null;
        }

        return MapUtils.getString(properties, propertiesPath);
    }
}
