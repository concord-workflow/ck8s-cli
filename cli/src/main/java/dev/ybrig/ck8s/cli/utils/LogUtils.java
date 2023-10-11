package dev.ybrig.ck8s.cli.utils;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.util.List;

public final class LogUtils
{

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_RED_BOLD = "\033[1;31m";

    private LogUtils()
    {
    }

    public static void info(String log, Object... args)
    {
        FormattingTuple m = MessageFormatter.arrayFormat(log, args);
        System.out.println(m.getMessage());
    }

    public static void warn(String log, Object... args)
    {
        FormattingTuple m = MessageFormatter.arrayFormat(log, args);
        System.out.println("WARN: " + m.getMessage());
    }

    public static void error(String log, Object... args)
    {
        FormattingTuple m = MessageFormatter.arrayFormat(log, args);
        System.err.println(ANSI_RED_BOLD + "ERROR: " + m.getMessage() + ANSI_RESET);
        if (m.getThrowable() != null) {
            m.getThrowable().printStackTrace(System.err);
        }
    }

    public static void logAsTable(List<String[]> rows)
    {
        if (rows.isEmpty()) {
            return;
        }

        String formatString = buildFormatString(maxRow(rows));
        for (String[] row : rows) {
            System.out.print(formatNull(formatString, row));
        }
    }

    private static String buildFormatString(int[] row)
    {
        StringBuilder result = new StringBuilder();
        for (int c : row) {
            result.append("%").append(c).append("s ");
        }
        return result.append('\n').toString();
    }

    private static int[] maxRow(List<String[]> rows)
    {
        int[] result = new int[rows.get(0).length];
        for (String[] r : rows) {
            for (int i = 0; i < r.length; i++) {
                int len = r[i] != null ? r[i].length() + 2 : 5;
                if (len > result[i]) {
                    result[i] = len;
                }
            }
        }
        return result;
    }

    private static String formatNull(String str, Object[] args)
    {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                args[i] = "n/a";
            }
        }

        return String.format(str, args);
    }
}
