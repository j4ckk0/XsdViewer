package org.jtools.xsdviewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The graph extracted from one XSD: the global schema objects (nodes) and the
 * direct ("level 1") links between them (edges).
 */
public final class Model {

    /** A global schema object: element, complexType, simpleType, group, attributeGroup, attribute,
     *  or a placeholder for a built-in XSD type ("builtin") / an object not declared in this file ("external").
     *  {@code ns} is the namespace the name lives in: the target namespace for declarations, the
     *  referenced namespace for external placeholders (what the client uses to find the file declaring it). */
    public record Node(String id, String kind, String name, String ns, int line, String doc) {}

    /** A direct link from one node to another, e.g. element -> its type, complexType -> a child element's type. */
    public record Edge(String from, String to, String label) {}

    /** An xs:import / xs:include / xs:redefine found at the top of the schema. */
    public record Import(String tag, String namespace, String schemaLocation) {}

    public String targetNamespace = "";
    public final List<Import> imports = new ArrayList<>();
    public final Map<String, Node> nodes = new LinkedHashMap<>();
    public final LinkedHashSet<Edge> edges = new LinkedHashSet<>();

    public String toJson() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"targetNamespace\":");
        Json.string(sb, targetNamespace);
        sb.append(",\"imports\":[");
        boolean first = true;
        for (Import i : imports) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"tag\":");
            Json.string(sb, i.tag());
            sb.append(",\"namespace\":");
            Json.string(sb, i.namespace());
            sb.append(",\"schemaLocation\":");
            Json.string(sb, i.schemaLocation());
            sb.append('}');
        }
        sb.append("],\"nodes\":[");
        first = true;
        for (Node n : nodes.values()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":");
            Json.string(sb, n.id());
            sb.append(",\"kind\":");
            Json.string(sb, n.kind());
            sb.append(",\"name\":");
            Json.string(sb, n.name());
            sb.append(",\"ns\":");
            Json.string(sb, n.ns());
            sb.append(",\"line\":").append(n.line());
            sb.append(",\"doc\":");
            Json.string(sb, n.doc());
            sb.append('}');
        }
        sb.append("],\"edges\":[");
        first = true;
        for (Edge e : edges) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"from\":");
            Json.string(sb, e.from());
            sb.append(",\"to\":");
            Json.string(sb, e.to());
            sb.append(",\"label\":");
            Json.string(sb, e.label());
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }
}
