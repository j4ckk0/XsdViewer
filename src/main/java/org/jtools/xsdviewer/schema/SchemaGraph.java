package org.jtools.xsdviewer.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The graph extracted from one XSD: the global schema objects (nodes) and the direct
 * ("level 1") links between them (edges). Node ids are {@code kind:name} (see {@link #nodeId}).
 */
public final class SchemaGraph {

    /** Separator between kind and name in a node id: {@code element:purchaseOrder}. */
    public static final char ID_SEPARATOR = ':';

    /**
     * A global schema object: a declaration ({@link NodeKind#GLOBAL_DECLARATIONS}), or a
     * placeholder for a built-in XSD type ({@link NodeKind#BUILTIN}) / an object not declared in
     * this file ({@link NodeKind#EXTERNAL}). {@code ns} is the namespace the name lives in: the
     * target namespace for declarations, the referenced namespace for external placeholders (what
     * the client uses to find the file declaring it). {@code line} is 1-based, 0 when unknown.
     */
    public record Node(String id, String kind, String name, String ns, int line, String doc) {}

    /** A direct link from one node to another, e.g. element -> its type; {@code label} is a {@link LinkLabel}. */
    public record Edge(String from, String to, String label) {}

    /** An xs:import / xs:include / xs:redefine found at the top of the schema. */
    public record Import(String tag, String namespace, String schemaLocation) {}

    public String targetNamespace = "";
    public final List<Import> imports = new ArrayList<>();
    /** By id, in declaration order. */
    public final Map<String, Node> nodes = new LinkedHashMap<>();
    /** In discovery order; identical parallel edges collapse. */
    public final LinkedHashSet<Edge> edges = new LinkedHashSet<>();

    public static String nodeId(String kind, String name) {
        return kind + ID_SEPARATOR + name;
    }

    public static String kindOf(String id) {
        return id.substring(0, id.indexOf(ID_SEPARATOR));
    }

    public static String nameOf(String id) {
        return id.substring(id.indexOf(ID_SEPARATOR) + 1);
    }

    public boolean declares(String id) {
        return nodes.containsKey(id);
    }

    public String toJson() {
        return SchemaGraphJsonWriter.write(this);
    }
}
