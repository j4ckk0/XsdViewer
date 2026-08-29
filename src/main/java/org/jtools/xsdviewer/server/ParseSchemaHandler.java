package org.jtools.xsdviewer.server;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.schema.XsdParser;

/** {@code POST /api/parse}, body = schema text: answers the JSON graph, or 400 with the parse error. */
final class ParseSchemaHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        String text = HttpResponses.readBody(ex);
        try {
            HttpResponses.json(ex, HttpStatus.OK, XsdParser.parse(text).toJson());
        } catch (Exception e) {
            HttpResponses.error(ex, HttpStatus.BAD_REQUEST, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
