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

/** Property names of the JSON exchanged with the page (the API contract; mirrored by the client). */
public final class JsonKey {

    private JsonKey() {}

    // schema graph (POST /api/parse)
    public static final String TARGET_NAMESPACE = "targetNamespace";
    public static final String IMPORTS = "imports";
    public static final String TAG = "tag";
    public static final String NAMESPACE = "namespace";
    public static final String SCHEMA_LOCATION = "schemaLocation";
    public static final String NODES = "nodes";
    public static final String ID = "id";
    public static final String KIND = "kind";
    public static final String NAME = "name";
    public static final String NS = "ns";
    public static final String LINE = "line";
    public static final String DOC = "doc";
    public static final String EDGES = "edges";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String LABEL = "label";
    /** Cardinality of a link, only when it has one: {@code min}, and {@code max} (-1 = unbounded). */
    public static final String MIN = "min";
    public static final String MAX = "max";

    // schema files (/api/initial, /api/open, /api/locate)
    public static final String PATH = "path";
    public static final String TEXT = "text";

    // workspaces (/api/workspace/*, /api/initial) and file choosers (/api/choose)
    public static final String FILES = "files";
    public static final String ACTIVE = "active";
    public static final String WORKSPACE = "workspace";
    public static final String MISSING = "missing";
    public static final String CANCELLED = "cancelled";
    /** Marker property of a workspace file, holding the format version. */
    public static final String WORKSPACE_MARKER = "xsdviewer";

    // capabilities (/api/capabilities)
    public static final String DIALOGS = "dialogs";
    /** Language of the machine's locale (the JVM default), the page's default language. */
    public static final String LANGUAGE = "language";

    // status answers
    public static final String ERROR = "error";
    public static final String OK = "ok";
}
