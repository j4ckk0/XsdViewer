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

/** Keys of the texts in {@code messages*.properties}; see {@link Messages}. */
public final class MessageKey {

    private MessageKey() {}

    // command line
    public static final String USAGE = "cli.usage";
    public static final String OPTION_VALUE_EXPECTED = "cli.optionValueExpected";
    public static final String INVALID_PORT = "cli.invalidPort";
    public static final String NOT_A_FILE = "cli.notAFile";
    public static final String CANNOT_START = "cli.cannotStart";

    // console
    public static final String SERVER_LISTENING = "server.listening";
    public static final String SERVER_QUIT_REQUESTED = "server.quitRequested";
    public static final String SERVER_NO_PAGE_LEFT = "server.noPageLeft";
    public static final String LOG_FILE = "server.logFile";
    public static final String LOG_FILE_UNAVAILABLE = "server.logFileUnavailable";
    public static final String REQUEST_FAILED = "server.requestFailed";

    // HTTP / API errors
    public static final String POST_EXPECTED = "http.postExpected";
    public static final String PAGE_ID_EXPECTED = "http.pageIdExpected";
    public static final String NOT_FOUND = "http.notFound";
    public static final String NO_INITIAL_FILE = "api.noInitialFile";
    public static final String BAD_SETTINGS = "api.badSettings";
    public static final String LOCATION_EXPECTED = "api.locationExpected";
    public static final String REMOTE_LOCATION_NOT_SUPPORTED = "api.remoteLocationNotSupported";
    public static final String FILE_NOT_FOUND = "api.fileNotFound";
    public static final String FILE_NAME_EXPECTED = "api.fileNameExpected";
    public static final String NO_FILE_WITH_CONTENT = "api.noFileWithContent";
    public static final String NO_DISPLAY = "api.noDisplay";
    public static final String INTERNAL_ERROR = "api.internalError";
    public static final String DIALOG_FAILED = "api.dialogFailed";
    public static final String SCHEMA_EXPECTED = "api.schemaExpected";
    public static final String SCHEMA_NOT_COMPILED = "api.schemaNotCompiled";
    public static final String NOT_A_WORKSPACE = "api.notAWorkspace";
    public static final String WORKSPACE_EXPECTED = "api.workspaceExpected";
    public static final String INVALID_JSON = "json.invalid";

    // native file dialogs
    public static final String DIALOG_OPEN_SCHEMA = "dialog.openSchema";
    public static final String DIALOG_OPEN_FOLDER = "dialog.openFolder";
    public static final String DIALOG_OPEN_WORKSPACE = "dialog.openWorkspace";
    public static final String DIALOG_SAVE_WORKSPACE = "dialog.saveWorkspace";
    public static final String DIALOG_FILTER_SCHEMAS = "dialog.filterSchemas";
    public static final String DIALOG_FILTER_WORKSPACES = "dialog.filterWorkspaces";
    public static final String DIALOG_OVERWRITE = "dialog.overwrite";

    // schema parsing
    public static final String NOT_A_SCHEMA = "xsd.notASchema";
    public static final String EXTERNAL_DECLARATION_DOC = "xsd.externalDeclarationDoc";
    public static final String BUILTIN_TYPE_DOC = "xsd.builtinTypeDoc";
}
