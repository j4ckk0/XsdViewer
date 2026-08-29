package org.jtools.xsdviewer.json;

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

    // schema files (/api/initial, /api/open, /api/locate)
    public static final String PATH = "path";
    public static final String TEXT = "text";

    // status answers
    public static final String ERROR = "error";
    public static final String OK = "ok";
}
