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

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.jtools.framework.server.WebServer;
import org.jtools.xsdviewer.Messages;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

/**
 * XsdViewer's HTTP API: its routes on JToolsFramework's {@link WebServer}, which serves the page from {@code web/},
 * keeps the pages' presence and answers the program's own routes ({@code /api/settings}, {@code /api/about},
 * {@code /api/quit}). Every request is answered in the page's language: core's {@link Messages} are set to it for the
 * request's length — a presence stream's included, hours long.
 */
public final class XsdViewerApi {

    private static final String ACCEPT_LANGUAGE = "Accept-Language";

    private XsdViewerApi() {}

    /** Registers the routes; {@code initialFile} is the command line's file, known once the command line is read. */
    public static WebServer routes(WebServer server, Supplier<Path> initialFile) {
        ServedSchemaFiles files = new ServedSchemaFiles();
        return server.filter(new PageLanguage())
                .post(ApiPath.PARSE, new ParseSchemaRoute())
                .get(ApiPath.INITIAL, new InitialFileRoute(files, initialFile))
                .get(ApiPath.OPEN, new OpenSchemaLocationRoute(files))
                .post(ApiPath.LOCATE, new LocateSchemaFileRoute(files, new SchemaFileFinder()))
                .post(ApiPath.CHOOSE, new ChooseFilesRoute(files))
                .post(ApiPath.CHOOSE_FOLDER, new ChooseFolderRoute(files))
                .post(ApiPath.WORKSPACE_SAVE, new SaveWorkspaceRoute())
                .post(ApiPath.WORKSPACE_OPEN, new OpenWorkspaceRoute(files))
                .post(ApiPath.VALIDATE, new ValidateRoute(files))
                .post(ApiPath.MODEL, new ModelRoute())
                .post(ApiPath.COMPARE_DECLARATIONS, new CompareDeclarationsRoute())
                .post(ApiPath.COMPARE_TEXTS, new CompareTextsRoute())
                .post(ApiPath.COMPARE_SCHEMAS, new CompareSchemasRoute())
                .post(ApiPath.COMPARE_WORKSPACES, new CompareWorkspacesRoute());
    }

    /** Core's {@link Messages} in the page's language ({@code Accept-Language}) while the request lasts. */
    private static final class PageLanguage extends Filter {

        @Override
        public void doFilter(HttpExchange ex, Chain chain) throws IOException {
            Messages.setRequestLocale(Messages.localeOf(ex.getRequestHeaders().getFirst(ACCEPT_LANGUAGE)));
            try {
                chain.doFilter(ex);
            } finally {
                Messages.clearRequestLocale();
            }
        }

        @Override
        public String description() {
            return "core's Messages in the page's language";
        }
    }
}
