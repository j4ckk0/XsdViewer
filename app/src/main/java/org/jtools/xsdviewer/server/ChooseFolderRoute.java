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

import org.jtools.framework.desktop.FileDialogs;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

import org.jtools.framework.server.Exchange;
import org.jtools.framework.server.WebServer;
import org.jtools.framework.support.Json;

/** {@code POST /api/choose-folder}: the native folder chooser, answering the schemas of the folder and its sub-folders. 409 without a display. */
final class ChooseFolderRoute implements WebServer.Route {

    private final ServedSchemaFiles files;

    ChooseFolderRoute(ServedSchemaFiles files) {
        this.files = files;
    }

    @Override
    public Object answer(Exchange ex) throws IOException {
        if (!FileDialogs.available()) throw new WebServer.Refusal(HttpStatus.CONFLICT, Messages.get(MessageKey.NO_DISPLAY));
        Path folder = FileDialogs.chooseFolder(Messages.get(MessageKey.DIALOG_OPEN_FOLDER));
        if (folder == null) return Json.raw(JsonWriter.object(JsonKey.CANCELLED, true));
        SchemaFolder.Listing listing = SchemaFolder.list(folder);
        JsonWriter w = new JsonWriter(4096).beginObject()
                .property(JsonKey.FOLDER, folder.toString())
                .property(JsonKey.NAME, folder.getFileName() == null ? folder.toString() : folder.getFileName().toString());
        w.name(JsonKey.FILES).beginArray();
        for (Path p : listing.files()) files.writeFile(w, p);
        w.endArray().property(JsonKey.TRUNCATED, listing.truncated());
        return Json.raw(w.endObject().toString());
    }
}
