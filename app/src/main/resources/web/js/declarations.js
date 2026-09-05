/**
 * Finding declarations across the whole workspace — its open tabs and its listed files, parsed in
 * the background but not open —: what an external placeholder resolves to, and who uses what.
 * A "place" is where a declaration lives: {tab} or {entry}, with {model, nodes, outEdges, inEdges, fileName} alike.
 */
import { IMPORT_TAG, NODE_KIND, TYPE_REFERENCE_KIND, kindOfId, nodeId } from './constants.js';
import { session } from './state.js';
import { tabsOf } from './tabs.js';
import { tabOfFile } from './workspace-files.js';

/** The kinds of declaration an external placeholder ("type:X", "element:X"...) can resolve to. */
export function kindsOf(node) {
  const k = kindOfId(node.id);
  return k === TYPE_REFERENCE_KIND ? [NODE_KIND.COMPLEX_TYPE, NODE_KIND.SIMPLE_TYPE] : [k];
}

/** The id a declaration gets in a file that references it without declaring it. */
const externalIdOf = (n) =>
  nodeId(n.kind === NODE_KIND.COMPLEX_TYPE || n.kind === NODE_KIND.SIMPLE_TYPE ? TYPE_REFERENCE_KIND : n.kind, n.name);

/** Looks for the declaration of {@code name} (one of {@code kinds}, in namespace {@code ns}) in a place (a tab, or a listed file's place). */
export function findIn(place, name, kinds, ns) {
  if (!place.model) return null;
  for (const k of kinds) {
    const n = place.nodes.get(nodeId(k, name));
    // a schema without targetNamespace (chameleon include) takes the namespace of the including one
    if (n && n.kind !== NODE_KIND.EXTERNAL && (n.ns === ns || n.ns === '')) return n.id;
  }
  return null;
}

/** The indexes of a listed file's model (nodes by id, edges by end), built once per model. */
function indexOf(entry) {
  if (!entry.index || entry.index.model !== entry.model) {
    const nodes = new Map(entry.model.nodes.map(n => [n.id, n]));
    const outEdges = new Map(), inEdges = new Map();
    for (const e of entry.model.edges) {
      if (!outEdges.has(e.from)) outEdges.set(e.from, []);
      outEdges.get(e.from).push(e);
      if (!inEdges.has(e.to)) inEdges.set(e.to, []);
      inEdges.get(e.to).push(e);
    }
    entry.index = { model: entry.model, nodes, outEdges, inEdges };
  }
  return entry.index;
}

/**
 * The place a listed file is, shaped like a tab so that what draws a tab's model can draw it: its
 * indexed model, the workspace it belongs to, and no box opened. Used to compare one declaration
 * across two workspaces, where neither file need be open in a tab.
 */
export function placeOfEntry(entry, ws) {
  return Object.assign({ entry, fileName: entry.name, workspace: ws, modelExpanded: new Set() }, indexOf(entry));
}

/** The place of a tab. */
const tabPlace = (tab) => ({ tab, model: tab.model, nodes: tab.nodes, outEdges: tab.outEdges, inEdges: tab.inEdges, fileName: tab.fileName });

/** The places of {@code ws} other than the tab {@code skip}: its open tabs, then its parsed files not open in a tab. */
function placesOf(ws, skip) {
  const out = [];
  for (const t of tabsOf(ws)) if (t !== skip && t.model) out.push(tabPlace(t));
  for (const entry of ws.files) {
    if (!entry.model || tabOfFile(entry)) continue;
    out.push(Object.assign({ entry, fileName: entry.name }, indexOf(entry)));
  }
  return out;
}

/** The declaration of {@code name} elsewhere in {@code skip}'s workspace (open tabs first, then listed files): {place, id} or null. */
export function findInWorkspace(name, kinds, ns, skip) {
  for (const place of placesOf(skip.workspace, skip)) {
    const id = findIn(place, name, kinds, ns);
    if (id) return { place, id };
  }
  return null;
}

/** The nodes of the other files of {@code home}'s workspace that link to {@code n} (declared in {@code home}), where it is an external placeholder: [{n, edges, place}]. */
export function usersInWorkspace(n, home) {
  const extId = externalIdOf(n);
  const out = [];
  for (const place of placesOf(home.workspace, home)) {
    const ext = place.nodes.get(extId);
    if (!ext || ext.kind !== NODE_KIND.EXTERNAL || !(ext.ns === (n.ns || '') || ext.ns === '' || !n.ns)) continue;
    const users = new Map();
    for (const e of place.inEdges.get(extId) || []) {
      if (!users.has(e.from)) users.set(e.from, []);
      users.get(e.from).push(e);
    }
    for (const [id, edges] of users) { const u = place.nodes.get(id); if (u) out.push({ n: u, edges, place }); }
  }
  return out;
}

/** The data attributes naming a place on a drawn node or row: the tab's index, or the listed file's index in the workspace. */
export function placeAttributes(place, ws, dataAttr, DATA) {
  if (!place) return '';
  return place.tab ? dataAttr(DATA.TAB, session.tabs.indexOf(place.tab)) : dataAttr(DATA.FILE, ws.files.indexOf(place.entry));
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
