package org.jtools.xsdviewer.server;

/*-
 * #%L
 * XsdViewer
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
