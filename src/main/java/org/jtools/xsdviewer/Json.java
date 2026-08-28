package org.jtools.xsdviewer;

/** Minimal JSON string encoding; the model is simple enough not to need a library. */
final class Json {

    private Json() {}

    static void string(StringBuilder sb, String s) {
        if (s == null) {
            sb.append("null");
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
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    static String string(String s) {
        StringBuilder sb = new StringBuilder(s == null ? 4 : s.length() + 2);
        string(sb, s);
        return sb.toString();
    }
}
