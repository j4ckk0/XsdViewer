package org.jtools.xsdviewer.server;

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
    /** Everything else: the static files of the page. */
    public static final String ROOT = "/";

    // query parameters
    public static final String PARAM_BASE = "base";
    public static final String PARAM_LOCATION = "location";
    public static final String PARAM_NAME = "name";
}
