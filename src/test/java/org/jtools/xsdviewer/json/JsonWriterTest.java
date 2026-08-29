package org.jtools.xsdviewer.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonWriterTest {

    @Test
    void stringsAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\\n\\r\\t\\u0001é\"", JsonStrings.quote("a\"b\\c\n\r\t\u0001é"));
        assertEquals("null", JsonStrings.quote(null));
    }

    @Test
    void nestedContainersGetTheirCommas() {
        String json = new JsonWriter().beginObject()
                .property("s", "x").property("i", 1).property("b", false)
                .name("a").beginArray().value("p").value(2).beginObject().endObject().endArray()
                .name("o").beginObject().property("k", "v").endObject()
                .endObject().toString();
        assertEquals("{\"s\":\"x\",\"i\":1,\"b\":false,\"a\":[\"p\",2,{}],\"o\":{\"k\":\"v\"}}", json);
    }

    @Test
    void singlePropertyObjects() {
        assertEquals("{\"error\":\"boom\"}", JsonWriter.object("error", "boom"));
        assertEquals("{\"ok\":true}", JsonWriter.object("ok", true));
    }
}
