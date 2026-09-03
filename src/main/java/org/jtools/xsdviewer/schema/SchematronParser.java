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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Turns a Schematron ({@code sch:schema}) into a {@link SchemaGraph}: the phases, patterns, rules,
 * assertions ({@code assert} / {@code report}) and diagnostics become nodes, and the links follow
 * the chain phase → pattern ({@code active}) → rule → assertion → diagnostic, plus a pattern to the
 * abstract pattern it instantiates ({@code is-a}) and a rule to the abstract rule it extends.
 * <p>
 * Schematron names little: a rule is known by its context, an assertion by its test, so a node is
 * named by its {@code id} when it has one and by its expression otherwise (the expression is kept
 * whole as the node's {@code xpath}); a pattern without an id takes its title, else its rank.
 * An unnamed rule or assertion is identified within its parent ({@code rule:pattern/context}) and
 * a second identical one gets a suffix. Line numbers come from the rank of the element in the
 * document, the same in the DOM and in the SAX pass ({@link DeclarationLineIndex#elementLines}).
 * A fragment meant for {@code sch:include} (a {@code pattern}, a {@code rule}... as the root) is
 * read as if it were the only child of a schema.
 */
final class SchematronParser {

    private static final int MAX_TEXT_LENGTH = 1000;
    private static final String WHITESPACE = "\\s+";
    /** Between the parent's key and the expression in the id of an unnamed rule or assertion: {@code rule:orders/item}. */
    private static final char SCOPE_SEPARATOR = '/';
    /** Before the rank in the id of a second identical node: {@code rule:orders/item#2}. */
    private static final char DUPLICATE_MARK = '#';
    /** The name of a pattern that has neither id, name nor title: "pattern 3". */
    private static final String UNNAMED_PATTERN = "pattern ";
    /** Around the expression a {@code value-of} or {@code name} stands for in a message: {@code {count(item)}}. */
    private static final char PLACEHOLDER_OPEN = '{', PLACEHOLDER_CLOSE = '}';
    private static final String NAME_FUNCTION = "name(";
    private static final String NAME_FUNCTION_END = ")";
    private static final String ROLE_OPEN = "[", ROLE_CLOSE = "] ";

    /** A link to a node this file may not declare: (from, to, label), the target checked once everything is read. */
    private record Pending(String from, String to, String label) {}

    private final SchemaGraph graph = new SchemaGraph();
    private final int[] lines;
    /** The rank of each element in document order: its line is {@code lines[rank]}. */
    private final Map<Element, Integer> ranks = new IdentityHashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final Set<String> ids = new HashSet<>();
    /** The id of the node each declaring element got: what {@link SchematronValidator} reports a firing assertion as. */
    private final Map<Element, String> elementIds = new IdentityHashMap<>();
    private int patterns;

    private SchematronParser(int[] lines) {
        this.lines = lines;
    }

    /** The graph of a Schematron file: {@code root} is its root element, {@code text} the file (for the line numbers). */
    static SchemaGraph parse(Element root, String text) throws Exception {
        return parse(root, new SchematronParser(DeclarationLineIndex.elementLines(text))).graph;
    }

    /** The node id of each declaring element of {@code root} (phases, patterns, rules, assertions, diagnostics), without line numbers. */
    static Map<Element, String> elementIds(Element root) {
        return parse(root, new SchematronParser(new int[0])).elementIds;
    }

    private static SchematronParser parse(Element root, SchematronParser parser) {
        parser.rank(root);
        if (SchematronVocabulary.SCHEMA.equals(root.getLocalName())) {
            for (Element c : children(root)) parser.topLevel(c);
        } else {
            parser.topLevel(root);   // a fragment: a pattern, a rule, a phase... to be included by a schema
        }
        parser.resolve();
        return parser;
    }

    private void rank(Element e) {
        ranks.put(e, ranks.size());
        for (Element c : XsdParser.children(e)) rank(c);
    }

    private int line(Element e) {
        Integer rank = ranks.get(e);
        return rank == null || rank >= lines.length ? 0 : lines[rank];
    }

    /** A child of the schema (or a fragment's root). */
    private void topLevel(Element c) {
        switch (c.getLocalName()) {
            case SchematronVocabulary.INCLUDE -> include(c);
            case SchematronVocabulary.PHASE -> phase(c);
            case SchematronVocabulary.PATTERN -> pattern(c);
            case SchematronVocabulary.RULE -> rule(c, null);
            case SchematronVocabulary.DIAGNOSTICS -> {
                for (Element d : children(c, SchematronVocabulary.DIAGNOSTIC)) diagnostic(d);
            }
            case SchematronVocabulary.DIAGNOSTIC -> diagnostic(c);
            default -> { }
        }
    }

    private void include(Element inc) {
        graph.imports.add(new SchemaGraph.Import(SchematronVocabulary.INCLUDE, "", inc.getAttribute(SchematronVocabulary.ATTR_HREF)));
    }

    private void phase(Element phase) {
        if (!phase.hasAttribute(SchematronVocabulary.ATTR_ID)) return;
        String name = phase.getAttribute(SchematronVocabulary.ATTR_ID);
        String id = node(NodeKind.PHASE, name, name, phase, paragraphs(phase), "");
        for (Element a : children(phase, SchematronVocabulary.ACTIVE)) {
            if (a.hasAttribute(SchematronVocabulary.ATTR_PATTERN)) {
                pending.add(new Pending(id, SchemaGraph.nodeId(NodeKind.PATTERN, a.getAttribute(SchematronVocabulary.ATTR_PATTERN)), LinkLabel.ACTIVE));
            }
        }
    }

    private void pattern(Element pattern) {
        patterns++;
        String key = first(pattern, SchematronVocabulary.ATTR_ID, XsdVocabulary.ATTR_NAME);
        String title = text(child(pattern, SchematronVocabulary.TITLE));
        if (key == null) key = !title.isEmpty() ? title : UNNAMED_PATTERN + patterns;
        String doc = paragraphs(pattern);
        if (!title.isEmpty() && !title.equals(key)) doc = doc.isEmpty() ? title : title + '\n' + doc;
        String id = node(NodeKind.PATTERN, key, key, pattern, doc, "");
        if (pattern.hasAttribute(SchematronVocabulary.ATTR_IS_A)) {
            pending.add(new Pending(id, SchemaGraph.nodeId(NodeKind.PATTERN, pattern.getAttribute(SchematronVocabulary.ATTR_IS_A)), LinkLabel.IS_A));
        }
        for (Element c : children(pattern)) {
            switch (c.getLocalName()) {
                case SchematronVocabulary.INCLUDE -> include(c);
                case SchematronVocabulary.RULE -> {
                    String ruleId = rule(c, SchemaGraph.nameOf(id));
                    if (ruleId != null) graph.edges.add(new SchemaGraph.Edge(id, ruleId, LinkLabel.RULE));
                }
                default -> { }
            }
        }
    }

    /** A rule of a pattern whose key is {@code scope} (null for a rule fragment): its id, or null when it has neither id nor context. */
    private String rule(Element rule, String scope) {
        String context = rule.getAttribute(SchematronVocabulary.ATTR_CONTEXT);
        String key = rule.getAttribute(SchematronVocabulary.ATTR_ID);
        if (key.isEmpty()) {
            if (context.isEmpty()) return null;
            key = scope == null ? context : scope + SCOPE_SEPARATOR + context;
        }
        String id = node(NodeKind.RULE, key, context.isEmpty() ? key : context, rule, "", context);
        for (Element c : children(rule)) {
            switch (c.getLocalName()) {
                case SchematronVocabulary.INCLUDE -> include(c);
                case SchematronVocabulary.EXTENDS -> {
                    if (c.hasAttribute(SchematronVocabulary.ATTR_RULE)) {
                        pending.add(new Pending(id, SchemaGraph.nodeId(NodeKind.RULE, c.getAttribute(SchematronVocabulary.ATTR_RULE)), LinkLabel.EXTENDS));
                    }
                }
                case SchematronVocabulary.ASSERT, SchematronVocabulary.REPORT -> {
                    String assertionId = assertion(c, SchemaGraph.nameOf(id));
                    if (assertionId != null) graph.edges.add(new SchemaGraph.Edge(id, assertionId, c.getLocalName()));
                }
                default -> { }
            }
        }
        return id;
    }

    /** An assert / report of the rule whose key is {@code scope}: its id, or null without a test. */
    private String assertion(Element a, String scope) {
        String test = a.getAttribute(SchematronVocabulary.ATTR_TEST);
        String key = a.getAttribute(SchematronVocabulary.ATTR_ID);
        String name = key.isEmpty() ? test : key;   // an assertion is known by its id when it has one, else by its test
        if (key.isEmpty()) {
            if (test.isEmpty()) return null;
            key = scope + SCOPE_SEPARATOR + test;
        }
        String role = first(a, SchematronVocabulary.ATTR_ROLE, SchematronVocabulary.ATTR_FLAG);
        String message = message(a);
        String doc = role == null ? message : ROLE_OPEN + role + ROLE_CLOSE + message;
        String id = node(a.getLocalName(), key, name, a, doc, test);
        if (a.hasAttribute(SchematronVocabulary.ATTR_DIAGNOSTICS)) {
            for (String d : a.getAttribute(SchematronVocabulary.ATTR_DIAGNOSTICS).trim().split(WHITESPACE)) {
                if (!d.isEmpty()) pending.add(new Pending(id, SchemaGraph.nodeId(NodeKind.DIAGNOSTIC, d), LinkLabel.DIAGNOSTIC));
            }
        }
        return id;
    }

    private void diagnostic(Element d) {
        if (!d.hasAttribute(SchematronVocabulary.ATTR_ID)) return;
        String name = d.getAttribute(SchematronVocabulary.ATTR_ID);
        node(NodeKind.DIAGNOSTIC, name, name, d, message(d), "");
    }

    /** Adds a node of {@code kind} keyed {@code key} (suffixed when the key is taken); answers its id. */
    private String node(String kind, String key, String name, Element decl, String doc, String xpath) {
        String id = SchemaGraph.nodeId(kind, key);
        for (int n = 2; !ids.add(id); n++) id = SchemaGraph.nodeId(kind, key + DUPLICATE_MARK + n);
        graph.nodes.put(id, new SchemaGraph.Node(id, kind, name, "", line(decl), doc).withXpath(xpath));
        elementIds.put(decl, id);
        return id;
    }

    /** The links to what may be declared elsewhere: a placeholder node for each missing target. */
    private void resolve() {
        for (Pending p : pending) {
            if (!graph.declares(p.to())) {
                String kind = SchemaGraph.kindOf(p.to());
                graph.nodes.put(p.to(), new SchemaGraph.Node(p.to(), NodeKind.EXTERNAL, SchemaGraph.nameOf(p.to()), "", 0,
                        Messages.get(MessageKey.EXTERNAL_DECLARATION_DOC, kind)));
            }
            graph.edges.add(new SchemaGraph.Edge(p.from(), p.to(), p.label()));
        }
        pending.clear();
    }

    /** The value of the first of {@code attributes} that {@code e} carries, or null. */
    private static String first(Element e, String... attributes) {
        for (String a : attributes) if (e.hasAttribute(a)) return e.getAttribute(a);
        return null;
    }

    /** The child elements of {@code e} in a Schematron namespace. */
    private static List<Element> children(Element e) {
        return XsdParser.children(e).stream().filter(c -> c.getNamespaceURI() != null && SchematronVocabulary.NAMESPACES.contains(c.getNamespaceURI())).toList();
    }

    private static List<Element> children(Element e, String localName) {
        return children(e).stream().filter(c -> localName.equals(c.getLocalName())).toList();
    }

    private static Element child(Element e, String localName) {
        List<Element> found = children(e, localName);
        return found.isEmpty() ? null : found.get(0);
    }

    /** The {@code p} paragraphs of a phase or pattern, one per line. */
    private static String paragraphs(Element e) {
        StringBuilder sb = new StringBuilder();
        for (Element p : children(e, SchematronVocabulary.PARAGRAPH)) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(text(p));
        }
        return cut(sb.toString());
    }

    /** The text of an assertion or diagnostic: its message, a {@code value-of} shown as {@code {select}}, a {@code name} as {@code {name()}}. */
    private static String message(Element e) {
        StringBuilder sb = new StringBuilder();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(n.getNodeValue());
            } else if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element c = (Element) n;
                if (SchematronVocabulary.VALUE_OF.equals(c.getLocalName())) {
                    sb.append(PLACEHOLDER_OPEN).append(c.getAttribute(SchematronVocabulary.ATTR_SELECT)).append(PLACEHOLDER_CLOSE);
                } else if (SchematronVocabulary.NAME.equals(c.getLocalName())) {
                    sb.append(PLACEHOLDER_OPEN).append(NAME_FUNCTION).append(c.getAttribute(SchematronVocabulary.ATTR_PATH)).append(NAME_FUNCTION_END).append(PLACEHOLDER_CLOSE);
                } else {
                    sb.append(message(c));   // emph, dir, span: their text
                }
            }
        }
        return cut(sb.toString().replaceAll(WHITESPACE, " ").trim());
    }

    /** The text of {@code e} with its whitespace collapsed; empty for null. */
    private static String text(Element e) {
        return e == null ? "" : e.getTextContent().replaceAll(WHITESPACE, " ").trim();
    }

    private static String cut(String s) {
        return s.length() > MAX_TEXT_LENGTH ? s.substring(0, MAX_TEXT_LENGTH) : s;
    }
}
