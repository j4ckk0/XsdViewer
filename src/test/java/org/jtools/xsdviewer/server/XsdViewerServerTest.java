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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The HTTP interface end to end, on an ephemeral port, with the sample schema as initial file. */
class XsdViewerServerTest {

    private static XsdViewerServer server;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        server = XsdViewerServer.start("127.0.0.1", 0, Path.of("samples/purchaseOrder.xsd"));
    }

    @AfterAll
    static void stop() {
        server.stop();
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path.substring(1))).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path.substring(1)))
                .header("Accept-Language", "en").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void staticFilesAndTheirTypes() throws Exception {
        HttpResponse<String> page = get("/");
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(page.body().contains("js/app.js"));
        assertEquals("text/javascript; charset=utf-8", get("/js/app.js").headers().firstValue("Content-Type").orElse(""));
        assertEquals("application/json", get("/i18n/en.json").headers().firstValue("Content-Type").orElse(""));
        assertEquals(404, get("/nope.js").statusCode());
    }

    @Test
    void pathsOutsideTheWebFolderAreRefused() throws Exception {
        assertEquals(404, get("/..%2Findex.html").statusCode());
        assertEquals(404, get("/js/..%2F..%2Findex.html").statusCode());
    }

    @Test
    void parseAnswersTheGraphOrTheError() throws Exception {
        HttpResponse<String> ok = post("/api/parse", "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"a\" type=\"xs:string\"/></xs:schema>");
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"id\":\"element:a\""));
        HttpResponse<String> bad = post("/api/parse", "<root/>");
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().startsWith("{\"error\":\"Root element is <root>"), bad.body());
        assertEquals(405, get("/api/parse").statusCode());
    }

    @Test
    void initialFileAndCapabilities() throws Exception {
        HttpResponse<String> initial = get("/api/initial");
        assertEquals(200, initial.statusCode());
        assertTrue(initial.body().startsWith("{\"name\":\"purchaseOrder.xsd\""));
        HttpResponse<String> caps = get("/api/capabilities");
        assertEquals(200, caps.statusCode());
        assertTrue(caps.body().contains("\"dialogs\":") && caps.body().contains("\"version\":"), caps.body());
    }

    @Test
    void openResolvesRelativeToAServedFileOnly() throws Exception {
        String base = Path.of("samples/purchaseOrder.xsd").toAbsolutePath().toString();
        get("/api/initial");   // makes the initial file a served file
        assertEquals(200, get("/api/open?base=" + base + "&location=import/order.xsd&strict=true").statusCode());
        assertEquals(404, get("/api/open?base=&location=import/order.xsd&strict=true").statusCode());
        assertEquals(400, get("/api/open?base=&location=http://example.com/x.xsd").statusCode());
    }
}
