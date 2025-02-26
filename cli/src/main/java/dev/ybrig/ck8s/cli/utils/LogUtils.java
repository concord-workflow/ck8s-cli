package dev.ybrig.ck8s.cli.utils;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.util.List;

public final class LogUtils {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RED_BOLD = "\033[1;31m";

    public static void info(String log, Object... args) {
        var m = MessageFormatter.arrayFormat(log, args);
        System.out.println(m.getMessage());
    }

    public static void warn(String log, Object... args) {
        var m = MessageFormatter.arrayFormat(log, args);
        System.out.println("WARN: " + m.getMessage());
    }

    public static void error(String log, Object... args) {
        var m = MessageFormatter.arrayFormat(log, args);
        System.err.println(ANSI_RED_BOLD + "ERROR: " + m.getMessage() + ANSI_RESET);
        if (m.getThrowable() != null) {
            m.getThrowable().printStackTrace(System.err);
        }
    }

    public static void logAsTable(List<String[]> rows) {
        if (rows.isEmpty()) {
            return;
        }

        var formatString = buildFormatString(maxRow(rows));
        for (var row : rows) {
            System.out.print(formatNull(formatString, row));
        }
    }

    private LogUtils() {
    }

    private static String buildFormatString(int[] row) {
        var result = new StringBuilder();
        for (var c : row) {
            result.append("%").append(c).append("s ");
        }
        return result.append('\n').toString();
    }

    private static int[] maxRow(List<String[]> rows) {
        var result = new int[rows.get(0).length];
        for (var r : rows) {
            for (var i = 0; i < r.length; i++) {
                var len = r[i] != null ? r[i].length() + 2 : 5;
                if (len > result[i]) {
                    result[i] = len;
                }
            }
        }
        return result;
    }

    private static String formatNull(String str, Object[] args) {
        for (var i = 0; i < args.length; i++) {
            if (args[i] == null) {
                args[i] = "n/a";
            }
        }

        return String.format(str, args);
    }
}
