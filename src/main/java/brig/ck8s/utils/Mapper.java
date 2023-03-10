package brig.ck8s.utils;

import brig.ck8s.model.MandatoryValuesMissing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

public class Mapper {

    public static Mapper jsonMapper() {
        return jsonMapper;
    }

    public static Mapper yamlMapper() {
        return yamlMapper;
    }

    public static Mapper xmlMapper() {
        return xmlMapper;
    }

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final Mapper yamlMapper = new Mapper(createYamlObjectMapper());
    private static final Mapper jsonMapper = new Mapper(createJsonObjectMapper());
    private static final Mapper xmlMapper = new Mapper(createXmlObjectMapper());

    private final ObjectMapper objectMapper;

    public Mapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> readMap(Path p) {
        try {
            return objectMapper.readValue(p.toFile(), MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
    }

    public Map<String, Object> readMap(URL url) {
        try {
            return objectMapper.readValue(url, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException("Error reading '" + url + "': " + e.getMessage());
        }
    }

    public <T> T read(InputStream in, Class<T> clazz) throws IOException {
        return objectMapper.readValue(in, clazz);
    }

    public <T> T read(Path p, Class<T> clazz) {
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

    public void write(Path path, Object value) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(path.toFile(), value);
        } catch (Exception e) {
            throw new RuntimeException("Error writing value to '" + path + "': " + e.getMessage());
        }
    }

    private static ObjectMapper createYamlObjectMapper() {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        return new ObjectMapper(yamlFactory);
    }

    private static ObjectMapper createJsonObjectMapper() {
        return new ObjectMapper();
    }

    private static ObjectMapper createXmlObjectMapper() {
        return new XmlMapper();
    }
}
