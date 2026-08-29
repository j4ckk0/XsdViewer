/** What differs between two parsed schemas: the declarations and the links that exist on one side only. */
import { NODE_KIND } from './constants.js';
import { cardinalityText } from './cardinality.js';

const KEY_SEPARATOR = '\u0000';

/** A declared node (not a placeholder for a built-in or an external object). */
const declared = (n) => n.kind !== NODE_KIND.BUILTIN && n.kind !== NODE_KIND.EXTERNAL;

/** Identity of a link, cardinality included: a changed minOccurs shows as one link gone and one added. */
const edgeKey = (e) => [e.from, e.to, e.label, cardinalityText(e)].join(KEY_SEPARATOR);

/**
 * @param left  the model (as answered by /api/parse) of the left file
 * @param right the model of the right file
 * @return {nodesOnlyLeft, nodesOnlyRight, edgesOnlyLeft, edgesOnlyRight, same}: nodes and edges of the models
 */
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
