package org.jtools.xsdviewer.schema;

import java.util.Set;

/**
 * The kinds of node of a {@link SchemaGraph}. A global declaration has the kind of the XSD
 * element that declares it; two more kinds stand for what the file does not declare.
 */
public final class NodeKind {

    private NodeKind() {}

    public static final String ELEMENT = XsdVocabulary.ELEMENT;
    public static final String COMPLEX_TYPE = XsdVocabulary.COMPLEX_TYPE;
    public static final String SIMPLE_TYPE = XsdVocabulary.SIMPLE_TYPE;
    public static final String GROUP = XsdVocabulary.GROUP;
    public static final String ATTRIBUTE_GROUP = XsdVocabulary.ATTRIBUTE_GROUP;
    public static final String ATTRIBUTE = XsdVocabulary.ATTRIBUTE;
    /** A built-in XSD type (xs:string, xs:int...). */
    public static final String BUILTIN = "builtin";
    /** An object referenced but not declared in this file (imported / included). */
    public static final String EXTERNAL = "external";

    /**
     * Kind used in the id of a reference to a named type ({@code type:X}) while it is not known
     * whether X is a complexType or a simpleType; stays in the id of an external type.
     */
    public static final String TYPE_REFERENCE = "type";

    /** The kinds of global declaration (the XSD elements allowed under xs:schema that have a name). */
    public static final Set<String> GLOBAL_DECLARATIONS =
            Set.of(ELEMENT, COMPLEX_TYPE, SIMPLE_TYPE, GROUP, ATTRIBUTE_GROUP, ATTRIBUTE);
}
