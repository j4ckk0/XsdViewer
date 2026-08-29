package org.jtools.xsdviewer.json;

/** JSON encoding of strings (quoting and escaping); the rest of the JSON is written by {@link JsonWriter}. */
public final class JsonStrings {

    public static final String NULL = "null";
    private static final String UNICODE_ESCAPE = "\\u%04x";

    private JsonStrings() {}

    /** Appends {@code s} as a quoted, escaped JSON string ({@code null} becomes the JSON null). */
    public static void quote(StringBuilder sb, String s) {
        if (s == null) {
            sb.append(NULL);
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format(UNICODE_ESCAPE, (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder(s == null ? NULL.length() : s.length() + 2);
        quote(sb, s);
        return sb.toString();
    }
}
