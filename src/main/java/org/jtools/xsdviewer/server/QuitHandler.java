package org.jtools.xsdviewer.server;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.json.JsonWriter;

/** {@code POST /api/quit}: File ▸ Quit in the page. Answers {@code {"ok":true}}, then stops the server. */
final class QuitHandler implements HttpHandler {

    private final Runnable shutdown;

    QuitHandler(Runnable shutdown) {
        this.shutdown = shutdown;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!HttpResponses.requirePost(ex)) return;
        HttpResponses.json(ex, HttpStatus.OK, JsonWriter.object(JsonKey.OK, true));
        System.out.println(Messages.get(MessageKey.SERVER_QUIT_REQUESTED));
        shutdown.run();
    }
}
