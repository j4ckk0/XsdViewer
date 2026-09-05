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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import org.jtools.xsdviewer.UserSettings;
import org.jtools.xsdviewer.json.JsonReader;
import org.jtools.xsdviewer.json.JsonWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The model and comparison API end to end, on the two versions of the catalog sample: what the page
 * asks of the server and what a program using the API alone gets back.
 */
class CompareApiTest {

    private static XsdViewerServer server;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final String TEST_PREFERENCES = "org/jtools/xsdviewer/test-compare";
    private static final Path V1 = Path.of("samples/compare/v1"), V2 = Path.of("samples/compare/v2");
    private static final List<String> V1_FILES = List.of("catalog.xsd", "common.xsd", "product.xsd", "supplier.xsd");
    private static final List<String> V2_FILES = List.of("catalog.xsd", "common.xsd", "product.xsd", "shipping.xsd");

    @BeforeAll
    static void start() throws Exception {
        System.setProperty(UserSettings.NODE_PROPERTY, TEST_PREFERENCES);
        server = XsdViewerServer.start("127.0.0.1", 0, null, false);
    }

    @AfterAll
    static void stop() throws Exception {
        server.stop();
        Preferences.userRoot().node(TEST_PREFERENCES).removeNode();
        System.clearProperty(UserSettings.NODE_PROPERTY);
    }

