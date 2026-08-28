package org.jtools.xsdviewer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Starts the HTTP server: serves the web UI from the classpath and exposes
 * {@code POST /api/parse} which turns XSD text into the JSON graph model.
 *
 * <pre>
 *   java -jar xsdviewer.jar [--port N] [--host H] [--no-browser] [file.xsd]
 * </pre>
 */
public final class Main {

    private static Path initialFile;
    private static HttpServer server;
    /** Files this server has read and handed to the page; /api/open only resolves locations relative to one of them. */
    private static final Set<Path> knownFiles = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String host = "127.0.0.1";
        boolean openBrowser = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--host" -> host = args[++i];
                case "--no-browser" -> openBrowser = false;
                case "-h", "--help" -> {
                    System.out.println("usage: java -jar xsdviewer.jar [--port N] [--host H] [--no-browser] [file.xsd]");
                    return;
                }
                default -> initialFile = Path.of(args[i]);
            }
        }
        if (initialFile != null && !Files.isRegularFile(initialFile)) {
            System.err.println("Not a file: " + initialFile);
            System.exit(1);
        }

        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/parse", Main::handleParse);
        server.createContext("/api/initial", Main::handleInitial);
        server.createContext("/api/open", Main::handleOpen);
        server.createContext("/api/locate", Main::handleLocate);
        server.createContext("/api/quit", Main::handleQuit);
        server.createContext("/", Main::handleStatic);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        String url = "http://" + host + ":" + server.getAddress().getPort() + "/";
        System.out.println("XsdViewer listening on " + url + "  (Ctrl+C to stop)");
        if (openBrowser) openBrowser(url);
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // fall through to xdg-open
        }
        try {
            new ProcessBuilder("xdg-open", url).start();
        } catch (IOException ignored) {
            // nothing more to try; the URL is printed on the console
        }
    }

    // ---- handlers -----------------------------------------------------------------------------

    private static void handleParse(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "POST expected");
            return;
        }
        String text;
        try (InputStream in = ex.getRequestBody()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            send(ex, 200, "application/json", XsdParser.parse(text).toJson());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            send(ex, 400, "application/json", "{\"error\":" + Json.string(msg) + "}");
        }
    }

    /** The file given on the command line, if any, so the page can open it at start-up. */
    private static void handleInitial(HttpExchange ex) throws IOException {
        if (initialFile == null) {
            send(ex, 404, "application/json", "{\"error\":\"no initial file\"}");
            return;
        }
        sendFile(ex, initialFile);
    }

    /**
     * {@code GET /api/open?base=<path>&location=<schemaLocation>}: the schema at {@code location},
     * so the page can follow a link into an imported / included file. The location is tried
     * relative to the directory of {@code base} (when it is a file this server already served),
     * then to the directories of all the files it served, then to the working directory: a file
     * opened from the browser has no known path, but its imports usually sit next to something
     * the server knows. Remote locations (http://...) are refused: the tool never goes on the network.
     */
    private static void handleOpen(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        String base = q.getOrDefault("base", ""), location = q.getOrDefault("location", "");
        if (location.isEmpty()) {
            send(ex, 400, "application/json", "{\"error\":\"location expected\"}");
            return;
        }
        if (location.contains("://")) {
            send(ex, 400, "application/json", "{\"error\":" + Json.string("remote schemaLocation not supported: " + location) + "}");
            return;
        }
        String rel = location.replace('\\', '/');
        List<Path> dirs = new ArrayList<>();
        if (!base.isEmpty()) {
            Path basePath = Path.of(base).toAbsolutePath().normalize();
            if (knownFiles.contains(basePath)) dirs.add(basePath.getParent());
        }
        for (Path f : knownFiles) dirs.add(f.getParent());
        dirs.add(Path.of("").toAbsolutePath());
        for (Path dir : dirs) {
            if (dir == null) continue;
            Path target = dir.resolve(rel).normalize();
            if (Files.isRegularFile(target)) {
                sendFile(ex, target);
                return;
            }
        }
        send(ex, 404, "application/json", "{\"error\":" + Json.string("file not found: " + location) + "}");
    }

    /**
     * {@code POST /api/locate?name=<file name>}, body = the file's text: finds where a file the
     * user opened in the browser (which hides its folder) is on disk, so that its imports can be
     * followed. Looks for a file with that name and the same content under the directories of the
     * files already served and the working directory (bounded walk), answering {@code {"path"}} or 404.
     */
    private static void handleLocate(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "POST expected");
            return;
        }
        String name = query(ex.getRequestURI().getRawQuery()).getOrDefault("name", "");
        String text;
        try (InputStream in = ex.getRequestBody()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
            send(ex, 400, "application/json", "{\"error\":\"file name expected\"}");
            return;
        }
        List<Path> roots = new ArrayList<>();
        for (Path f : knownFiles) if (f.getParent() != null && !roots.contains(f.getParent())) roots.add(f.getParent());
        roots.add(Path.of("").toAbsolutePath());
        String wanted = canonical(text);
        for (Path root : roots) {
            Path found = findFile(root, name, wanted);
            if (found != null) {
                knownFiles.add(found);
                send(ex, 200, "application/json", "{\"path\":" + Json.string(found.toString()) + "}");
                return;
            }
        }
        send(ex, 404, "application/json", "{\"error\":" + Json.string("no file " + name + " with this content found under " + roots) + "}");
    }

    private static final int LOCATE_MAX_DEPTH = 8, LOCATE_MAX_ENTRIES = 50_000;

    /** A regular file named {@code name} whose content is {@code wanted}, under {@code root}; hidden directories are skipped. */
    private static Path findFile(Path root, String name, String wanted) {
        int[] budget = { LOCATE_MAX_ENTRIES };
        try (var walk = Files.walk(root, LOCATE_MAX_DEPTH)) {
            var it = walk.filter(p -> {
                if (--budget[0] < 0) return false;
                return p.getFileName() != null && p.getFileName().toString().equals(name)
                        && !hidden(root, p) && Files.isRegularFile(p);
            }).iterator();
            while (it.hasNext()) {
                Path p = it.next();
                try {
                    if (canonical(Files.readString(p, StandardCharsets.UTF_8)).equals(wanted)) return p.toAbsolutePath().normalize();
                } catch (IOException | java.io.UncheckedIOException e) { /* unreadable or not UTF-8: not this one */ }
            }
        } catch (IOException | java.io.UncheckedIOException e) { /* unreadable directory */ }
        return null;
    }

    private static boolean hidden(Path root, Path p) {
        for (Path part : root.relativize(p)) if (part.toString().startsWith(".")) return true;
        return false;
    }

    /** Text without BOM and with LF line endings, so that a file compares equal however it was read. */
    private static String canonical(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        return text.replace("\r\n", "\n");
    }

    /** {@code POST /api/quit}: File ▸ Quit in the page. Answers, then stops the server and exits. */
    private static void handleQuit(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "POST expected");
            return;
        }
        send(ex, 200, "application/json", "{\"ok\":true}");
        System.out.println("Quit requested from the page, stopping");
        Thread.ofPlatform().start(() -> {
            server.stop(1);   // finish in-flight exchanges (1 s max), then exit
            System.exit(0);
        });
    }

    /** {@code {name, path, text}} of a schema file; remembers it as a valid base for /api/open. */
    private static void sendFile(HttpExchange ex, Path file) throws IOException {
        Path abs = file.toAbsolutePath().normalize();
        String text = Files.readString(abs, StandardCharsets.UTF_8);
        knownFiles.add(abs);
        send(ex, 200, "application/json",
                "{\"name\":" + Json.string(abs.getFileName().toString())
                        + ",\"path\":" + Json.string(abs.toString())
                        + ",\"text\":" + Json.string(text) + "}");
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> m = new HashMap<>();
        if (rawQuery == null) return m;
        for (String part : rawQuery.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            m.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..") || !path.matches("/[A-Za-z0-9._-]+")) {
            send(ex, 404, "text/plain", "not found");
            return;
        }
        try (InputStream in = Main.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                send(ex, 404, "text/plain", "not found");
                return;
            }
            byte[] body = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(path));
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type.startsWith("text/") ? type + "; charset=utf-8" : type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
