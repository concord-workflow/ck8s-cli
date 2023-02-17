package brig.ck8s.utils;

import picocli.CommandLine;

public class EnumConverter<T extends Enum<T>> implements CommandLine.ITypeConverter<T> {

    private final Class<T> clazz;

    public EnumConverter(Class<T> enumClass) {
        this.clazz = enumClass;
    }

    @Override
    public T convert(String value) {
        String name = value.toUpperCase();
        name = name.replace("-", "_");
        try {
            return Enum.valueOf(clazz, name);
        } catch (IllegalArgumentException e) {
            throw new CommandLine.TypeConversionException("unknown '" + value + "'. available candidates: " + EnumCompletionCandidates.convert(clazz));
        }
    }
}
