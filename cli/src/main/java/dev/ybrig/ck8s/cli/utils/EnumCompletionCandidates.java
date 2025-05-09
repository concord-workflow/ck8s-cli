package dev.ybrig.ck8s.cli.utils;

import java.util.ArrayList;
import java.util.List;

public class EnumCompletionCandidates<T extends Enum<T>>
        extends ArrayList<String> {

    public static <T extends Enum<T>> List<String> convert(Class<T> enumClass) {
        List<String> result = new ArrayList<>();
        for (var enumVal : enumClass.getEnumConstants()) {
            var name = enumVal.name().toLowerCase();
            name = name.replace("_", "-");
            result.add(name);
        }
        return result;
    }

    public EnumCompletionCandidates(Class<T> enumClass) {
        super(convert(enumClass));
    }
}
