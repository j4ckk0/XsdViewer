package org.jtools.xsdviewer.schema;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jtools.xsdviewer.schema.DeclarationLineIndex.Span;
import org.jtools.xsdviewer.schema.DeclarationLineIndex.Tag;
import org.w3c.dom.Element;

/**
 * Turns a WSDL 1.1 into a {@link SchemaGraph}: the services, portTypes, operations, bindings and
 * messages become nodes, the schemas inline in {@code wsdl:types} are parsed by {@link XsdParser}
 * into the same graph, and the links follow the chain service → portType (labelled with the port's
 * name) → operation → message (input / output / fault) → element or type (labelled with the part's
 * name). A binding links to the portType it binds. Only this file is read: what it references
 * without declaring becomes a placeholder node, resolved by the page in the other open files.
 */
final class WsdlParser {

    private static final int MAX_DOCUMENTATION_LENGTH = 1000;
    private static final String LINE_BREAK_WITH_INDENT = "[ \\t]*\\n[ \\t]*";
    /** Between the portType's and the operation's names in an operation's id: {@code operation:Orders.submit}. */
    private static final char OPERATION_ID_SEPARATOR = '.';
    /** Depths, in the tag path, of the declarations: a wsdl:definitions child, an operation, a global declaration of an inline schema. */
    private static final int DECLARATION_DEPTH = 2, OPERATION_DEPTH = 3, INLINE_SCHEMA_DECLARATION_DEPTH = 4;

    private final SchemaGraph graph = new SchemaGraph();
    private final XsdParser xsd;
    private final Map<String, Span> lines;
    /** The bindings declared here, by name: the port of a service reaches the portType through its binding. */
    private final Map<String, Element> bindings = new HashMap<>();

    private WsdlParser(Map<String, Span> lines) {
        this.lines = lines;
        this.xsd = new XsdParser(graph, lines);
    }

    /** The graph of a WSDL file: {@code definitions} is its root, {@code text} the file (for the line numbers). */
    static SchemaGraph parse(Element definitions, String text) throws Exception {
        return new WsdlParser(DeclarationLineIndex.build(text, WsdlParser::declarationId)).doParse(definitions);
    }

    /** The node declared by a tag path: a named message / portType / binding / service, an operation of a portType, a global declaration of an inline schema. */
    static String declarationId(List<Tag> path) {
        Tag t = path.get(path.size() - 1);
        if (t.name() == null) return null;
        switch (path.size()) {
            case DECLARATION_DEPTH -> {
                if (WsdlNames.NAMESPACE.equals(t.uri()) && NodeKind.WSDL_DECLARATIONS.contains(t.localName())) {
                    return SchemaGraph.nodeId(t.localName(), t.name());
                }
            }
            case OPERATION_DEPTH -> {
                Tag parent = path.get(1);
                if (t.is(WsdlNames.NAMESPACE, WsdlNames.OPERATION) && parent.is(WsdlNames.NAMESPACE, WsdlNames.PORT_TYPE)
                        && parent.name() != null) {
                    return operationId(parent.name(), t.name());
                }
            }
            case INLINE_SCHEMA_DECLARATION_DEPTH -> {
                if (path.get(1).is(WsdlNames.NAMESPACE, WsdlNames.TYPES) && path.get(2).is(XsdNames.NAMESPACE, XsdNames.SCHEMA)) {
                    return XsdParser.globalDeclarationId(t);
                }
            }
            default -> { }
        }
        return null;
    }

    /** An operation is named within its portType: {@code operation:PortType.operation}. */
    static String operationId(String portType, String operation) {
        return SchemaGraph.nodeId(NodeKind.OPERATION, portType + OPERATION_ID_SEPARATOR + operation);
    }

