/** Finding declarations across the open tabs: what an external placeholder resolves to, and who uses what. */
import { IMPORT_TAG, NODE_KIND, TYPE_REFERENCE_KIND, kindOfId, nodeId } from './constants.js';
import { session } from './state.js';

/** The kinds of declaration an external placeholder ("type:X", "element:X"...) can resolve to. */
export function kindsOf(node) {
  const k = kindOfId(node.id);
  return k === TYPE_REFERENCE_KIND ? [NODE_KIND.COMPLEX_TYPE, NODE_KIND.SIMPLE_TYPE] : [k];
}

/** The id a declaration gets in a file that references it without declaring it. */
export const externalIdOf = (n) =>
  nodeId(n.kind === NODE_KIND.COMPLEX_TYPE || n.kind === NODE_KIND.SIMPLE_TYPE ? TYPE_REFERENCE_KIND : n.kind, n.name);

/** Looks for the declaration of {@code name} (one of {@code kinds}, in namespace {@code ns}) in the tab {@code t}. */
export function findIn(t, name, kinds, ns) {
  if (!t.model) return null;
  for (const k of kinds) {
    const n = t.nodes.get(nodeId(k, name));
    // a schema without targetNamespace (chameleon include) takes the namespace of the including one
    if (n && n.kind !== NODE_KIND.EXTERNAL && (n.ns === ns || n.ns === '')) return n.id;
  }
  return null;
}

/** The declaration of {@code name} in any open tab (except {@code skip}): {tab, id} or null. */
export function findInTabs(name, kinds, ns, skip) {
  for (const t of session.tabs) {
    if (t === skip) continue;
    const id = findIn(t, name, kinds, ns);
    if (id) return { tab: t, id };
  }
  return null;
}

/** The nodes of the other open tabs that link to {@code n} (declared in tab {@code home}), where it is an external placeholder: [{n, edges, tab}]. */
export function usersInOtherTabs(n, home) {
  const extId = externalIdOf(n);
  const out = [];
  for (const t of session.tabs) {
    if (t === home || !t.model) continue;
    const ext = t.nodes.get(extId);
    if (!ext || ext.kind !== NODE_KIND.EXTERNAL || !(ext.ns === (n.ns || '') || ext.ns === '' || !n.ns)) continue;
    const users = new Map();
    for (const e of t.inEdges.get(extId) || []) {
      if (!users.has(e.from)) users.set(e.from, []);
      users.get(e.from).push(e);
    }
    for (const [id, edges] of users) { const u = t.nodes.get(id); if (u) out.push({ n: u, edges, tab: t }); }
  }
  return out;
}

/** The schemaLocations declared by tab {@code t} that may hold namespace {@code ns}. */
export function locationsFor(t, ns) {
  const out = [];
  for (const i of t.model.imports) {
    if (!i.schemaLocation) continue;
    const hit = i.tag === IMPORT_TAG.IMPORT ? i.namespace === ns : (ns === t.model.targetNamespace || ns === '');
    if (hit && !out.includes(i.schemaLocation)) out.push(i.schemaLocation);
  }
  return out;
}