    private static HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.url() + path.substring(1)))
                .header("Accept-Language", "en").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, Object> object(HttpResponse<String> r) {
        assertEquals(200, r.statusCode(), r.body());
        return JsonReader.asObject(JsonReader.parse(r.body()));
    }

    /** {@code "files": [{name, text}...]} for a version's files, written into {@code w}. */
    private static void files(JsonWriter w, Path dir, List<String> names) throws Exception {
        w.name("files").beginArray();
        for (String n : names) w.beginObject().property("name", n).property("text", Files.readString(dir.resolve(n))).endObject();
        w.endArray();
    }

    /** One side of a declaration request: the version's files, product.xsd as home, the declaration. */
    private static void side(JsonWriter w, Path dir, List<String> names, String id) throws Exception {
        w.beginObject();
        files(w, dir, names);
        w.property("home", names.indexOf("product.xsd")).property("id", id).endObject();
    }

    private static int boxes(Object tree) {
        if (tree == null) return 0;
        Map<String, Object> b = JsonReader.asObject(tree);
        int n = 1;
        for (Object c : JsonReader.asArray(b.get("attributes"))) n += boxes(c);
        for (Object c : JsonReader.asArray(b.get("children"))) n += boxes(c);
        return n;
    }

    @Test
    void theModelOfADeclarationWithItsBoxesOpenedOnDemand() throws Exception {
        JsonWriter w = new JsonWriter().beginObject();
        files(w, V2, V2_FILES);
        w.property("home", V2_FILES.indexOf("product.xsd")).property("id", "complexType:ProductType");
        w.name("expanded").beginArray().endArray().endObject();
        Map<String, Object> tree = object(post(ApiPath.MODEL, w.toString()));
        assertEquals("ProductType", tree.get("name"));
        assertEquals(true, tree.get("root"));
        assertEquals(10, boxes(tree), "the type, two attributes, the sequence and its six elements; weight's type stays folded");

        w = new JsonWriter().beginObject();
        files(w, V2, V2_FILES);
        w.property("home", V2_FILES.indexOf("product.xsd")).property("id", "complexType:ProductType").property("openAll", true).endObject();
        assertEquals(12, boxes(object(post(ApiPath.MODEL, w.toString()))), "weight opened: its unit attribute and its base type too");
    }

    @Test
    void anUnknownDeclarationIsRefused() throws Exception {
        JsonWriter w = new JsonWriter().beginObject();
        files(w, V2, V2_FILES);
        w.property("home", 2).property("id", "complexType:Nothing").endObject();
        assertEquals(400, post(ApiPath.MODEL, w.toString()).statusCode());
        assertEquals(400, post(ApiPath.MODEL, "not json").statusCode());
    }

    @Test
    void twoDeclarationsComparedTheirBoxesMarkedAndTheLinksOnlyOneSideHas() throws Exception {
        JsonWriter w = new JsonWriter().beginObject();
        w.name("left");
        side(w, V1, V1_FILES, "complexType:ProductType");
        w.name("right");
        side(w, V2, V2_FILES, "complexType:ProductType");
        Map<String, Object> r = object(post(ApiPath.COMPARE_DECLARATIONS, w.endObject().toString()));
        assertEquals(10, boxes(r.get("left")));
        assertEquals(12, boxes(r.get("right")));
        Map<String, Object> counts = JsonReader.asObject(r.get("counts"));
        assertEquals(1, JsonReader.asInt(counts.get("removed"), -1), "legacyCode");
        assertEquals(3, JsonReader.asInt(counts.get("added"), -1), "weight, its unit, its base type");
        assertEquals(3, JsonReader.asInt(counts.get("changed"), -1), "description, tag, category");
        assertEquals("changed", JsonReader.asObject(JsonReader.asArray(JsonReader.asObject(r.get("left")).get("attributes")).get(1)).get("diff"), "@category");
        Map<String, Object> links = JsonReader.asObject(r.get("links"));
        assertEquals(4, JsonReader.asArray(links.get("onlyLeft")).size(), "description 0..1, legacyCode, tag 0..*, category 0..1");
        assertEquals(4, JsonReader.asArray(links.get("onlyRight")).size(), "description 1, weight, tag 0..10, category 1");
    }

    @Test
    void aSideWithoutTheDeclarationIsWhollyOneSided() throws Exception {
        JsonWriter w = new JsonWriter().beginObject();
        w.name("left");
        side(w, V1, V1_FILES, "complexType:ProductType");
        w.name("right");
        side(w, V2, V2_FILES, "complexType:Gone");
        Map<String, Object> r = object(post(ApiPath.COMPARE_DECLARATIONS, w.endObject().toString()));
        assertEquals(null, r.get("right"));
        assertEquals(10, JsonReader.asInt(JsonReader.asObject(r.get("counts")).get("removed"), -1));
    }

    @Test
    void twoTextsComparedLineByLine() throws Exception {
        String left = Files.readString(V1.resolve("product.xsd")), right = Files.readString(V2.resolve("product.xsd"));
        Map<String, Object> r = object(post(ApiPath.COMPARE_TEXTS, new JsonWriter().beginObject().property("left", left).property("right", right).property("businessOnly", true).endObject().toString()));
        List<Object> la = JsonReader.asArray(r.get("la")), ops = JsonReader.asArray(r.get("ops"));
        assertTrue(la.size() < left.split("\n").length, "comments, the schema tag and the import are not compared");
        assertEquals(11, JsonReader.asInt(JsonReader.asObject(la.get(0)).get("n"), -1), "the first business line of v1: the product element, on line 11 of the file");
        assertFalse(ops.isEmpty());
        assertEquals(false, r.get("onlyMoves"));

        Map<String, Object> shaped = object(post(ApiPath.COMPARE_TEXTS,
                new JsonWriter().beginObject().property("left", "<a>\n  <b/>\n</a>").property("right", "    <a>\n<b/>\n    </a>").property("ignoreSpacing", true).endObject().toString()));
        List<Object> shapedOps = JsonReader.asArray(shaped.get("ops"));
        assertEquals(3, shapedOps.size());
        for (Object o : shapedOps) assertEquals("=", JsonReader.asObject(o).get("op"));
        assertEquals("    <a>", JsonReader.asObject(JsonReader.asArray(shaped.get("lb")).get(0)).get("text"), "the lines are answered as written");
    }

    @Test
    void whatTwoSchemasDeclareThatTheOtherDoesNot() throws Exception {
        String left = Files.readString(V1.resolve("product.xsd")), right = Files.readString(V2.resolve("product.xsd"));
        Map<String, Object> r = object(post(ApiPath.COMPARE_SCHEMAS, new JsonWriter().beginObject().property("left", left).property("right", right).endObject().toString()));
        assertEquals(true, r.get("schemas"));
        assertEquals(false, r.get("same"));
        List<String> onlyRight = new ArrayList<>();
        for (Object n : JsonReader.asArray(r.get("nodesOnlyRight"))) onlyRight.add((String) JsonReader.asObject(n).get("id"));
        assertEquals(List.of("complexType:Weight", "simpleType:Unit"), onlyRight);
        assertEquals(false, object(post(ApiPath.COMPARE_SCHEMAS, new JsonWriter().beginObject().property("left", "<a/>").property("right", right).endObject().toString())).get("schemas"));
    }

    @Test
    void theFilesOfTwoWorkspacesPairedByName() throws Exception {
        JsonWriter w = new JsonWriter().beginObject();
        w.name("left").beginArray();
        for (String n : V1_FILES) w.beginObject().property("name", n).property("text", Files.readString(V1.resolve(n))).endObject();
        w.endArray().name("right").beginArray();
        for (String n : V2_FILES) w.beginObject().property("name", n).property("text", Files.readString(V2.resolve(n))).endObject();
        w.endArray().property("businessOnly", true).endObject();
        Map<String, Object> r = object(post(ApiPath.COMPARE_WORKSPACES, w.toString()));
        List<String> statuses = new ArrayList<>();
        for (Object p : JsonReader.asArray(r.get("pairs"))) {
            Map<String, Object> pair = JsonReader.asObject(p);
            statuses.add(pair.get("name") + ":" + pair.get("status"));
        }
        assertEquals(List.of("catalog.xsd:same", "common.xsd:same", "product.xsd:different", "shipping.xsd:only-right", "supplier.xsd:only-left"), statuses,
                "catalog.xsd changed only its documentation and layout, which the business lines leave out");
        assertNotNull(r.get("pairs"));
    }
}
