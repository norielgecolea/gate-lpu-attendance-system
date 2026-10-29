package org.nors.dev.codes.lpu.security;

/** CSV cell encoding that keeps spreadsheet formulas from running. */
public final class CsvCells {

    private CsvCells() {
    }

    public static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value;
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            text = "'" + text;
        }
        String escaped = text.replace("\"", "\"\"");
        if (text.startsWith("'")
                || escaped.indexOf(',') >= 0
                || escaped.indexOf('"') >= 0
                || escaped.indexOf('\n') >= 0
                || escaped.indexOf('\r') >= 0) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
