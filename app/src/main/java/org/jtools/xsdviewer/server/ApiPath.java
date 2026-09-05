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

/** The HTTP paths served by {@link XsdViewerServer} (mirrored by the client). */
public final class ApiPath {

    private ApiPath() {}

    /** {@code POST} schema text, answers the JSON graph. */
    public static final String PARSE = "/api/parse";
    /** {@code GET}: the file given on the command line. */
    public static final String INITIAL = "/api/initial";
    /** {@code GET ?base=&location=}: a schema referenced by an xs:import / xs:include. */
    public static final String OPEN = "/api/open";
    /** {@code POST ?name=} + text: where a file opened in the browser is on disk. */
    public static final String LOCATE = "/api/locate";
    /** {@code POST}: stop the server. */
    public static final String QUIT = "/api/quit";
    /** {@code GET ?id=}: a page is open — an event stream held for the page's whole life. */
    public static final String ALIVE = "/api/alive";
    /** {@code POST ?id=}: a page is closing. */
    public static final String BYE = "/api/bye";
    /** {@code GET}: the settings ({@code {"autoStop": bool}}); {@code POST} the same shape to change them. */
    public static final String SETTINGS = "/api/settings";
    /** {@code GET}: what the server can do for the page ({@code {"dialogs": bool}}). */
    public static final String CAPABILITIES = "/api/capabilities";
    /** {@code POST}: native "open files" dialog, answers the chosen schemas. */
    public static final String CHOOSE = "/api/choose";
    /** {@code POST}: native folder chooser, answers the schemas of the folder and its sub-folders. */
    public static final String CHOOSE_FOLDER = "/api/choose-folder";
    /** {@code POST} a workspace: native "save as" dialog, writes the workspace file. */
    public static final String WORKSPACE_SAVE = "/api/workspace/save";
    /** {@code POST}: native "open" dialog, answers the workspace and its schemas. */
    public static final String WORKSPACE_OPEN = "/api/workspace/open";
    /** {@code POST ?schema=<path>}, body = an XML document: validates it against that schema file. */
    public static final String VALIDATE = "/api/validate";
    /** Everything else: the static files of the page. */
    public static final String ROOT = "/";

    // query parameters
    public static final String PARAM_BASE = "base";
    public static final String PARAM_LOCATION = "location";
    public static final String PARAM_NAME = "name";
    /** {@code /api/alive}, {@code /api/bye}: the page's own random id. */
    public static final String PARAM_ID = "id";
    /** {@code /api/open}: resolve only against the directory of {@code base}. */
    public static final String PARAM_STRICT = "strict";
    public static final String PARAM_SCHEMA = "schema";
    public static final String PARAM_SCHEMATRON = "schematron";
    public static final String PARAM_PHASE = "phase";
    public static final String TRUE = "true";
}
