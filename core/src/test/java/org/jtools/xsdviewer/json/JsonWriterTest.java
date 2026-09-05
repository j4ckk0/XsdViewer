package org.jtools.xsdviewer.json;

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
