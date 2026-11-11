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

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.jtools.framework.desktop.FileDialogs;
import org.jtools.framework.app.WebApp;
import org.jtools.xsdviewer.json.JsonKey;
import org.jtools.xsdviewer.server.XsdViewerApi;

/**
 * Entry point: JToolsFramework's {@link WebApp} — the command line, {@code xsdviewer.ini}, the Settings menu, the log,
 * the browser, the stop once the last page is closed — serving XsdViewer's routes ({@link XsdViewerApi}).
 *
 * <pre>
 *   java -jar xsdviewer.jar [--port N] [--host H] [--no-browser] [--keep-alive] [--verbose] [file.xsd]
 * </pre>
 *
 * {@code --help} lists every option.
 */
public final class XsdViewerApplication {

    public static final String APP_NAME = "XsdViewer";
    /** The program's id: its jar, {@code xsdviewer.ini}, its folder {@code ~/.xsdviewer}, its preferences. */
    public static final String ID = "xsdviewer";
    /** The system property naming another node for the user's settings: the tests', which never touch the user's own. */
    public static final String PREFERENCES_PROPERTY = ID + ".preferences";
    /** The port when neither the command line, the Settings menu nor {@code xsdviewer.ini} names one. */
    static final int DEFAULT_PORT = 8091;
    private static final String PAGE_ROOT = "web";
    private static final String FILE_OPERAND = "[file.xsd]", FILE_OPERAND_HELP = "the schema, or the workspace (*.xsdviewer.json), to open";

    private XsdViewerApplication() {}

    public static void main(String[] args) {
        program().launch(args);
    }

    /** The program: what {@link #main} launches, and what the tests start ({@code program().start("--port", "0")}). */
    public static WebApp program() {
        AtomicReference<Path> initialFile = new AtomicReference<>();
        return WebApp.named(APP_NAME).id(ID).defaultPort(DEFAULT_PORT).pageRoot(PAGE_ROOT)
                .fileOperand(FILE_OPERAND, FILE_OPERAND_HELP, initialFile::set)
                .aboutFields(about -> about.put(JsonKey.DIALOGS, FileDialogs.available()))
                .endpoints(server -> XsdViewerApi.routes(server, initialFile::get));
    }
}
