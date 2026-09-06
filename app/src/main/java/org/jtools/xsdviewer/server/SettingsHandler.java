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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;

/**
 * {@code /api/settings}: the Settings menu, kept for the next runs. {@code GET} answers
 * {@code {"autoStop": bool, "port": n}} ({@code port} 0 when none was chosen: the default stands).
 * {@code POST} of the same shape applies and keeps each field it carries — {@code autoStop} at once
 * ({@link PageWatch#setEnabled}), {@code port} for the next start (a server does not change the port
 * it already listens on) — then answers the new state. Both fields are optional; a malformed body,
 * or a port outside 1..65535, is a bad request.
 */
final class SettingsHandler implements HttpHandler {

    private static final int MIN_PORT = 1, MAX_PORT = 65535;

    private final PageWatch pages;
    private final Consumer<Boolean> persistAutoStop;
    private final IntSupplier port;
    private final IntConsumer persistPort;

    SettingsHandler(PageWatch pages, Consumer<Boolean> persistAutoStop, IntSupplier port, IntConsumer persistPort) {
        this.pages = pages;
        this.persistAutoStop = persistAutoStop;
        this.port = port;
        this.persistPort = persistPort;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (HttpMethod.POST.equals(ex.getRequestMethod())) {
            Map<String, Object> body;
            try {
                body = JsonReader.asObject(JsonReader.parse(HttpResponses.readBody(ex)));
            } catch (RuntimeException malformed) {   // not JSON, not an object
                body = null;
            }
            if (body == null) {
                HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.BAD_SETTINGS));
                return;
            }
            if (!applied(ex, body)) {
                return;   // a bad field: the bad request was already answered
            }
        }
        HttpResponses.json(ex, HttpStatus.OK, new JsonWriter().beginObject()
                .property(JsonKey.AUTO_STOP, pages.isEnabled())
                .property(JsonKey.PORT, port.getAsInt())
                .endObject().toString());
    }

    /** Applies the fields present in {@code body}; false (a bad request already answered) when one is malformed. */
    private boolean applied(HttpExchange ex, Map<String, Object> body) throws IOException {
        if (body.containsKey(JsonKey.AUTO_STOP)) {
            if (!(body.get(JsonKey.AUTO_STOP) instanceof Boolean value)) {
                HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.BAD_SETTINGS));
                return false;
            }
            pages.setEnabled(value);
            persistAutoStop.accept(value);
        }
        if (body.containsKey(JsonKey.PORT)) {
            int chosen = body.get(JsonKey.PORT) instanceof Number n ? n.intValue() : -1;
            if (chosen != 0 && (chosen < MIN_PORT || chosen > MAX_PORT)) {   // 0 clears the choice back to the default
                HttpResponses.error(ex, HttpStatus.BAD_REQUEST, Messages.get(MessageKey.INVALID_PORT, String.valueOf(body.get(JsonKey.PORT))));
                return false;
            }
            persistPort.accept(chosen);
        }
        return true;
    }
}
