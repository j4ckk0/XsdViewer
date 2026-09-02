import { NODE_KIND } from './constants.js';

/** Fills a tab with a parsed schema: the node map, the edge indexes, the declaration lines, and the first selection. */
export function indexSchema(st, json) {
  st.model = json;
  st.nodes = new Map(json.nodes.map(n => [n.id, n]));
  st.outEdges = new Map();
  st.inEdges = new Map();
  for (const e of json.edges) {
    if (!st.outEdges.has(e.from)) st.outEdges.set(e.from, []);
    st.outEdges.get(e.from).push(e);
    if (!st.inEdges.has(e.to)) st.inEdges.set(e.to, []);
    st.inEdges.get(e.to).push(e);
  }
  st.lineToNode = new Map();
  for (const n of json.nodes) if (n.line > 0) st.lineToNode.set(n.line, n.id);
  st.selected = initialSelection(st);
}

/** The first service (a WSDL), else the first global element that nothing references (a likely document root), else the first element, else the first node. */
function initialSelection(st) {
  const nodes = st.model.nodes;
  const service = nodes.find(n => n.kind === NODE_KIND.SERVICE);
  if (service) return service.id;
  const roots = nodes.filter(n => n.kind === NODE_KIND.ELEMENT && !(st.inEdges.get(n.id) || []).some(e => e.from !== n.id));
  const first = roots[0] || nodes.find(n => n.kind === NODE_KIND.ELEMENT) || nodes[0];
  return first ? first.id : null;
}
