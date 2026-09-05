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

/** The XML Schema namespace and the local names of the elements and attributes this tool reads. */
public final class XsdVocabulary {

    private XsdVocabulary() {}

    public static final String NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    // elements
    public static final String SCHEMA = "schema";
    public static final String IMPORT = "import";
    public static final String INCLUDE = "include";
    public static final String REDEFINE = "redefine";
    public static final String ELEMENT = "element";
    public static final String COMPLEX_TYPE = "complexType";
    public static final String SIMPLE_TYPE = "simpleType";
    public static final String GROUP = "group";
    public static final String ATTRIBUTE_GROUP = "attributeGroup";
    public static final String ATTRIBUTE = "attribute";
    public static final String ANNOTATION = "annotation";
    public static final String DOCUMENTATION = "documentation";
    public static final String EXTENSION = "extension";
    public static final String RESTRICTION = "restriction";
    public static final String SIMPLE_CONTENT = "simpleContent";
    public static final String COMPLEX_CONTENT = "complexContent";
    public static final String ENUMERATION = "enumeration";
    public static final String LIST = "list";
    public static final String UNION = "union";
    public static final String SEQUENCE = "sequence";
    public static final String CHOICE = "choice";
    public static final String ALL = "all";
    public static final String ANY = "any";
    public static final String ANY_ATTRIBUTE = "anyAttribute";
    public static final String KEY = "key";
    public static final String UNIQUE = "unique";
    public static final String KEYREF = "keyref";

    // attributes
    public static final String ATTR_NAME = "name";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_REF = "ref";
    public static final String ATTR_BASE = "base";
    public static final String ATTR_ITEM_TYPE = "itemType";
    public static final String ATTR_MEMBER_TYPES = "memberTypes";
    public static final String ATTR_SUBSTITUTION_GROUP = "substitutionGroup";
    public static final String ATTR_TARGET_NAMESPACE = "targetNamespace";
    public static final String ATTR_NAMESPACE = "namespace";
    public static final String ATTR_SCHEMA_LOCATION = "schemaLocation";
    public static final String ATTR_MIN_OCCURS = "minOccurs";
    public static final String ATTR_MAX_OCCURS = "maxOccurs";
    public static final String MAX_OCCURS_UNBOUNDED = "unbounded";
    public static final String ATTR_USE = "use";
    public static final String ATTR_VALUE = "value";
    public static final String ATTR_REFER = "refer";
    /** A wildcard's namespace constraint ({@code ##any} when absent). */
    public static final String NAMESPACE_ANY = "##any";
    public static final String USE_REQUIRED = "required";
    public static final String USE_PROHIBITED = "prohibited";

    /** Separator between the prefix and the local part of a qualified name ({@code xs:string}). */
    public static final char QNAME_SEPARATOR = ':';
}
