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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaTextTest {

    private static final String LATIN = "<xs:schema><!-- caf\u00e9 --></xs:schema>";

    @Test
    void utf8WithAndWithoutBom() {
        byte[] utf8 = LATIN.getBytes(StandardCharsets.UTF_8);
        assertEquals(LATIN, SchemaText.decode(utf8));
        byte[] withBom = new byte[utf8.length + 3];
        withBom[0] = (byte) 0xEF; withBom[1] = (byte) 0xBB; withBom[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, withBom, 3, utf8.length);
        assertEquals(LATIN, SchemaText.decode(withBom));
    }

    @Test
    void latin1DeclaredOrNot() {
        String declared = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + LATIN;
        assertEquals(declared, SchemaText.decode(declared.getBytes(StandardCharsets.ISO_8859_1)));
        assertEquals(LATIN, SchemaText.decode(LATIN.getBytes(StandardCharsets.ISO_8859_1)), "no declaration: ISO-8859-1 decodes anything");
        String cp1252 = "<?xml version=\"1.0\" encoding=\"windows-1252\"?><a>\u20ac</a>";
        assertEquals(cp1252, SchemaText.decode(cp1252.getBytes(java.nio.charset.Charset.forName("windows-1252"))));
    }

    @Test
    void utf16WithBom() {
        byte[] bytes = ("\uFEFF" + LATIN).getBytes(StandardCharsets.UTF_16BE);
        assertEquals(LATIN, SchemaText.decode(bytes));
    }

    @Test
    void readsAFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("latin.xsd");
        Files.write(f, LATIN.getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(LATIN, SchemaText.read(f));
    }
}
