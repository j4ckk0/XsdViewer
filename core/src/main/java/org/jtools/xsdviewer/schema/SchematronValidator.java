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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

/**
 * Checks an XML document against a Schematron with the JDK's XPath 1.0 engine: phases, abstract
 * patterns and their parameters, abstract rules ({@code extends}), {@code let} variables,
 * {@code include}s next to the file ({@link SchematronIncludes}), the messages with {@code value-of}
 * and {@code name} filled in and the diagnostics an assertion names appended ({@link SchematronMessage}).
 * Each node fires at most one rule per pattern (the first whose context matches, as in ISO
 * Schematron). An expression the engine cannot compile — XPath 2 and later — is reported once as
 * {@link Severity#UNSUPPORTED}, and nothing is checked there. A problem names the assertion, rule
 * and pattern as {@link SchematronParser} names them, so the page can select them.
 * <p>
 * What changes as the evaluation goes down the schema — the variables in force, the parameters of
 * the abstract pattern being instantiated — travels as a {@link Frame}; the validator itself only
 * accumulates the problems.
 */
public final class SchematronValidator {

    /** The phase running every pattern (the ISO name). */
    public static final String ALL_PHASES = "#ALL";
    /** Problems beyond this many are not collected. */
    static final int MAX_PROBLEMS = 200;
    private static final String WARNING_ROLE = "warn", INFO_ROLE = "info";
    private static final String UNION = "|", DESCENDANTS = "//", ROOT = "/";
    private static final String WHITESPACE = "\\s+";
    /** Between an assertion's message and each diagnostic it names. */
    private static final String DIAGNOSTIC_SEPARATOR = " — ";

    /**
     * A firing assertion (or a failure to evaluate one): where in the document ({@code line},
     * {@code column}, {@code location}: the node's path), the message, and what fired
     * ({@code assertion}, {@code rule}, {@code pattern}: node ids of the Schematron's graph; {@code test}).
     */
    public record Problem(String severity, int line, int column, String message, String location,
                          String assertion, String rule, String pattern, String test) {}

    /** The outcome: valid when no assertion of error severity fired; the phases the schema declares and the one run; how many tests were evaluated. */
    public record Result(boolean valid, List<Problem> problems, boolean truncated, List<String> phases, String phase, int checked) {}

    /**
     * What an expression is evaluated with at one point of the schema: the {@code let} variables in
     * force (innermost last) and the parameters of the abstract pattern being instantiated
     * ({@code $name} → value; none outside one). Immutable: a level of the schema gets its own.
     */
    private record Frame(Map<String, Object> variables, Map<String, String> params) {
        static final Frame ROOT = new Frame(Map.of(), Map.of());

        Frame with(String variable, Object value) {
            Map<String, Object> v = new LinkedHashMap<>(variables);
            v.put(variable, value);
            return new Frame(v, params);
        }

        Frame withParams(Map<String, String> p) {
            return new Frame(variables, p);
        }

        /** {@code $name} replaced by the parameter's value, longest names first. */
        String substitute(String expression) {
            if (params.isEmpty()) return expression;
            String s = expression;
            List<String> names = new ArrayList<>(params.keySet());
            names.sort((a, b) -> b.length() - a.length());
            for (String n : names) s = s.replaceAll("\\$" + Pattern.quote(n) + "(?![\\w.-])", Matcher.quoteReplacement(params.get(n)));
            return s;
        }
    }

    private final Element schema;
    private final Document instance;
    private final XPath xpath = XPathFactory.newInstance().newXPath();
    private final Map<String, String> prefixes = new HashMap<>();
    private final Map<String, XPathExpression> compiled = new HashMap<>();
    /** The node id of each declaring element ({@link SchematronParser#elementIds}). */
    private final Map<Element, String> ids;
    /** The frame of the expression being evaluated: what the variable resolver reads. */
    private Frame evaluating = Frame.ROOT;
    private final List<Problem> problems = new ArrayList<>();
    private boolean truncated;
    private int checked;

