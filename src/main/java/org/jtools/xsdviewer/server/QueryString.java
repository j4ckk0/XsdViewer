package org.jtools.xsdviewer.server;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

/** The decoded {@code key=value} parameters of a request's query string. */
final class QueryString {

    private static final String PARAMETER_SEPARATOR = "&";
    private static final char VALUE_SEPARATOR = '=';

    private final Map<String, String> params = new HashMap<>();

    QueryString(String rawQuery) {
        if (rawQuery == null) return;
        for (String part : rawQuery.split(PARAMETER_SEPARATOR)) {
            int eq = part.indexOf(VALUE_SEPARATOR);
            if (eq < 0) continue;
            params.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
    }

    static QueryString of(HttpExchange ex) {
        return new QueryString(ex.getRequestURI().getRawQuery());
    }

    /** The parameter's value, or "" when absent. */
    String get(String name) {
        return params.getOrDefault(name, "");
    }
}
