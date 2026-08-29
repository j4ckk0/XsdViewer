package org.jtools.xsdviewer.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonReaderTest {

    @Test
    void readsEveryValueType() {
        Map<String, Object> o = JsonReader.asObject(JsonReader.parse(
                " { \"s\": \"a\\\"b\\n\\u00e9\", \"i\": -12, \"d\": 1.5e2, \"t\": true, \"f\": false, \"n\": null, \"a\": [1, \"x\", {}], \"o\": {\"k\": []} } "));
        assertEquals("a\"b\né", o.get("s"));
        assertEquals(-12L, o.get("i"));
        assertEquals(150.0, o.get("d"));
        assertEquals(Boolean.TRUE, o.get("t"));
        assertEquals(Boolean.FALSE, o.get("f"));
        assertNull(o.get("n"));
        assertEquals(List.of(1L, "x", Map.of()), o.get("a"));
        assertEquals(Map.of("k", List.of()), o.get("o"));
        assertEquals(List.of("s", "i", "d", "t", "f", "n", "a", "o"), List.copyOf(o.keySet()));
    }

    @Test
    void roundTripsTheWriter() {
        String json = new JsonWriter().beginObject().property("p", "x\ty\"z\\").name("l").beginArray().value(3).value(true).endArray().endObject().toString();
        Map<String, Object> o = JsonReader.asObject(JsonReader.parse(json));
        assertEquals("x\ty\"z\\", o.get("p"));
        assertEquals(List.of(3L, Boolean.TRUE), o.get("l"));
    }

    @Test
    void helpers() {
        assertNull(JsonReader.asObject("x"));
        assertNull(JsonReader.asArray("x"));
        assertNull(JsonReader.asString(1L));
        assertEquals(7, JsonReader.asInt("no", 7));
        assertEquals(3, JsonReader.asInt(3L, 7));
    }

    @Test
    void rejectsBrokenJson() {
        for (String bad : List.of("", "{", "[1,]", "{\"a\" 1}", "tru", "\"abc", "{\"a\":1} x", "01x", "\"\\q\"")) {
            assertThrows(IllegalArgumentException.class, () -> JsonReader.parse(bad), bad);
        }
    }
}
