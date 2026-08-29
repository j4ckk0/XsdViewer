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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/** {@code POST /api/choose-folder}: the native folder chooser, answering the schemas of the folder and its sub-folders. 409 without a display. */
final class ChooseFolderHandler implements HttpHandler {

    private final ServedSchemaFiles files;

    ChooseFolderHandler(ServedSchemaFiles files) {
        this.files = files;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        if (!FileDialogs.available()) {
            HttpResponses.error(ex, HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
            return;
        }
        Path folder = FileDialogs.chooseFolder(Messages.get(MessageKey.DIALOG_OPEN_FOLDER));
        if (folder == null) {
            HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.CANCELLED, true));
            return;
        }
        SchemaFolder.Listing listing = SchemaFolder.list(folder);
        JsonWriter w = new JsonWriter(4096).beginObject().property(JsonKey.FOLDER, folder.toString());
        w.name(JsonKey.FILES).beginArray();
        for (Path p : listing.files()) files.writeFile(w, p);
        w.endArray().property(JsonKey.TRUNCATED, listing.truncated());
        HttpResponses.json(ex, HttpStatus.OK, w.endObject().toString());
    }
}
