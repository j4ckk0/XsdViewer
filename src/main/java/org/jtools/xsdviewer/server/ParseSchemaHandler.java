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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.schema.SchemaParser;

/** {@code POST /api/parse}, body = the text of a schema file (XSD or WSDL): answers the JSON graph, or 400 with the parse error. */
final class ParseSchemaHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        String text = HttpResponses.readBody(ex);
        try {
            HttpResponses.json(ex, HttpStatus.OK, SchemaParser.parse(text).toJson());
        } catch (Exception e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
