package org.jtools.xsdviewer.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

/**
 * The HTTP server of the tool: the static page under {@code /} and the API under {@code /api/*}
 * (one handler class per path, see {@link ApiPath}). Requests run on virtual threads.
 */
public final class XsdViewerServer {

    private static final String URL_SCHEME = "http://";
    /** Seconds given to in-flight exchanges when stopping (the quit answer itself, mostly). */
    private static final int STOP_DELAY_SECONDS = 1;

    private final HttpServer server;
    private final String url;

    private XsdViewerServer(HttpServer server, String host) {
        this.server = server;
        this.url = URL_SCHEME + host + ':' + server.getAddress().getPort() + ApiPath.ROOT;
    }

    /** Binds to {@code host:port} (port 0 for an ephemeral one) and starts serving. */
    public static XsdViewerServer start(String host, int port, Path initialFile) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(host, port), 0);
        XsdViewerServer server = new XsdViewerServer(http, host);
        ServedSchemaFiles files = new ServedSchemaFiles();
        http.createContext(ApiPath.PARSE, new ParseSchemaHandler());
        http.createContext(ApiPath.INITIAL, new InitialFileHandler(files, initialFile));
        http.createContext(ApiPath.OPEN, new OpenSchemaLocationHandler(files));
        http.createContext(ApiPath.LOCATE, new LocateSchemaFileHandler(files, new SchemaFileFinder()));
        http.createContext(ApiPath.QUIT, new QuitHandler(server::stopAndExit));
        http.createContext(ApiPath.CAPABILITIES, new CapabilitiesHandler());
        http.createContext(ApiPath.CHOOSE, new ChooseFilesHandler(files));
        http.createContext(ApiPath.WORKSPACE_SAVE, new SaveWorkspaceHandler());
        http.createContext(ApiPath.WORKSPACE_OPEN, new OpenWorkspaceHandler(files));
        http.createContext(ApiPath.ROOT, new StaticResourceHandler());
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        return server;
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
