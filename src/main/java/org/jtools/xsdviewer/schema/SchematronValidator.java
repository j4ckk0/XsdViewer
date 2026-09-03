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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

/**
 * Checks an XML document against a Schematron with the JDK's XPath 1.0 engine: phases, abstract
 * patterns and their parameters, abstract rules ({@code extends}), {@code let} variables,
 * {@code include}s next to the file, diagnostics-free messages with {@code value-of} and
 * {@code name} filled in. Each node fires at most one rule per pattern (the first whose context
 * matches, as in ISO Schematron). An expression the engine cannot compile — XPath 2 and later —
 * is reported once as {@link Severity#UNSUPPORTED}, and nothing is checked there. A problem names
 * the assertion, rule and pattern as {@link SchematronParser} names them, so the page can select them.
 */
public final class SchematronValidator {

    /** The phase running every pattern (the ISO name). */
    public static final String ALL_PHASES = "#ALL";
    /** Problems beyond this many are not collected. */
    static final int MAX_PROBLEMS = 200;
    private static final int MAX_INCLUDE_DEPTH = 16;
    private static final String REMOTE_LOCATION_MARK = "://";
    private static final String ATTR_ABSTRACT = "abstract", ATTR_DEFAULT_PHASE = "defaultPhase", ATTR_PREFIX = "prefix", ATTR_URI = "uri", ATTR_VALUE = "value";
    private static final String NS = "ns", LET = "let", PARAM = "param", EMPH = "emph", SPAN = "span", DIR = "dir";
    private static final String TRUE = "true";
    private static final String WARNING_ROLE = "warn", INFO_ROLE = "info";
    private static final String UNION = "|", DESCENDANTS = "//", ROOT = "/";
    private static final String WHITESPACE = "\\s+";
    private static final String PLACEHOLDER = "{%s}";
    private static final String ATTRIBUTE_STEP = "/@";

    /**
     * A firing assertion (or a failure to evaluate one): where in the document ({@code line},
     * {@code column}, {@code location}: the node's path), the message, and what fired
     * ({@code assertion}, {@code rule}, {@code pattern}: node ids of the Schematron's graph; {@code test}).
     */
    public record Problem(String severity, int line, int column, String message, String location,
                          String assertion, String rule, String pattern, String test) {}

    /** The outcome: valid when no assertion of error severity fired; the phases the schema declares and the one run; how many tests were evaluated. */
    public record Result(boolean valid, List<Problem> problems, boolean truncated, List<String> phases, String phase, int checked) {}

    /** A variable scope: the {@code let}s in force, innermost last. */
    private record Scope(Map<String, Object> variables) {
        Scope child() { return new Scope(new LinkedHashMap<>(variables)); }
    }

    private final Path file;
    private final Document instance;
    private final XPath xpath = XPathFactory.newInstance().newXPath();
    private final Map<String, String> prefixes = new HashMap<>();
    private final Map<String, XPathExpression> compiled = new HashMap<>();
    private final Map<Element, String> ids;
    private final List<Problem> problems = new ArrayList<>();
    private boolean truncated;
    private int checked;
    private Scope scope = new Scope(new LinkedHashMap<>());
    /** The parameters of the abstract pattern being run as an instance ({@code $name} → value), empty otherwise. */
    private Map<String, String> params = Map.of();

    private SchematronValidator(Path file, Document instance, Map<Element, String> ids) {
        this.file = file;
        this.instance = instance;
        this.ids = ids;
        xpath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) { return prefixes.get(prefix); }

            @Override
            public String getPrefix(String uri) { return null; }

