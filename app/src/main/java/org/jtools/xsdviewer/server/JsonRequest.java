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

import java.io.IOException;
import java.util.Map;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonReader;

import com.sun.net.httpserver.HttpExchange;

/**
 * The opening every handler of a JSON request shares: the method checked, the body read and parsed,
 * a malformed body answered as {@code 400} rather than a dropped connection. Null when the request
 * has already been answered, which the handler takes as its cue to stop.
 */
final class JsonRequest {

    private JsonRequest() {}

    static Map<String, Object> of(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return null;
        try {
            return JsonReader.asObject(JsonReader.parse(HttpResponses.readBody(ex)));
        } catch (IllegalArgumentException e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.INVALID_JSON, e.getMessage()));
            return null;
        }
    }
}