    private SchematronValidator(Element schema, Document instance, Map<Element, String> ids) {
        this.schema = schema;
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
        xpath.setXPathVariableResolver(name -> evaluating.variables().get(name.getLocalPart()));
        for (Element ns : SchematronDom.children(schema, SchematronVocabulary.NS)) {
            prefixes.put(ns.getAttribute(SchematronVocabulary.ATTR_PREFIX), ns.getAttribute(SchematronVocabulary.ATTR_URI));
        }
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
        Element root = SecureXmlFactories.newDocumentBuilder().parse(schematronFile.toFile()).getDocumentElement();
        if (!SchematronDom.isSchematron(root) || !SchematronVocabulary.SCHEMA.equals(root.getLocalName())) {
            throw new IllegalArgumentException(Messages.get(MessageKey.NOT_A_SCHEMATRON, root.getTagName()));
        }
        Document instance = LocatedDocument.parse(xml);
        List<String> unread = SchematronIncludes.resolve(root, schematronFile);
        SchematronValidator v = new SchematronValidator(root, instance, SchematronParser.elementIds(root));
        for (String why : unread) v.unsupported(why, "", "", "", "");
        return v.run(phase);
    }

    private Result run(String phase) throws XPathExpressionException {
        List<String> phases = SchematronDom.children(schema, SchematronVocabulary.PHASE).stream()
                .map(p -> p.getAttribute(SchematronVocabulary.ATTR_ID)).filter(id -> !id.isEmpty()).toList();
        if (phase == null || phase.isEmpty()) {
            phase = schema.hasAttribute(SchematronVocabulary.ATTR_DEFAULT_PHASE) ? schema.getAttribute(SchematronVocabulary.ATTR_DEFAULT_PHASE) : ALL_PHASES;
        }
        Set<String> active = null;
        if (!ALL_PHASES.equals(phase)) {
            Element p = SchematronDom.byId(schema, SchematronVocabulary.PHASE, phase);
            if (p == null) throw new IllegalArgumentException(Messages.get(MessageKey.PHASE_UNKNOWN, phase));
            active = SchematronDom.children(p, SchematronVocabulary.ACTIVE).stream().map(a -> a.getAttribute(SchematronVocabulary.ATTR_PATTERN)).collect(Collectors.toSet());
        }
        Frame frame = lets(schema, instance, Frame.ROOT);
        for (Element pattern : SchematronDom.children(schema, SchematronVocabulary.PATTERN)) {
            if (isAbstract(pattern)) continue;
            if (active != null && !active.contains(pattern.getAttribute(SchematronVocabulary.ATTR_ID))) continue;
            String patternId = ids.get(pattern);
            Element body = pattern;
            Frame own = frame;
            if (pattern.hasAttribute(SchematronVocabulary.ATTR_IS_A)) {   // an instance of an abstract pattern: its rules, with the parameters
                String name = pattern.getAttribute(SchematronVocabulary.ATTR_IS_A);
                body = SchematronDom.byId(schema, SchematronVocabulary.PATTERN, name);
                if (body == null) {
                    unsupported(Messages.get(MessageKey.ABSTRACT_PATTERN_MISSING, name), "", "", patternId, "");
                    continue;
                }
                Map<String, String> params = new LinkedHashMap<>();
                for (Element param : SchematronDom.children(pattern, SchematronVocabulary.PARAM)) {
                    params.put(param.getAttribute(XsdVocabulary.ATTR_NAME), param.getAttribute(SchematronVocabulary.ATTR_VALUE));
                }
                own = frame.withParams(params);
            }
            pattern(body, patternId, own);
        }
        boolean valid = problems.stream().noneMatch(p -> Severity.ERROR.equals(p.severity()));
        return new Result(valid, problems, truncated, phases, phase, checked);
    }

