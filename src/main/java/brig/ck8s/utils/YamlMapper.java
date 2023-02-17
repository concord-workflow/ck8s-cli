package brig.ck8s.utils;

import brig.ck8s.model.MandatoryValuesMissing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

public final class YamlMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final ObjectMapper objectMapper = createObjectMapper();

    public static Map<String, Object> readMap(Path p) {
        try {
            return objectMapper.readValue(p.toFile(), MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
    }

    public static Map<String, Object> readMap(URL url) {
        try {
            return objectMapper.readValue(url, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Error reading '" + url + "': " + e.getMessage());
        }
    }

    public static <T> T read(Path p, Class<T> clazz) {
        try {
            return objectMapper.readValue(p.toFile(), clazz);
        } catch (ValueInstantiationException e) {
            if (e.getCause() instanceof MandatoryValuesMissing mvm) {
                throw mvm;
            }
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
    }

    public static void write(Path path, Object value) {
        try {
            objectMapper.writeValue(path.toFile(), value);
        } catch (Exception e) {
            throw new RuntimeException("Error writing value to '" + path + "': " + e.getMessage());
        }
    }

    private static ObjectMapper createObjectMapper() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        return new ObjectMapper(yamlFactory);
    }

    private YamlMapper() {
    }
}
