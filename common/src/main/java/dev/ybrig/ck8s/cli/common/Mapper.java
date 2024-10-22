package dev.ybrig.ck8s.cli.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;

public class Mapper
{

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>()
    {
    };
    private static final Mapper yamlMapper = new Mapper(createYamlObjectMapper());
    private static final Mapper jsonMapper = new Mapper(createJsonObjectMapper());
    private static final Mapper xmlMapper = new Mapper(createXmlObjectMapper());
    private final ObjectMapper objectMapper;

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public Mapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public static Mapper jsonMapper()
    {
        return jsonMapper;
    }

    public static Mapper yamlMapper()
    {
        return yamlMapper;
    }

    public static Mapper xmlMapper()
    {
        return xmlMapper;
    }

    private static ObjectMapper createYamlObjectMapper()
    {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        return new ObjectMapper(yamlFactory);
    }

    private static ObjectMapper createJsonObjectMapper()
    {
        var om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        return om;
    }

    private static ObjectMapper createXmlObjectMapper()
    {
        return new XmlMapper();
    }

    public Map<String, Object> readMap(Path p)
    {
        try {
            return objectMapper.readValue(p.toFile(), MAP_TYPE);
        }
        catch (Exception e) {
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
    }

    public Map<String, Object> readMap(String content) {
        try {
            return objectMapper.readValue(content, MAP_TYPE);
        }
        catch (Exception e) {
            throw new RuntimeException("Error reading '" + content + "': " + e.getMessage());
        }
    }

    public Map<String, Object> readMap(URL url)
    {
        try {
            return objectMapper.readValue(url, MAP_TYPE);
        }
        catch (Exception e) {
            throw new RuntimeException("Error reading '" + url + "': " + e.getMessage());
        }
    }

    public <T> T read(InputStream in, Class<T> clazz)
            throws IOException
    {
        return objectMapper.readValue(in, clazz);
    }

    public <T> T read(InputStream in, TypeReference<T> valueTypeRef)
            throws IOException
    {
        return objectMapper.readValue(in, valueTypeRef);
    }

    public <T> T read(Path p, Class<T> clazz)
    {
        try {
            return objectMapper.readValue(p.toFile(), clazz);
        }
        catch (ValueInstantiationException e) {
            if (e.getCause() instanceof MandatoryValuesMissing) {
                throw (MandatoryValuesMissing)e.getCause();
            }
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
        catch (Exception e) {
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
    }

    public <T> T read(Path p, TypeReference<T> valueTypeRef)
    {
        try {
            return objectMapper.readValue(p.toFile(), valueTypeRef);
        }
        catch (ValueInstantiationException e) {
            if (e.getCause() instanceof MandatoryValuesMissing) {
                throw (MandatoryValuesMissing)e.getCause();
            }
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
        catch (Exception e) {
            throw new RuntimeException("Error reading '" + p.toAbsolutePath().normalize() + "': " + e.getMessage());
        }
    }

    public void write(Path path, Object value)
    {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(path.toFile(), value);
        }
        catch (Exception e) {
            throw new RuntimeException("Error writing value to '" + path + "': " + e.getMessage());
        }
    }

    public void write(OutputStream out, Object value)
    {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(out, value);
        }
        catch (Exception e) {
            throw new RuntimeException("Error writing value to file: " + e.getMessage());
        }
    }

    public String writeAsString(Object value)
    {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(value);
        }
        catch (Exception e) {
            throw new RuntimeException("Error writing value '" + value + "' to string: " + e.getMessage());
        }
    }

    public <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef)
    {
        return objectMapper.convertValue(fromValue, toValueTypeRef);
    }
}
