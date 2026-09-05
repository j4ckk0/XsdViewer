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
import java.util.Set;

/**
 * The names the Schematron specification defines, as they are written in a rule file: the elements
 * of the ISO Schematron namespace (the older Schematron 1.5 namespace is read the same way) and
 * their attributes — the words of the language being parsed, never a text shown to a reader.
 */
public final class SchematronNames {

    private SchematronNames() {}

    /** ISO/IEC 19757-3. */
    public static final String NAMESPACE = "http://purl.oclc.org/dsdl/schematron";
    /** Schematron 1.5, the pre-ISO vocabulary: the same elements, a pattern named by {@code name}. */
    public static final String NAMESPACE_1_5 = "http://www.ascc.net/xml/schematron";
    public static final Set<String> NAMESPACES = Set.of(NAMESPACE, NAMESPACE_1_5);

    // elements
    public static final String SCHEMA = "schema";
    public static final String TITLE = "title";
    public static final String PARAGRAPH = "p";
    public static final String INCLUDE = "include";
    public static final String PHASE = "phase";
    public static final String ACTIVE = "active";
    public static final String PATTERN = "pattern";
    public static final String RULE = "rule";
    public static final String EXTENDS = "extends";
    public static final String ASSERT = "assert";
    public static final String REPORT = "report";
    public static final String DIAGNOSTICS = "diagnostics";
    public static final String DIAGNOSTIC = "diagnostic";
    /** A namespace prefix declared for the expressions; a variable; a parameter of an abstract pattern. */
    public static final String NS = "ns";
    public static final String LET = "let";
    public static final String PARAM = "param";
    /** Inside a message: the value of an expression, the name of the context node, marked-up text. */
    public static final String VALUE_OF = "value-of";
    public static final String NAME = "name";
    public static final String EMPH = "emph";
    public static final String SPAN = "span";
    public static final String DIR = "dir";

    // attributes (name is XsdNames's)
    public static final String ATTR_ID = "id";
    public static final String ATTR_CONTEXT = "context";
    public static final String ATTR_TEST = "test";
    public static final String ATTR_PATTERN = "pattern";
    public static final String ATTR_RULE = "rule";
    public static final String ATTR_IS_A = "is-a";
    public static final String ATTR_DIAGNOSTICS = "diagnostics";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_FLAG = "flag";
    public static final String ATTR_HREF = "href";
    public static final String ATTR_SELECT = "select";
    public static final String ATTR_PATH = "path";
    public static final String ATTR_ABSTRACT = "abstract";
    public static final String ATTR_DEFAULT_PHASE = "defaultPhase";
    /** Of an {@code ns}: the prefix and the namespace it stands for. */
    public static final String ATTR_PREFIX = "prefix";
    public static final String ATTR_URI = "uri";
    /** Of a {@code let} or a {@code param}: what it is worth (an expression for a {@code let}). */
    public static final String ATTR_VALUE = "value";
    public static final String TRUE = "true";
}
