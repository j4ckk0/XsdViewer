/** What differs between two parsed schemas — the declarations and the links on one side only — and the keys telling links apart. */
import { NODE_KIND } from './constants.js';
import { cardinalityText } from './cardinality.js';

const KEY_SEPARATOR = '\u0000';

/** A declared node (not a placeholder for a built-in or an external object). */
const declared = (n) => n.kind !== NODE_KIND.BUILTIN && n.kind !== NODE_KIND.EXTERNAL;

/** Identity of a link, cardinality included: a changed minOccurs shows as one link gone and one added. */
const edgeKey = (e) => [e.from, e.to, e.label, cardinalityText(e)].join(KEY_SEPARATOR);

/** A link seen from one node, told apart by what it is — its word, the other end's kind and name, its cardinality — rather than by the file it is written in. */
export const linkKey = (node, edge) => [edge.label, node.kind, node.name, cardinalityText(edge)].join(KEY_SEPARATOR);

/** The keys of the links around a node of a place, both ways: what its side of a comparison holds. */
export function neighbourhoodKeys(place, id) {
  const keys = new Set();
  for (const e of place.outEdges.get(id) || []) { const n = place.nodes.get(e.to); if (n) keys.add(linkKey(n, e)); }
  for (const e of place.inEdges.get(id) || []) { const n = place.nodes.get(e.from); if (n) keys.add(linkKey(n, e)); }
  return keys;
}

/** The declarations and links present in only one of two parsed models: {nodesOnlyLeft, nodesOnlyRight, edgesOnlyLeft, edgesOnlyRight, same}. */
export function diffModels(left, right) {
  const leftIds = new Set(left.nodes.filter(declared).map(n => n.id));
  const rightIds = new Set(right.nodes.filter(declared).map(n => n.id));
  const leftEdges = new Map(left.edges.map(e => [edgeKey(e), e]));
  const rightEdges = new Map(right.edges.map(e => [edgeKey(e), e]));
  const result = {
    nodesOnlyLeft: left.nodes.filter(n => declared(n) && !rightIds.has(n.id)),
    nodesOnlyRight: right.nodes.filter(n => declared(n) && !leftIds.has(n.id)),
    edgesOnlyLeft: [...leftEdges].filter(([k]) => !rightEdges.has(k)).map(([, e]) => e),
    edgesOnlyRight: [...rightEdges].filter(([k]) => !leftEdges.has(k)).map(([, e]) => e),
  };
  result.same = !result.nodesOnlyLeft.length && !result.nodesOnlyRight.length && !result.edgesOnlyLeft.length && !result.edgesOnlyRight.length;
  return result;
}
