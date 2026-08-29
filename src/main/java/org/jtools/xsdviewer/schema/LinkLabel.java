package org.jtools.xsdviewer.schema;

/**
 * Labels of the edges of a {@link SchemaGraph}: the nature of the reference from one declaration
 * to another. They are part of the model handed to the page (not user-interface texts), and mirror
 * the XSD vocabulary; a nested element's type link is labelled with the element's name instead.
 */
public final class LinkLabel {

    private LinkLabel() {}

    /** A global element / attribute to its declared type. */
    public static final String TYPE = "type";
    /** {@code <xs:element ref="..."/>} inside a declaration. */
    public static final String REF = "ref";
    /** {@code <xs:attribute ref="..."/>} inside a declaration. */
    public static final String ATTRIBUTE_REF = "attribute ref";
    /** A global element to the head of its substitution group. */
    public static final String SUBSTITUTES = "substitutes";
    /** {@code <xs:group ref="..."/>} */
    public static final String GROUP = "group";
    /** {@code <xs:attributeGroup ref="..."/>} */
    public static final String ATTRIBUTE_GROUP = "attributeGroup";
    public static final String EXTENDS = "extends";
    public static final String RESTRICTS = "restricts";
    public static final String LIST_OF = "list of";
    public static final String UNION_OF = "union of";

    private static final String ATTRIBUTE_PREFIX = "attribute ";

    /** A nested attribute {@code name} to its type: {@code "attribute name"}. */
    public static String attribute(String name) {
        return ATTRIBUTE_PREFIX + name;
    }
}
