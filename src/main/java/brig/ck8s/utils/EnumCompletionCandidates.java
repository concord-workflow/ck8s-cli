package brig.ck8s.utils;

import java.util.ArrayList;
import java.util.List;

public class EnumCompletionCandidates<T extends Enum<T>>  extends ArrayList<String> {

    public EnumCompletionCandidates(Class<T> enumClass) {
        super(convert(enumClass));
    }

    public static <T extends Enum<T>> List<String> convert(Class<T> enumClass) {
        List<String> result = new ArrayList<>();
        for (T enumVal : enumClass.getEnumConstants()) {
            String name = enumVal.name().toLowerCase();
            name = name.replace("_", "-");
            result.add(name);
        }
        return result;
    }
}
