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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;

import java.util.prefs.Preferences;

import org.jtools.xsdviewer.UserSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The HTTP interface end to end, on an ephemeral port, with the sample schema as initial file. */
class XsdViewerServerTest {

    private static XsdViewerServer server;
    private static final HttpClient client = HttpClient.newHttpClient();

    /** The settings the test changes go to a preferences node of their own, removed afterwards. */
    private static final String TEST_PREFERENCES = "org/jtools/xsdviewer/test";

    @BeforeAll
    static void start() throws Exception {
        System.setProperty(UserSettings.NODE_PROPERTY, TEST_PREFERENCES);
        server = XsdViewerServer.start("127.0.0.1", 0, Path.of("samples/purchaseOrder.xsd"), false);
    }

    @AfterAll
    static void stop() throws Exception {
        server.stop();
        Preferences.userRoot().node(TEST_PREFERENCES).removeNode();
        System.clearProperty(UserSettings.NODE_PROPERTY);
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
    void aliveIsAnEventStreamAndByeAnswers() throws Exception {
        assertEquals(400, get("/api/alive").statusCode());
        HttpResponse<InputStream> alive = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/alive?id=t1")).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, alive.statusCode());
        assertEquals(AliveHandler.CONTENT_TYPE, alive.headers().firstValue("Content-Type").orElse(""));
        try (InputStream in = alive.body()) {
            byte[] first = in.readNBytes(8);      // the first ping arrives at once
            assertEquals(": ping\n\n", new String(first, java.nio.charset.StandardCharsets.UTF_8));
        }
        HttpResponse<String> bye = post("/api/bye?id=t1", "");
        assertEquals(200, bye.statusCode());
        assertEquals("{\"ok\":true}", bye.body());
        assertEquals(405, get("/api/bye?id=t1").statusCode());
    }

    @Test
    void settingsAreAnsweredAppliedAndKept() throws Exception {
        assertEquals("{\"autoStop\":false}", get("/api/settings").body());        // started with stopWhenNoPage = false
        HttpResponse<String> on = post("/api/settings", "{\"autoStop\": true}");
        assertEquals(200, on.statusCode());
        assertEquals("{\"autoStop\":true}", on.body());
        assertTrue(UserSettings.autoStop(), "kept in the preferences");
        assertEquals("{\"autoStop\":true}", get("/api/settings").body());
        assertEquals(400, post("/api/settings", "{\"autoStop\": \"yes\"}").statusCode());
        assertEquals(400, post("/api/settings", "nonsense").statusCode());
        post("/api/settings", "{\"autoStop\": false}");
        assertFalse(UserSettings.autoStop());
    }

    @Test
    void validatesAgainstServedSchemasOnly() throws Exception {
        String xsd = Path.of("samples/purchaseOrder.xsd").toAbsolutePath().toString();
        String sch = Path.of("samples/schematron/purchaseOrder.sch").toAbsolutePath().toString();
        String xml = Files.readString(Path.of("samples/purchaseOrder.xml"));
        assertEquals(400, post("/api/validate", xml).statusCode(), "no schema named");
        assertEquals(404, post("/api/validate?schematron=" + sch, xml).statusCode(), "not served yet");
        get("/api/initial");
        get("/api/open?base=" + xsd + "&location=schematron/purchaseOrder.sch&strict=true");   // now served
        String both = post("/api/validate?schema=" + xsd + "&schematron=" + sch, xml).body();
        assertTrue(both.startsWith("{\"valid\":true,\"problems\":[],\"truncated\":false,\"phases\":[\"basic\",\"full\"],\"phase\":\"full\",\"checked\":14}"), both);
        String bad = post("/api/validate?schematron=" + sch + "&phase=basic", xml.replace("<po:quantity>1</po:quantity>", "<po:quantity>120</po:quantity>")).body();
        assertTrue(bad.contains("\"valid\":false"), bad);
        assertTrue(bad.contains("\"source\":\"schematron\",\"severity\":\"error\",\"line\":24,\"column\":5,\"message\":\"At most 99 of Lawnmower per line.\",\"location\":\"/po:purchaseOrder/po:items/po:item[1]\",\"assertion\":\"assert:structure/po:item/po:quantity < 100\",\"rule\":\"rule:structure/po:item\",\"pattern\":\"pattern:structure\",\"test\":\"po:quantity < 100\"}"), bad);
        assertTrue(bad.endsWith("\"phase\":\"basic\",\"checked\":6}"), bad);
        assertEquals(400, post("/api/validate?schematron=" + sch + "&phase=nope", xml).statusCode());
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