    /** Runs the rules of a pattern (its own, or those of the abstract pattern it instantiates): a node fires the first rule whose context matches it. */
    private void pattern(Element pattern, String patternId, Frame frame) {
        try {
            frame = lets(pattern, instance, frame);
        } catch (XPathExpressionException e) {
            unsupported(e.getMessage(), "", "", patternId, "");
            return;
        }
        Set<Node> fired = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Element rule : SchematronDom.children(pattern, SchematronVocabulary.RULE)) {
            if (isAbstract(rule) || !rule.hasAttribute(SchematronVocabulary.ATTR_CONTEXT)) continue;
            String ruleId = ids.get(rule);
            String context = frame.substitute(rule.getAttribute(SchematronVocabulary.ATTR_CONTEXT));
            NodeList nodes;
            try {
                nodes = (NodeList) evaluate(selection(context), instance, XPathConstants.NODESET, frame);
            } catch (XPathExpressionException e) {
                unsupported(e.getMessage(), "", ruleId, patternId, context);
                continue;
            }
            List<Element> assertions = new ArrayList<>(), lets = new ArrayList<>();
            collect(rule, assertions, lets, new ArrayList<>());
            for (int i = 0; i < nodes.getLength() && !truncated; i++) {
                Node node = nodes.item(i);
                if (!fired.add(node)) continue;
                Frame at;
                try {
                    at = lets(lets, node, frame);
                } catch (XPathExpressionException e) {
                    unsupported(e.getMessage(), "", ruleId, patternId, context);
                    break;
                }
                for (Element a : assertions) assertion(a, node, ruleId, patternId, at);
            }
        }
    }

    /** The assertions and lets of a rule: those of the abstract rules it extends first (recursively, each once), then its own. */
    private void collect(Element rule, List<Element> assertions, List<Element> lets, List<Element> visited) {
        if (visited.contains(rule)) return;
        visited.add(rule);
        for (Element c : SchematronDom.children(rule)) {
            switch (c.getLocalName()) {
                case SchematronVocabulary.EXTENDS -> {
                    String name = c.getAttribute(SchematronVocabulary.ATTR_RULE);
                    Element base = name.isEmpty() ? null : SchematronDom.byId(schema, SchematronVocabulary.RULE, name);
                    if (base != null) collect(base, assertions, lets, visited);
                    else unsupported(Messages.get(MessageKey.ABSTRACT_RULE_MISSING, name), "", ids.get(rule), "", "");
                }
                case SchematronVocabulary.LET -> lets.add(c);
                case SchematronVocabulary.ASSERT, SchematronVocabulary.REPORT -> assertions.add(c);
                default -> { }
            }
        }
    }

    private void assertion(Element a, Node node, String ruleId, String patternId, Frame frame) {
        String test = frame.substitute(a.getAttribute(SchematronVocabulary.ATTR_TEST));
        String id = ids.get(a);
        boolean value;
        try {
            value = (Boolean) evaluate(test, node, XPathConstants.BOOLEAN, frame);
        } catch (XPathExpressionException e) {
            unsupported(e.getMessage(), id, ruleId, patternId, test);
            return;
        }
        checked++;
        boolean fires = SchematronVocabulary.REPORT.equals(a.getLocalName()) == value;
        if (!fires) return;
        String role = a.hasAttribute(SchematronVocabulary.ATTR_ROLE) ? a.getAttribute(SchematronVocabulary.ATTR_ROLE) : a.getAttribute(SchematronVocabulary.ATTR_FLAG);
        StringBuilder message = new StringBuilder(message(a, node, frame));
        // the diagnostics the assertion names, rendered on the same node, follow the message
        for (String d : a.getAttribute(SchematronVocabulary.ATTR_DIAGNOSTICS).trim().split(WHITESPACE)) {
            Element diagnostic = d.isEmpty() ? null : SchematronDom.byId(schema, SchematronVocabulary.DIAGNOSTIC, d);
            if (diagnostic != null) message.append(DIAGNOSTIC_SEPARATOR).append(message(diagnostic, node, frame));
        }
        add(new Problem(severity(role), LocatedDocument.line(node), LocatedDocument.column(node), message.toString(), LocatedDocument.location(node), id, ruleId, patternId, test));
    }

    /** The message of an assertion or diagnostic on {@code node}: its {@code value-of}s evaluated there, a {@code name} the node's name (or the named node's). */
    private String message(Element e, Node node, Frame frame) {
        return SchematronMessage.render(e, new SchematronMessage.Leaf() {
            @Override
            public String valueOf(String select) {
                String expression = frame.substitute(select);
                try {
                    return (String) evaluate(expression, node, XPathConstants.STRING, frame);
                } catch (XPathExpressionException ex) {
                    return SchematronMessage.PLACEHOLDERS.valueOf(expression);
                }
            }

            @Override
            public String name(String path) {
                if (path.isEmpty()) return node.getNodeName();
                try {
                    Node named = (Node) evaluate(frame.substitute(path), node, XPathConstants.NODE, frame);
                    return named == null ? "" : named.getNodeName();
                } catch (XPathExpressionException ex) {
                    return "";
                }
            }
        });
    }

    /** An expression that could not be evaluated (or a piece of the schema that could not be read): reported once, at no line, with the reason. */
    private void unsupported(String why, String assertionId, String ruleId, String patternId, String expression) {
        String reason = why == null ? "" : why;
        if (problems.stream().anyMatch(p -> Severity.UNSUPPORTED.equals(p.severity()) && p.test().equals(expression) && Objects.equals(p.rule(), ruleId) && p.message().equals(reason))) return;
        add(new Problem(Severity.UNSUPPORTED, 0, 0, reason, "", assertionId, ruleId, patternId, expression));
    }

    private void add(Problem p) {
        if (problems.size() >= MAX_PROBLEMS) { truncated = true; return; }
        problems.add(p);
    }

    private static boolean isAbstract(Element e) {
        return SchematronVocabulary.TRUE.equals(e.getAttribute(SchematronVocabulary.ATTR_ABSTRACT));
    }

    private static String severity(String role) {
        String r = role.toLowerCase(Locale.ROOT);
        if (r.contains(WARNING_ROLE)) return Severity.WARNING;
        if (r.contains(INFO_ROLE)) return Severity.INFO;
        return Severity.ERROR;
    }

    /** {@code frame} plus the {@code let}s that are direct children of {@code e}, evaluated on {@code context}. */
    private Frame lets(Element e, Node context, Frame frame) throws XPathExpressionException {
        return lets(SchematronDom.children(e, SchematronVocabulary.LET), context, frame);
    }

    private Frame lets(List<Element> lets, Node context, Frame frame) throws XPathExpressionException {
        for (Element let : lets) {
            String name = let.getAttribute(XsdVocabulary.ATTR_NAME);
            if (!let.hasAttribute(SchematronVocabulary.ATTR_VALUE)) {
                frame = frame.with(name, let.getTextContent());
                continue;
            }
            XPathExpression expr = compile(frame.substitute(let.getAttribute(SchematronVocabulary.ATTR_VALUE)));
            evaluating = frame;
            XPathEvaluationResult<?> r = expr.evaluateExpression(context);
            Object value = switch (r.type()) {
                case NODESET -> expr.evaluate(context, XPathConstants.NODESET);   // as a NodeList, which the engine takes back as a variable
                default -> r.value();
            };
            frame = frame.with(name, value);
        }
        return frame;
    }

    /** Evaluates {@code expression} on {@code context} with the variables of {@code frame}; the result is of {@code type} ({@link XPathConstants}). */
    private Object evaluate(String expression, Node context, QName type, Frame frame) throws XPathExpressionException {
        evaluating = frame;
        return compile(expression).evaluate(context, type);
    }

    private XPathExpression compile(String expression) throws XPathExpressionException {
        XPathExpression e = compiled.get(expression);
        if (e == null) {
            e = xpath.compile(expression);
            compiled.put(expression, e);
        }
        return e;
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
}
