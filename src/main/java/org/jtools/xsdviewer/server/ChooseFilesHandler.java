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
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/** {@code POST /api/choose}: the native "open files" dialog, so that the files chosen come with their location. 409 without a display. */
final class ChooseFilesHandler implements HttpHandler {

    private static final String SCHEMA_EXTENSION = ".xsd";
    private static final String WSDL_EXTENSION = ".wsdl";
    private static final String XML_EXTENSION = ".xml";

    private final ServedSchemaFiles files;

    ChooseFilesHandler(ServedSchemaFiles files) {
        this.files = files;
    }

    static boolean isSchemaName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(SCHEMA_EXTENSION) || n.endsWith(WSDL_EXTENSION) || n.endsWith(XML_EXTENSION);
    }

    /** What the "open" dialog lets pick: schema files, WSDL files, any XML. */
    static FileDialogs.Filter schemaFilter() {
        return new FileDialogs.Filter(Messages.get(MessageKey.DIALOG_FILTER_SCHEMAS),
                List.of("*" + SCHEMA_EXTENSION, "*" + WSDL_EXTENSION, "*" + XML_EXTENSION));
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        if (!FileDialogs.available()) {
            HttpResponses.error(ex, HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
            return;
        }
        List<Path> chosen = FileDialogs.chooseFilesToOpen(Messages.get(MessageKey.DIALOG_OPEN_SCHEMA), true, schemaFilter());
        JsonWriter w = new JsonWriter(4096).beginObject().name(JsonKey.FILES).beginArray();
        for (Path p : chosen) files.writeFile(w, p);
        HttpResponses.json(ex, HttpStatus.OK, w.endArray().endObject().toString());
    }
}
