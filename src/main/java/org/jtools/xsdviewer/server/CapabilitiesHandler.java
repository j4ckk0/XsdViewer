package org.jtools.xsdviewer.server;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/** {@code GET /api/capabilities}: {@code {"dialogs": bool}}, whether the server can show native file dialogs. */
final class CapabilitiesHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.DIALOGS, FileDialogs.available()));
    }
}
