package org.jtools.xsdviewer;

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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jtools.xsdviewer.schema.LinkLabel;
import org.jtools.xsdviewer.schema.NodeKind;
import org.jtools.xsdviewer.schema.SchemaGraph;
import org.jtools.xsdviewer.schema.XsdNames;
import org.jtools.xsdviewer.workspace.Workspace;
import org.junit.jupiter.api.Test;

/**
 * The vocabulary the server and the page share is written twice: here in Java constants, there in
 * {@code js/constants.js}. This test reads the JavaScript and fails when the two drift apart.
 */
class PageContractTest {

    private static final Path CONSTANTS_JS = Path.of("app/src/main/resources/web/js/constants.js");

    /** The string values of {@code export const NAME = { KEY: 'value', ... }} in constants.js. */
    private static Set<String> jsValues(String constant) throws Exception {
        String js = Files.readString(CONSTANTS_JS);
        Matcher block = Pattern.compile("export const " + constant + " = \\{(.*?)\\};", Pattern.DOTALL).matcher(js);
        assertTrue(block.find(), constant + " in constants.js");
        Set<String> values = new TreeSet<>();
        Matcher entry = Pattern.compile("[A-Z_]+: '([^']*)'").matcher(block.group(1));
        while (entry.find()) values.add(entry.group(1));
        return values;
    }

    /** The public static final String constants of a class, those whose name starts with {@code prefix}. */
    private static Set<String> javaValues(Class<?> c, String prefix) throws Exception {
        Set<String> values = new TreeSet<>();
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && f.getName().startsWith(prefix)) {
                values.add((String) f.get(null));
            }
        }
        return values;
    }

    @Test
    void nodeKinds() throws Exception {
        Set<String> java = javaValues(NodeKind.class, "");
        java.remove(NodeKind.TYPE_REFERENCE);   // the page has it as TYPE_REFERENCE_KIND
        assertEquals(java, jsValues("NODE_KIND"));
        assertTrue(Files.readString(CONSTANTS_JS).contains("TYPE_REFERENCE_KIND = '" + NodeKind.TYPE_REFERENCE + "'"));
    }

    @Test
    void linkLabels() throws Exception {
        Set<String> js = jsValues("LINK_LABEL");
        js.remove(LinkLabel.attribute(""));   // ATTRIBUTE_PREFIX: the prefix of a nested attribute's label
        assertEquals(javaValues(LinkLabel.class, ""), js);
    }

    @Test
    void apiPaths() throws Exception {
        Set<String> java = new TreeSet<>();
        for (Field f : Class.forName("org.jtools.xsdviewer.server.ApiPath").getDeclaredFields()) {
            f.setAccessible(true);
            if (f.getType() == String.class && ((String) f.get(null)).startsWith("/api/")) java.add((String) f.get(null));
        }
        assertEquals(java, jsValues("API"));
        Set<String> params = new TreeSet<>();
        for (Field f : Class.forName("org.jtools.xsdviewer.server.ApiPath").getDeclaredFields()) {
            f.setAccessible(true);
            if (f.getName().startsWith("PARAM_")) params.add((String) f.get(null));
        }
        assertEquals(params, jsValues("API_PARAM"));
    }

    @Test
    void importTagsAndFileNames() throws Exception {
        assertEquals(Set.of(XsdNames.IMPORT, XsdNames.INCLUDE, XsdNames.REDEFINE), jsValues("IMPORT_TAG"));
        String js = Files.readString(CONSTANTS_JS);
        assertTrue(js.contains("WORKSPACE_FILE_SUFFIX = '" + Workspace.FILE_SUFFIX + "'"), "the workspace file suffix");
        assertTrue(js.contains("ID_SEPARATOR = '" + SchemaGraph.ID_SEPARATOR + "'"), "the node id separator");
    }
}
