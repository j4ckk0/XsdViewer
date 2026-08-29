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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaFolderTest {

    @Test
    void listsSchemasRecursivelySortedSkippingHiddenAndOtherFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("b.xsd"), "");
        Files.writeString(dir.resolve("A.XSD"), "");
        Files.writeString(dir.resolve("notes.txt"), "");
        Files.createDirectories(dir.resolve("sub/deeper"));
        Files.writeString(dir.resolve("sub/deeper/c.xsd"), "");
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git/hidden.xsd"), "");
        SchemaFolder.Listing l = SchemaFolder.list(dir);
        assertEquals(List.of(dir.resolve("A.XSD"), dir.resolve("b.xsd"), dir.resolve("sub/deeper/c.xsd")), l.files());
        assertFalse(l.truncated());
    }

    @Test
    void sampleFolder() throws Exception {
        SchemaFolder.Listing l = SchemaFolder.list(Path.of("samples/compare"));
        assertEquals(8, l.files().size());
        assertTrue(l.files().get(0).endsWith(Path.of("v1", "catalog.xsd")));
    }
}
