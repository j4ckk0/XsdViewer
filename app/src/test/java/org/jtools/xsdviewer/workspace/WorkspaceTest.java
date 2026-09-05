package org.jtools.xsdviewer.workspace;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class WorkspaceTest {

    private static final Path DIR = Path.of("/home/me/schemas").toAbsolutePath();
    private static final Path FILE = DIR.resolve("all" + Workspace.FILE_SUFFIX);

    @Test
    void pathsAreRelativeToTheWorkspaceFileWhenPossible() {
        Workspace ws = new Workspace(List.of(DIR.resolve("order.xsd"), DIR.resolve("common/types.xsd"), Path.of("/other/x.xsd").toAbsolutePath()), 1);
        String json = ws.toJson(FILE);
        assertTrue(json.startsWith("{\"xsdviewer\":1,\"files\":[\"order.xsd\",\"common/types.xsd\",\""), json);
        assertTrue(json.endsWith("\"],\"active\":1}"), json);
        assertTrue(json.contains("x.xsd"));
    }

    @Test
    void roundTrip() {
        Workspace ws = new Workspace(List.of(DIR.resolve("order.xsd"), DIR.resolve("common/types.xsd")), 1);
        Workspace back = Workspace.fromJson(ws.toJson(FILE), FILE);
        assertEquals(ws, back);
    }

    @Test
    void readsHandWrittenFilesAndDefaults() {
        Workspace ws = Workspace.fromJson("{ \"xsdviewer\": 1, \"files\": [\"a.xsd\", \"\", \"sub/../b.xsd\"] }", FILE);
        assertEquals(List.of(DIR.resolve("a.xsd"), DIR.resolve("b.xsd")), ws.files());
        assertEquals(0, ws.active());
        assertEquals(0, Workspace.fromJson("{\"xsdviewer\":1,\"files\":[\"a.xsd\"],\"active\":9}", FILE).active());
    }

    @Test
    void recognisesWorkspaces() {
        assertTrue(Workspace.looksLikeWorkspace("{\"xsdviewer\": 1, \"files\": []}"));
        assertFalse(Workspace.looksLikeWorkspace("{\"files\": []}"));
        assertFalse(Workspace.looksLikeWorkspace("<xs:schema/>"));
        assertThrows(IllegalArgumentException.class, () -> Workspace.fromJson("{\"files\": []}", FILE));
        assertThrows(IllegalArgumentException.class, () -> Workspace.fromJson("{\"xsdviewer\": 1}", FILE));
        assertThrows(IllegalArgumentException.class, () -> Workspace.fromJson("nope", FILE));
    }
}
