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
