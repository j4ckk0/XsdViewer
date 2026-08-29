package org.jtools.xsdviewer.schema;

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
    public static final String LIST = "list";
    public static final String UNION = "union";

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

    /** Separator between the prefix and the local part of a qualified name ({@code xs:string}). */
    public static final char QNAME_SEPARATOR = ':';
}
