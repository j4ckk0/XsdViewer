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

import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.WebServer;

/**
 * The opening every route of a JSON request shares: the body parsed by core's reader, a malformed one refused with
 * {@code 400} and where it went wrong.
 */
final class JsonRequest {

    private JsonRequest() {}

    static Map<String, Object> of(Exchange ex) throws IOException {
        try {
            return JsonReader.asObject(JsonReader.parse(ex.text()));
        } catch (IllegalArgumentException e) {
            throw new WebServer.Refusal(HttpStatus.BAD_REQUEST, Messages.get(MessageKey.INVALID_JSON, e.getMessage()));
        }
    }
}
