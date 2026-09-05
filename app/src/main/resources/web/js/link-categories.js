/**
 * What a link is: the family it belongs to (a WSDL's service chain, a Schematron's rules, or — no
 * family — the schema's own structure), whether it is a derivation, and the category the graph's
 * *Links* menu switches it on and off by. Every question of the shape "what kind of link is this"
 * is answered here; the vocabulary itself, the labels and the kinds of node, is in constants.js.
 */
import { LINK_LABEL, SCHEMATRON_KINDS, WSDL_KINDS } from './constants.js';

/**
 * The family a kind belongs to: the service description of a WSDL, the rules of a Schematron, or
 * (null) the XML Schema objects — which a WSDL's inline schemas and a Schematron's targets also are.
 * The graph draws a family's objects and the links to them apart from the schema's own.
 */
export const FAMILY = { WSDL: 'wsdl', SCHEMATRON: 'schematron' };
export const familyOf = (kind) => (WSDL_KINDS.has(kind) ? FAMILY.WSDL : SCHEMATRON_KINDS.has(kind) ? FAMILY.SCHEMATRON : null);
/** The family of a link between {@code fromKind} and {@code toKind}: a link is the family's as soon as one of its ends is. */
export const linkFamily = (fromKind, toKind) => familyOf(fromKind) || familyOf(toKind);

/** The words that name a link of a family's chain (a port's or a part's name is a name, not one of these). */
const FAMILY_LINK_LABELS = {
  [FAMILY.WSDL]: new Set([LINK_LABEL.OPERATION, LINK_LABEL.INPUT, LINK_LABEL.OUTPUT, LINK_LABEL.FAULT, LINK_LABEL.BINDS]),
  [FAMILY.SCHEMATRON]: new Set([LINK_LABEL.ACTIVE, LINK_LABEL.RULE, LINK_LABEL.IS_A, LINK_LABEL.ASSERT, LINK_LABEL.REPORT, LINK_LABEL.DIAGNOSTIC]),
};
/** The family whose chain uses {@code label} as a word, or null. */
export const labelFamily = (label) => (FAMILY_LINK_LABELS[FAMILY.WSDL].has(label) ? FAMILY.WSDL
  : FAMILY_LINK_LABELS[FAMILY.SCHEMATRON].has(label) ? FAMILY.SCHEMATRON : null);

export const STRUCTURAL_LINK_LABELS = new Set([
  LINK_LABEL.TYPE, LINK_LABEL.REF, LINK_LABEL.ATTRIBUTE_REF, LINK_LABEL.SUBSTITUTES, LINK_LABEL.GROUP,
  LINK_LABEL.ATTRIBUTE_GROUP, LINK_LABEL.EXTENDS, LINK_LABEL.RESTRICTS, LINK_LABEL.LIST_OF, LINK_LABEL.UNION_OF,
  LINK_LABEL.OPERATION, LINK_LABEL.INPUT, LINK_LABEL.OUTPUT, LINK_LABEL.FAULT, LINK_LABEL.BINDS,
  LINK_LABEL.ACTIVE, LINK_LABEL.RULE, LINK_LABEL.IS_A, LINK_LABEL.ASSERT, LINK_LABEL.REPORT, LINK_LABEL.DIAGNOSTIC,
]);
/**
 * The categories a link falls into, what the graph's *Links* menu switches on and off: what a
 * declaration contains, its attributes, the type it is, the type it derives from, what it names,
 * and the chain of a WSDL or a Schematron.
 */
const LINK_CATEGORY = {
  CONTENT: 'content', ATTRIBUTE: 'attribute', TYPE: 'type', DERIVATION: 'derivation', REFERENCE: 'reference', CHAIN: 'chain',
};
export const LINK_CATEGORIES = Object.values(LINK_CATEGORY);

/** Edge labels of a derivation (a type to its base type, a Schematron pattern or rule to the abstract one it builds on): drawn with a hollow arrowhead, as a UML generalisation. */
const DERIVATION_LINK_LABELS = new Set([LINK_LABEL.EXTENDS, LINK_LABEL.RESTRICTS, LINK_LABEL.IS_A]);
export const isDerivation = (edge) => DERIVATION_LINK_LABELS.has(edge.label);

/**
 * The category of the link {@code edge} between a node of kind {@code fromKind} and one of kind
 * {@code toKind}: the family's chain first (one end of it is enough), then what the label says;
 * a label that is neither an XSD word nor a chain word is the name of a nested element — content.
 */
export function linkCategory(edge, fromKind, toKind) {
  const label = edge.label;
  if (linkFamily(fromKind, toKind)) return LINK_CATEGORY.CHAIN;
  if (DERIVATION_LINK_LABELS.has(label)) return LINK_CATEGORY.DERIVATION;
  if (label === LINK_LABEL.ATTRIBUTE_REF || label.startsWith(LINK_LABEL.ATTRIBUTE_PREFIX)) return LINK_CATEGORY.ATTRIBUTE;
  if (label === LINK_LABEL.TYPE || label === LINK_LABEL.LIST_OF || label === LINK_LABEL.UNION_OF) return LINK_CATEGORY.TYPE;
  if (label === LINK_LABEL.REF || label === LINK_LABEL.SUBSTITUTES || label === LINK_LABEL.GROUP
      || label === LINK_LABEL.ATTRIBUTE_GROUP || label.startsWith(LINK_LABEL.KEYREF_PREFIX)) return LINK_CATEGORY.REFERENCE;
  return LINK_CATEGORY.CONTENT;
}

