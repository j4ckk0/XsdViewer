package fr.j4ckk0.xsdviewer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Turns the text of an XSD into a {@link Model}: one node per global declaration,
 * one edge per direct reference from a declaration to another one.
 *
 * <p>Only the given file is read; objects it references but does not declare
 * (imported / included ones) become "external" nodes, XSD built-in types become
 * "builtin" nodes.
 */
public final class XsdParser {

    public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private static final Set<String> TOP_KINDS =
            Set.of("element", "complexType", "simpleType", "group", "attributeGroup", "attribute");

    private final Model model = new Model();
    /** Edges whose target is not resolved yet: "type:X", "element:X", "group:X"... */
    private final List<Model.Edge> pending = new ArrayList<>();
    private Map<String, Integer> lines = Map.of();

    private XsdParser() {}

    public static Model parse(String xsdText) throws Exception {
        return new XsdParser().doParse(xsdText);
    }

    private Model doParse(String text) throws Exception {
        lines = lineIndex(text);

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(text)));

        Element schema = doc.getDocumentElement();
        if (!XSD_NS.equals(schema.getNamespaceURI()) || !"schema".equals(schema.getLocalName())) {
            throw new IllegalArgumentException(
                    "Root element is <" + schema.getTagName() + ">, not an XML Schema (xs:schema)");
        }
        model.targetNamespace = schema.getAttribute("targetNamespace");

        // Pass 1: the global declarations become nodes.
        for (Element c : children(schema)) {
            String ln = c.getLocalName();
            if ("import".equals(ln) || "include".equals(ln) || "redefine".equals(ln)) {
                model.imports.add(new Model.Import(ln, c.getAttribute("namespace"), c.getAttribute("schemaLocation")));
                continue;
            }
            if (TOP_KINDS.contains(ln) && c.hasAttribute("name")) {
                String id = ln + ":" + c.getAttribute("name");
                model.nodes.put(id, new Model.Node(id, ln, c.getAttribute("name"),
                        lines.getOrDefault(id, 0), documentation(c)));
            }
        }

        // Pass 2: the links.
        for (Element c : children(schema)) {
            String ln = c.getLocalName();
            if (TOP_KINDS.contains(ln) && c.hasAttribute("name")) {
                collect(c, ln + ":" + c.getAttribute("name"), true);
            }
        }

        // Pass 3: resolve targets, creating placeholder nodes for what this file does not declare.
        for (Model.Edge e : pending) {
            String to = e.to();
            if (!model.nodes.containsKey(to)) {
                int colon = to.indexOf(':');
                String kind = to.substring(0, colon);
                String name = to.substring(colon + 1);
                if ("type".equals(kind)) {
                    if (model.nodes.containsKey("complexType:" + name)) to = "complexType:" + name;
                    else if (model.nodes.containsKey("simpleType:" + name)) to = "simpleType:" + name;
                }
                if (!model.nodes.containsKey(to)) {
                    model.nodes.put(to, new Model.Node(to, "external", name, 0,
                            "Not declared in this file (" + kind + ")"));
                }
            }
            model.edges.add(new Model.Edge(e.from(), to, e.label()));
        }
        return model;
    }

    /**
     * Walks a declaration and records every reference it makes.
     *
     * @param e     the element being examined
     * @param owner id of the global declaration all links are attributed to
     * @param self  true when {@code e} is the global declaration itself (its own
     *              type / substitutionGroup are then labelled differently from a child's)
     */
    private void collect(Element e, String owner, boolean self) {
        if (!XSD_NS.equals(e.getNamespaceURI())) return; // e.g. content of xs:appinfo
        String ln = e.getLocalName();
        switch (ln) {
            case "annotation" -> { return; }
            case "element" -> {
                if (e.hasAttribute("ref")) {
                    link(owner, "element", e.getAttribute("ref"), e, self ? "ref" : "child ref");
                    return;
                }
                String name = e.getAttribute("name");
                if (e.hasAttribute("type")) {
                    linkType(owner, e.getAttribute("type"), e, self ? "type" : "child " + name);
                }
                if (self && e.hasAttribute("substitutionGroup")) {
                    link(owner, "element", e.getAttribute("substitutionGroup"), e, "substitutes");
                }
            }
            case "attribute" -> {
                if (e.hasAttribute("ref")) {
                    link(owner, "attribute", e.getAttribute("ref"), e, "attribute ref");
                    return;
                }
                if (e.hasAttribute("type")) {
                    linkType(owner, e.getAttribute("type"), e, self ? "type" : "attribute " + e.getAttribute("name"));
                }
            }
            case "group" -> {
                if (e.hasAttribute("ref")) {
                    link(owner, "group", e.getAttribute("ref"), e, "group");
                    return;
                }
            }
            case "attributeGroup" -> {
                if (e.hasAttribute("ref")) {
                    link(owner, "attributeGroup", e.getAttribute("ref"), e, "attributeGroup");
                    return;
                }
            }
            case "extension" -> {
                if (e.hasAttribute("base")) linkType(owner, e.getAttribute("base"), e, "extends");
            }
            case "restriction" -> {
                if (e.hasAttribute("base")) linkType(owner, e.getAttribute("base"), e, "restricts");
            }
            case "list" -> {
                if (e.hasAttribute("itemType")) linkType(owner, e.getAttribute("itemType"), e, "list of");
            }
            case "union" -> {
                if (e.hasAttribute("memberTypes")) {
                    for (String t : e.getAttribute("memberTypes").trim().split("\\s+")) {
                        if (!t.isEmpty()) linkType(owner, t, e, "union of");
                    }
                }
            }
            default -> { }
        }
        for (Element c : children(e)) collect(c, owner, false);
    }

    /** A reference to a named type: built-in XSD types are resolved now, the others at the end. */
    private void linkType(String owner, String qname, Element ctx, String label) {
        int colon = qname.indexOf(':');
        String prefix = colon < 0 ? null : qname.substring(0, colon);
        String local = colon < 0 ? qname : qname.substring(colon + 1);
        String ns = ctx.lookupNamespaceURI(prefix);
        // A name in the XSD namespace is a built-in type, unless this file declares it: schemas
        // that use the XSD namespace as their default namespace refer to their own types unprefixed.
        boolean declaredHere = model.nodes.containsKey("complexType:" + local) || model.nodes.containsKey("simpleType:" + local);
        if (XSD_NS.equals(ns) && !declaredHere) {
            String id = "builtin:" + local;
            model.nodes.computeIfAbsent(id, k -> new Model.Node(id, "builtin", local, 0, "XML Schema built-in type"));
            model.edges.add(new Model.Edge(owner, id, label));
        } else {
            pending.add(new Model.Edge(owner, "type:" + local, label));
        }
    }

    /** A reference (ref=, substitutionGroup=) to a named declaration of a given kind. */
    private void link(String owner, String kind, String qname, Element ctx, String label) {
        int colon = qname.indexOf(':');
        String local = colon < 0 ? qname : qname.substring(colon + 1);
        pending.add(new Model.Edge(owner, kind + ":" + local, label));
    }

    private static String documentation(Element decl) {
        for (Element a : children(decl)) {
            if (!"annotation".equals(a.getLocalName())) continue;
            StringBuilder sb = new StringBuilder();
            for (Element d : children(a)) {
                if ("documentation".equals(d.getLocalName())) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(d.getTextContent().trim().replaceAll("[ \\t]*\\n[ \\t]*", "\n"));
                }
            }
            if (sb.length() > 1000) sb.setLength(1000);
            return sb.toString();
        }
        return "";
    }

    private static List<Element> children(Element e) {
        NodeList nl = e.getChildNodes();
        List<Element> out = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) out.add((Element) n);
        }
        return out;
    }

    // ---- line numbers -------------------------------------------------------------------------

    /**
     * Maps "kind:name" of each global declaration to the line where its start tag opens.
     * The SAX locator reports the position of the end of the start tag, so we walk back
     * from there to the '<' to also handle start tags spread over several lines.
     */
    private static Map<String, Integer> lineIndex(String text) throws Exception {
        int[] lineStarts = lineStarts(text);
        Map<String, Integer> result = new HashMap<>();

        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        f.newSAXParser().parse(new InputSource(new StringReader(text)), new DefaultHandler() {
            private Locator locator;
            private int depth;

            @Override
            public void setDocumentLocator(Locator l) { locator = l; }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attrs) {
                depth++;
                if (depth != 2 || !XSD_NS.equals(uri) || !TOP_KINDS.contains(localName)) return;
                String name = attrs.getValue("name");
                if (name == null || locator == null) return;
                int line = locator.getLineNumber();
                int col = locator.getColumnNumber();
                if (line > 0 && line <= lineStarts.length) {
                    int offset = Math.min(text.length(), lineStarts[line - 1] + Math.max(0, col - 1));
                    int lt = text.lastIndexOf('<', Math.max(0, offset - 1));
                    if (lt >= 0) line = lineOf(lineStarts, lt);
                }
                result.putIfAbsent(localName + ":" + name, line);
            }

            @Override
            public void endElement(String uri, String localName, String qName) { depth--; }
        });
        return result;
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts.add(i + 1);
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 1-based line containing the character at {@code offset}. */
    private static int lineOf(int[] lineStarts, int offset) {
        int i = Arrays.binarySearch(lineStarts, offset);
        if (i < 0) i = -i - 2;
        return i + 1;
    }
}
