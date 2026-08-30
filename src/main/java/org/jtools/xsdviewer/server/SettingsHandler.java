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
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * {@code /api/settings}: the Settings menu. {@code GET} answers {@code {"autoStop": bool}};
 * {@code POST} the same shape applies it at once ({@link PageWatch#setEnabled}) and keeps it
 * for the next runs, then answers the new state.
 */
final class SettingsHandler implements HttpHandler {

    private final PageWatch pages;
    private final Consumer<Boolean> persistAutoStop;

    SettingsHandler(PageWatch pages, Consumer<Boolean> persistAutoStop) {
        this.pages = pages;
        this.persistAutoStop = persistAutoStop;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (HttpMethod.POST.equals(ex.getRequestMethod())) {
            Object autoStop;
            try {
                Map<String, Object> body = JsonReader.asObject(JsonReader.parse(HttpResponses.readBody(ex)));
                autoStop = body == null ? null : body.get(JsonKey.AUTO_STOP);
            } catch (RuntimeException malformed) {   // not JSON, not an object: answered as a bad request below
                autoStop = null;
            }
            if (!(autoStop instanceof Boolean value)) {
                HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.BAD_SETTINGS));
                return;
            }
            pages.setEnabled(value);
            persistAutoStop.accept(value);
        }
        HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.AUTO_STOP, pages.isEnabled()));
    }
}
