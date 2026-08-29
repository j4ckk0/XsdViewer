package org.jtools.xsdviewer.server;

import java.util.Map;

/** MIME types of the answers; static files get theirs from their extension. */
final class ContentType {

    private ContentType() {}

    static final String HEADER = "Content-Type";
    static final String TEXT_PREFIX = "text/";
    static final String CHARSET_UTF8 = "; charset=utf-8";

    static final String TEXT_PLAIN = "text/plain";
    static final String JSON = "application/json";
    static final String OCTET_STREAM = "application/octet-stream";

    private static final Map<String, String> BY_EXTENSION = Map.of(
            ".html", "text/html",
            ".js", "text/javascript",
            ".css", "text/css",
            ".json", JSON,
            ".svg", "image/svg+xml",
            ".png", "image/png");

    /** The type for a file name, with the charset for text types. */
    static String forFile(String path) {
        int dot = path.lastIndexOf('.');
        String type = dot < 0 ? null : BY_EXTENSION.get(path.substring(dot));
        return withCharset(type == null ? OCTET_STREAM : type);
    }

    /** {@code text/*} types are declared UTF-8, the others are left alone. */
    static String withCharset(String type) {
        return type.startsWith(TEXT_PREFIX) ? type + CHARSET_UTF8 : type;
    }
}