            @Override
            public Iterator<String> getPrefixes(String uri) { return Collections.emptyIterator(); }
        });
        xpath.setXPathVariableResolver(name -> scope.variables().get(name.getLocalPart()));
    }

    /**
     * @param schematronFile the Schematron (its includes resolve next to it)
     * @param xml            the document
     * @param phase          the phase to run: a phase id, {@link #ALL_PHASES}, or null for the schema's default
     * @throws IOException              when a file cannot be read
     * @throws SAXParseException        when the document is not well-formed
     * @throws IllegalArgumentException when the file is not a Schematron or the phase unknown
     */
    public static Result validate(Path schematronFile, String xml, String phase) throws Exception {
        Document sch = SecureXmlFactories.newDocumentBuilder().parse(schematronFile.toFile());
        Element root = sch.getDocumentElement();
        if (root.getNamespaceURI() == null || !SchematronVocabulary.NAMESPACES.contains(root.getNamespaceURI())
                || !SchematronVocabulary.SCHEMA.equals(root.getLocalName())) {
            throw new IllegalArgumentException(Messages.get(MessageKey.NOT_A_SCHEMATRON, root.getTagName()));
        }
        Document instance = LocatedDocument.parse(xml);
        SchematronValidator v = new SchematronValidator(schematronFile, instance, new IdentityHashMap<>());
        v.includes(root, 0);
        v.ids.putAll(SchematronParser.elementIds(root));
        return v.run(root, phase);
    }

    private Result run(Element schema, String phase) throws XPathExpressionException {
        for (Element ns : children(schema, NS)) prefixes.put(ns.getAttribute(ATTR_PREFIX), ns.getAttribute(ATTR_URI));
        List<String> phases = children(schema, SchematronVocabulary.PHASE).stream()
                .map(p -> p.getAttribute(SchematronVocabulary.ATTR_ID)).filter(id -> !id.isEmpty()).toList();
        if (phase == null || phase.isEmpty()) phase = schema.hasAttribute(ATTR_DEFAULT_PHASE) ? schema.getAttribute(ATTR_DEFAULT_PHASE) : ALL_PHASES;
        Set<String> active = null;
        if (!ALL_PHASES.equals(phase)) {
            Element p = byId(schema, SchematronVocabulary.PHASE, phase);
            if (p == null) throw new IllegalArgumentException(Messages.get(MessageKey.PHASE_UNKNOWN, phase));
            active = children(p, SchematronVocabulary.ACTIVE).stream().map(a -> a.getAttribute(SchematronVocabulary.ATTR_PATTERN)).collect(java.util.stream.Collectors.toSet());
        }
        lets(schema, instance);
        Scope schemaScope = scope;
        for (Element pattern : children(schema, SchematronVocabulary.PATTERN)) {
            if (TRUE.equals(pattern.getAttribute(ATTR_ABSTRACT))) continue;
            if (active != null && !active.contains(pattern.getAttribute(SchematronVocabulary.ATTR_ID))) continue;
            scope = schemaScope.child();
            params = Map.of();
            Element body = pattern;
            if (pattern.hasAttribute(SchematronVocabulary.ATTR_IS_A)) {
                String name = pattern.getAttribute(SchematronVocabulary.ATTR_IS_A);
                body = byId(schema, SchematronVocabulary.PATTERN, name);
                if (body == null) {
                    unsupported(Messages.get(MessageKey.ABSTRACT_PATTERN_MISSING, name), "", "", ids.get(pattern), "");
                    continue;
                }
                Map<String, String> p = new LinkedHashMap<>();
                for (Element param : children(pattern, PARAM)) p.put(param.getAttribute(XsdVocabulary.ATTR_NAME), param.getAttribute(ATTR_VALUE));
                params = p;
            }
            pattern(body, ids.get(pattern));
        }
        boolean valid = problems.stream().noneMatch(p -> Severity.ERROR.equals(p.severity()));
        return new Result(valid, problems, truncated, phases, phase, checked);
    }

    /** Runs the rules of a pattern (its own, or those of the abstract pattern it instantiates): a node fires the first rule whose context matches it. */
    private void pattern(Element pattern, String patternId) {
        try {
            lets(pattern, instance);
        } catch (XPathExpressionException e) {
            unsupported(e.getMessage(), "", "", patternId, "");
            return;
        }
        Scope patternScope = scope;
        Set<Node> fired = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Element rule : children(pattern, SchematronVocabulary.RULE)) {
            if (TRUE.equals(rule.getAttribute(ATTR_ABSTRACT)) || !rule.hasAttribute(SchematronVocabulary.ATTR_CONTEXT)) continue;
            String ruleId = ids.get(rule);
            String context = substitute(rule.getAttribute(SchematronVocabulary.ATTR_CONTEXT));
            NodeList nodes;
            try {
                nodes = (NodeList) compile(selection(context)).evaluate(instance, XPathConstants.NODESET);
            } catch (XPathExpressionException e) {
                unsupported(e.getMessage(), "", ruleId, patternId, context);
                continue;
            }
            List<Element> assertions = new ArrayList<>();
            List<Element> lets = new ArrayList<>();
            collect(rule, pattern.getOwnerDocument().getDocumentElement(), assertions, lets, new ArrayList<>());
            for (int i = 0; i < nodes.getLength() && !truncated; i++) {
                Node node = nodes.item(i);
                if (!fired.add(node)) continue;
                scope = patternScope.child();
                try {
                    for (Element let : lets) let(let, node);
                } catch (XPathExpressionException e) {
                    unsupported(e.getMessage(), "", ruleId, patternId, context);
                    break;
                }
                for (Element a : assertions) assertion(a, node, ruleId, patternId);
            }
        }
    }

    /** The assertions and lets of a rule: those of the abstract rules it extends first (recursively, each once), then its own. */
    private void collect(Element rule, Element schema, List<Element> assertions, List<Element> lets, List<Element> visited) {
        if (visited.contains(rule)) return;
        visited.add(rule);
        for (Element c : children(rule)) {
            switch (c.getLocalName()) {
                case SchematronVocabulary.EXTENDS -> {
                    Element base = c.hasAttribute(SchematronVocabulary.ATTR_RULE) ? byId(schema, SchematronVocabulary.RULE, c.getAttribute(SchematronVocabulary.ATTR_RULE)) : null;
                    if (base != null) collect(base, schema, assertions, lets, visited);
                    else unsupported(Messages.get(MessageKey.ABSTRACT_RULE_MISSING, c.getAttribute(SchematronVocabulary.ATTR_RULE)), "", ids.get(rule), "", "");
                }
                case LET -> lets.add(c);
                case SchematronVocabulary.ASSERT, SchematronVocabulary.REPORT -> assertions.add(c);
                default -> { }
            }
        }
    }

    private void assertion(Element a, Node node, String ruleId, String patternId) {
        String test = substitute(a.getAttribute(SchematronVocabulary.ATTR_TEST));
        String id = ids.get(a);
        boolean value;
        try {
            value = (Boolean) compile(test).evaluate(node, XPathConstants.BOOLEAN);
        } catch (XPathExpressionException e) {
            unsupported(e.getMessage(), id, ruleId, patternId, test);
            return;
        }
        checked++;
        boolean fires = SchematronVocabulary.REPORT.equals(a.getLocalName()) == value;
        if (!fires) return;
        String role = a.hasAttribute(SchematronVocabulary.ATTR_ROLE) ? a.getAttribute(SchematronVocabulary.ATTR_ROLE)
                : a.getAttribute(SchematronVocabulary.ATTR_FLAG);
        add(new Problem(severity(role), LocatedDocument.line(node), LocatedDocument.column(node), message(a, node), location(node), id, ruleId, patternId, test));
    }

    /** An expression that could not be evaluated: reported once, at no line, with the engine's message. */
    private void unsupported(String why, String assertionId, String ruleId, String patternId, String expression) {
        if (problems.stream().anyMatch(p -> Severity.UNSUPPORTED.equals(p.severity()) && p.test().equals(expression) && p.rule().equals(ruleId))) return;
        add(new Problem(Severity.UNSUPPORTED, 0, 0, why == null ? "" : why, "", assertionId, ruleId, patternId, expression));
    }

    private void add(Problem p) {
        if (problems.size() >= MAX_PROBLEMS) { truncated = true; return; }
        problems.add(p);
    }

    private static String severity(String role) {
        String r = role.toLowerCase(Locale.ROOT);
        if (r.contains(WARNING_ROLE)) return Severity.WARNING;
        if (r.contains(INFO_ROLE)) return Severity.INFO;
        return Severity.ERROR;
    }

    /** The {@code let}s that are direct children of {@code e}, evaluated on {@code context} into the current scope. */
    private void lets(Element e, Node context) throws XPathExpressionException {
        scope = scope.child();
        for (Element let : children(e, LET)) let(let, context);
    }

    private void let(Element let, Node context) throws XPathExpressionException {
        String name = let.getAttribute(XsdVocabulary.ATTR_NAME);
        if (!let.hasAttribute(ATTR_VALUE)) { scope.variables().put(name, let.getTextContent()); return; }
        XPathExpression expr = compile(substitute(let.getAttribute(ATTR_VALUE)));
        XPathEvaluationResult<?> r = expr.evaluateExpression(context);
        Object value = switch (r.type()) {
            case NODESET -> expr.evaluate(context, XPathConstants.NODESET);   // as a NodeList, which the engine takes back as a variable
            default -> r.value();
        };
        scope.variables().put(name, value);
    }

    private XPathExpression compile(String expression) throws XPathExpressionException {
        XPathExpression e = compiled.get(expression);
        if (e == null) {
            e = xpath.compile(expression);
            compiled.put(expression, e);
        }
        return e;
    }

    /** {@code $name} replaced by the value of the abstract pattern's parameter, longest names first. */
    private String substitute(String expression) {
        if (params.isEmpty()) return expression;
        String s = expression;
        List<String> names = new ArrayList<>(params.keySet());
        names.sort((a, b) -> b.length() - a.length());
        for (String n : names) s = s.replaceAll("\\$" + Pattern.quote(n) + "(?![\\w.-])", Matcher.quoteReplacement(params.get(n)));
        return s;
    }

    /** A rule's context (an XSLT-style pattern) as a selection from the root: each alternative not starting at the root gets {@code //}. */
    static String selection(String context) {
        StringBuilder sb = new StringBuilder();
        for (String alt : splitUnion(context)) {
            if (sb.length() > 0) sb.append(' ').append(UNION).append(' ');
            String a = alt.trim();
            sb.append(a.startsWith(ROOT) ? a : DESCENDANTS + a);
        }
        return sb.toString();
    }

    /** The top-level alternatives of a union: {@code |} outside brackets, parentheses and quotes. */
    private static List<String> splitUnion(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) { if (c == quote) quote = 0; continue; }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '[' || c == '(') depth++;
            else if (c == ']' || c == ')') depth--;
            else if (c == '|' && depth == 0) { out.add(s.substring(start, i)); start = i + 1; }
        }
        out.add(s.substring(start));
        return out;
    }

    /** The message of an assertion on {@code node}: its text, a {@code value-of} evaluated, a {@code name} the node's name. */
    private String message(Element a, Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node n = a.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(n.getNodeValue());
            } else if (n instanceof Element c) {
                switch (c.getLocalName()) {
                    case SchematronVocabulary.VALUE_OF -> {
                        String select = substitute(c.getAttribute(SchematronVocabulary.ATTR_SELECT));
                        try {
                            sb.append(compile(select).evaluate(node, XPathConstants.STRING));
                        } catch (XPathExpressionException e) {
                            sb.append(String.format(PLACEHOLDER, select));
                        }
                    }
                    case SchematronVocabulary.NAME -> {
                        Node named = node;
                        if (c.hasAttribute(SchematronVocabulary.ATTR_PATH)) {
                            try {
                                named = (Node) compile(substitute(c.getAttribute(SchematronVocabulary.ATTR_PATH))).evaluate(node, XPathConstants.NODE);
                            } catch (XPathExpressionException e) {
                                named = null;
                            }
                        }
                        sb.append(named == null ? "" : named.getNodeName());
                    }
                    case EMPH, SPAN, DIR -> sb.append(message(c, node));
                    default -> { }
                }
            }
        }
        return sb.toString().replaceAll(WHITESPACE, " ").trim();
    }

    /** The path of a node in the document: {@code /po:purchaseOrder/po:items/po:item[2]/po:quantity}, {@code .../@partNum} for an attribute. */
    static String location(Node node) {
        if (node instanceof Attr a) return location(a.getOwnerElement()) + ATTRIBUTE_STEP + a.getNodeName();
        Element e = LocatedDocument.elementOf(node);
        if (e == null) return ROOT;
        StringBuilder sb = new StringBuilder();
        for (Element x = e; x != null; x = x.getParentNode() instanceof Element p ? p : null) {
            int index = 1, count = 0;
            for (Node s = x.getParentNode() == null ? null : x.getParentNode().getFirstChild(); s != null; s = s.getNextSibling()) {
                if (s.getNodeType() != Node.ELEMENT_NODE || !s.getNodeName().equals(x.getNodeName())) continue;
                count++;
                if (s == x) index = count;
            }
            sb.insert(0, ROOT + x.getNodeName() + (count > 1 ? "[" + index + "]" : ""));
        }
        return sb.toString();
    }

    /** Replaces each {@code include} under {@code e} by the root of the file it names, read next to the Schematron (files only). */
    private void includes(Element e, int depth) throws IOException {
        for (Element inc : new ArrayList<>(descendants(e, SchematronVocabulary.INCLUDE))) {
            String href = inc.getAttribute(SchematronVocabulary.ATTR_HREF);
            Path target = file.resolveSibling(href).normalize();
            if (href.isEmpty() || href.contains(REMOTE_LOCATION_MARK) || !Files.isRegularFile(target) || depth >= MAX_INCLUDE_DEPTH) {
                unsupported(Messages.get(MessageKey.INCLUDE_NOT_FOUND, href), "", "", "", "");
                continue;
            }
            Element root;
            try {
                root = SecureXmlFactories.newDocumentBuilder().parse(target.toFile()).getDocumentElement();
            } catch (Exception ex) {
                unsupported(ex.getMessage(), "", "", "", "");
                continue;
            }
            Node imported = inc.getOwnerDocument().importNode(root, true);
            inc.getParentNode().replaceChild(imported, inc);
            if (imported instanceof Element ie) includes(ie, depth + 1);
        }
    }

    private static Element byId(Element schema, String localName, String id) {
        for (Element e : descendants(schema, localName)) if (id.equals(e.getAttribute(SchematronVocabulary.ATTR_ID))) return e;
        return null;
    }

    private static List<Element> descendants(Element e, String localName) {
        List<Element> out = new ArrayList<>();
        for (Element c : children(e)) {
            if (localName.equals(c.getLocalName())) out.add(c);
            out.addAll(descendants(c, localName));
        }
        return out;
    }

    private static List<Element> children(Element e) {
        return XsdParser.children(e).stream().filter(c -> c.getNamespaceURI() != null && SchematronVocabulary.NAMESPACES.contains(c.getNamespaceURI())).toList();
    }

    private static List<Element> children(Element e, String localName) {
        return children(e).stream().filter(c -> localName.equals(c.getLocalName())).toList();
    }
}
