package dev.ybrig.ck8s.cli.utils;

import java.util.ArrayList;
import java.util.List;

public final class ExceptionUtils
{

    private ExceptionUtils()
    {
    }

    public static void throwError(String msg, Throwable cause)
    {
        List<String> causeErrors = new ArrayList<>();
        while (cause != null) {
            causeErrors.add(cause.getMessage());
            cause = cause.getCause();
        }

        throw new RuntimeException(msg + String.join(". ", causeErrors));
    }
}