    private SchemaGraph doParse(Element definitions) {
        String tns = definitions.getAttribute(XsdNames.ATTR_TARGET_NAMESPACE);
        graph.targetNamespace = tns;

        // Pass 1: the imports, the inline schemas, the declarations as nodes.
        for (Element c : children(definitions)) {
            switch (c.getLocalName()) {
                case WsdlNames.IMPORT -> graph.imports.add(new SchemaGraph.Import(WsdlNames.IMPORT,
                        c.getAttribute(XsdNames.ATTR_NAMESPACE), c.getAttribute(WsdlNames.ATTR_LOCATION)));
                case WsdlNames.TYPES -> {
                    for (Element s : XsdParser.children(c)) {
                        if (XsdNames.NAMESPACE.equals(s.getNamespaceURI()) && XsdNames.SCHEMA.equals(s.getLocalName())) xsd.collectSchema(s);
                    }
                }
                case WsdlNames.MESSAGE, WsdlNames.PORT_TYPE, WsdlNames.BINDING, WsdlNames.SERVICE -> {
                    if (!c.hasAttribute(XsdNames.ATTR_NAME)) continue;
                    String name = c.getAttribute(XsdNames.ATTR_NAME);
                    node(SchemaGraph.nodeId(c.getLocalName(), name), c.getLocalName(), name, tns, c);
                    if (WsdlNames.BINDING.equals(c.getLocalName())) bindings.put(name, c);
                    if (WsdlNames.PORT_TYPE.equals(c.getLocalName())) {
                        for (Element op : children(c, WsdlNames.OPERATION)) {
                            String opName = op.getAttribute(XsdNames.ATTR_NAME);
                            node(operationId(name, opName), NodeKind.OPERATION, opName, tns, op);
                        }
                    }
                }
                default -> { }
            }
        }

        // Pass 2: the links.
        for (Element c : children(definitions)) {
            if (!c.hasAttribute(XsdNames.ATTR_NAME)) continue;
            String name = c.getAttribute(XsdNames.ATTR_NAME);
            String id = SchemaGraph.nodeId(c.getLocalName(), name);
            switch (c.getLocalName()) {
                case WsdlNames.MESSAGE -> {
                    List<String> parts = children(c, WsdlNames.PART).stream().map(p -> p.getAttribute(XsdNames.ATTR_NAME)).filter(n -> !n.isEmpty()).toList();
                    if (!parts.isEmpty()) graph.nodes.computeIfPresent(id, (k, n) -> n.withMembers(parts));
                    for (Element part : children(c, WsdlNames.PART)) {
                        String partName = part.getAttribute(XsdNames.ATTR_NAME);
                        if (part.hasAttribute(WsdlNames.ATTR_ELEMENT)) {
                            xsd.references().link(id, NodeKind.ELEMENT, part.getAttribute(WsdlNames.ATTR_ELEMENT), part, partName, null);
                        } else if (part.hasAttribute(XsdNames.ATTR_TYPE)) {
                            xsd.references().linkType(id, part.getAttribute(XsdNames.ATTR_TYPE), part, partName, null);
                        }
                    }
                }
                case WsdlNames.PORT_TYPE -> {
                    for (Element op : children(c, WsdlNames.OPERATION)) {
                        String opId = operationId(name, op.getAttribute(XsdNames.ATTR_NAME));
                        graph.edges.add(new SchemaGraph.Edge(id, opId, LinkLabel.OPERATION));
                        for (Element io : XsdParser.children(op)) {
                            String label = switch (io.getLocalName()) {
                                case WsdlNames.INPUT -> LinkLabel.INPUT;
                                case WsdlNames.OUTPUT -> LinkLabel.OUTPUT;
                                case WsdlNames.FAULT -> LinkLabel.FAULT;
                                default -> null;
                            };
                            if (label != null && io.hasAttribute(WsdlNames.ATTR_MESSAGE)) {
                                xsd.references().link(opId, NodeKind.MESSAGE, io.getAttribute(WsdlNames.ATTR_MESSAGE), io, label, null);
                            }
                        }
                    }
                }
                case WsdlNames.BINDING -> {
                    if (c.hasAttribute(XsdNames.ATTR_TYPE)) {
                        xsd.references().link(id, NodeKind.PORT_TYPE, c.getAttribute(XsdNames.ATTR_TYPE), c, LinkLabel.BINDS, null);
                    }
                }
                case WsdlNames.SERVICE -> {
                    for (Element port : children(c, WsdlNames.PORT)) {
                        if (!port.hasAttribute(WsdlNames.ATTR_BINDING)) continue;
                        String portName = port.getAttribute(XsdNames.ATTR_NAME);
                        String bindingRef = port.getAttribute(WsdlNames.ATTR_BINDING);
                        // through a binding declared here, the port reaches its portType; otherwise the link stops at the binding
                        Element binding = bindings.get(localPart(bindingRef));
                        if (binding != null && binding.hasAttribute(XsdNames.ATTR_TYPE)) {
                            xsd.references().link(id, NodeKind.PORT_TYPE, binding.getAttribute(XsdNames.ATTR_TYPE), binding, portName, null);
                        } else {
                            xsd.references().link(id, NodeKind.BINDING, bindingRef, port, portName, null);
                        }
                    }
                }
                default -> { }
            }
        }

        // Pass 3: the targets, the placeholders for what this file does not declare.
        xsd.references().resolve();
        return graph;
    }

    private void node(String id, String kind, String name, String ns, Element decl) {
        Span span = lines.getOrDefault(id, Span.NOWHERE);
        graph.nodes.put(id, new SchemaGraph.Node(id, kind, name, ns, span.start(), span.end(), documentation(decl)));
    }

    private static String localPart(String qname) {
        int colon = qname.indexOf(XsdNames.QNAME_SEPARATOR);
        return colon < 0 ? qname : qname.substring(colon + 1);
    }

    /** The child elements of {@code e} in the WSDL namespace. */
    private static List<Element> children(Element e) {
        return XsdParser.children(e).stream().filter(c -> WsdlNames.NAMESPACE.equals(c.getNamespaceURI())).toList();
    }

    /** The child elements of {@code e} in the WSDL namespace named {@code localName}. */
    private static List<Element> children(Element e, String localName) {
        return children(e).stream().filter(c -> localName.equals(c.getLocalName())).toList();
    }

    /** The text of the declaration's first wsdl:documentation. */
    private static String documentation(Element decl) {
        for (Element d : children(decl, WsdlNames.DOCUMENTATION)) {
            String text = d.getTextContent().trim().replaceAll(LINE_BREAK_WITH_INDENT, "\n");
            return text.length() > MAX_DOCUMENTATION_LENGTH ? text.substring(0, MAX_DOCUMENTATION_LENGTH) : text;
        }
        return "";
    }
}
