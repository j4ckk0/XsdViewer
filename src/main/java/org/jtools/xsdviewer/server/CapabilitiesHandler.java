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
import org.jtools.xsdviewer.BuildInfo;
import org.jtools.xsdviewer.Log;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

import java.util.Locale;

/** {@code GET /api/capabilities}: what the page adapts to — native dialogs available, the machine's language, the versions for Help ▸ About. */
final class CapabilitiesHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpResponses.json(ex, HttpStatus.OK, new JsonWriter().beginObject()
                .property(JsonKey.DIALOGS, FileDialogs.available())
                .property(JsonKey.LANGUAGE, Locale.getDefault().getLanguage())
                .property(JsonKey.VERSION, BuildInfo.version())
                .property(JsonKey.JAVA_VERSION, BuildInfo.javaVersion())
                .property(JsonKey.LOG_FILE, Log.file() == null ? "" : Log.file().toString())
                .endObject().toString());
    }
}
