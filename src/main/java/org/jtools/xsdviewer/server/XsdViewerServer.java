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
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jtools.xsdviewer.Log;
import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.jtools.xsdviewer.UserSettings;

/** The HTTP server: the page under {@code /}, the API under {@code /api/*} (one handler per {@link ApiPath}); virtual threads, answers in the language the page sends. */
public final class XsdViewerServer {

    private static final String URL_SCHEME = "http://";
    private static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
    /** Seconds given to in-flight exchanges when stopping (the quit answer itself, mostly). */
    private static final int STOP_DELAY_SECONDS = 1;

    private final HttpServer server;
    private final String url;

    private XsdViewerServer(HttpServer server, String host) {
        this.server = server;
        this.url = URL_SCHEME + host + ':' + server.getAddress().getPort() + ApiPath.ROOT;
    }

    /**
     * Binds to {@code host:port} (port 0 for an ephemeral one) and starts serving.
     *
     * @param stopWhenNoPage exit the process once every page has been closed for {@link PageWatch#GRACE}
     *                       (false with {@code --keep-alive}); the Settings menu can change it later
     */
    public static XsdViewerServer start(String host, int port, Path initialFile, boolean stopWhenNoPage) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(host, port), 0);
        XsdViewerServer server = new XsdViewerServer(http, host);
        ServedSchemaFiles files = new ServedSchemaFiles();
        PageWatch pages = new PageWatch();
        http.createContext(ApiPath.PARSE, localized(new ParseSchemaHandler()));
        http.createContext(ApiPath.INITIAL, localized(new InitialFileHandler(files, initialFile)));
        http.createContext(ApiPath.OPEN, localized(new OpenSchemaLocationHandler(files)));
        http.createContext(ApiPath.LOCATE, localized(new LocateSchemaFileHandler(files, new SchemaFileFinder())));
        http.createContext(ApiPath.QUIT, localized(new QuitHandler(server::stopAndExit)));
        http.createContext(ApiPath.ALIVE, localized(new AliveHandler(pages)));
        http.createContext(ApiPath.BYE, localized(new ByeHandler(pages)));
        http.createContext(ApiPath.SETTINGS, localized(new SettingsHandler(pages, UserSettings::setAutoStop)));
        http.createContext(ApiPath.CAPABILITIES, localized(new CapabilitiesHandler()));
        http.createContext(ApiPath.CHOOSE, localized(new ChooseFilesHandler(files)));
        http.createContext(ApiPath.CHOOSE_FOLDER, localized(new ChooseFolderHandler(files)));
        http.createContext(ApiPath.WORKSPACE_SAVE, localized(new SaveWorkspaceHandler()));
        http.createContext(ApiPath.WORKSPACE_OPEN, localized(new OpenWorkspaceHandler(files)));
        http.createContext(ApiPath.ROOT, localized(new StaticResourceHandler()));
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        pages.setEnabled(stopWhenNoPage);
        pages.watch(server::stopAndExit);
        return server;
    }

    /** The handler with the request's {@code Accept-Language} applied to {@link Messages}, and a failure logged and answered as a 500 instead of a dropped connection. */
    private static HttpHandler localized(HttpHandler handler) {
        return ex -> {
            Messages.setRequestLocale(Messages.localeOf(ex.getRequestHeaders().getFirst(ACCEPT_LANGUAGE_HEADER)));
            try {
                handler.handle(ex);
            } catch (Exception | Error e) {
                Log.warn(Messages.get(MessageKey.REQUEST_FAILED, ex.getRequestMethod(), ex.getRequestURI()), e);
                HttpResponses.error(ex, HttpStatus.INTERNAL_ERROR, Messages.get(MessageKey.INTERNAL_ERROR, String.valueOf(e)));
            } finally {
                Messages.clearRequestLocale();
            }
        };
    }

    /** The address of the page, e.g. {@code http://127.0.0.1:8080/}. */
    public String url() {
        return url;
    }

    public void stop() {
        server.stop(STOP_DELAY_SECONDS);
    }

    /** Stops the server from another thread (so that the current exchange completes) and exits the JVM. */
    private void stopAndExit() {
        Thread.ofPlatform().start(() -> {
            stop();
            System.exit(0);
        });
    }
}
