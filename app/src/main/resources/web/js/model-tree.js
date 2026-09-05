/**
 * The content model of a declaration as a tree of boxes, which the Model view draws and the
 * comparison of two declarations walks: the compositors of the declaration (a box per sequence,
 * choice, all), the elements they hold with their occurrences and their type, its attributes. An
 * anonymous type is walked in place; a named type, a global element, a group or a base type is a
 * box that opens on demand, its content being that node's own, from this file or from another file
 * of the workspace.
 *
 * A declaration of a WSDL or of a Schematron has no content model — no particle is written for a
 * service or a rule — but it has a chain of its own, and that chain is the model such a file has:
 * a service holds its ports, a portType its operations, an operation its messages, a message the
 * elements of its parts, where the schema's own content model takes over; a phase holds its
 * patterns, they their rules, they their assertions. Such a box is named after what the link leads
 * to, with the link's word above it, and opens the same way.
 *
 * Nothing here draws: a tree is a function of a declaration and the tab it is read from, which is
 * what lets the tests exercise it.
 */
import { LINK_LABEL, NODE_KIND, kindOfId, nameOfId } from './constants.js';
import { findInWorkspace, kindsOf } from './declarations.js';
import { familyOf } from './link-categories.js';

/** How deep "open every box" goes: enough for a schema, bounded for a recursive one. */
export const EXPAND_ALL_DEPTH = 6;
/** Between the indexes of a box's path from the root, which is what the opened set holds. */
export const PATH_SEPARATOR = '/';

/**
 * The node with {@code id} and the file it lives in: the place looked into, or — an external
 * placeholder — the file of the workspace {@code st} that declares it, whose own links are then the
 * ones to follow. A place is anything holding {@code nodes} and {@code outEdges}: a tab, or a
 * listed file. {@code n} is null when the declaration is nowhere to be found.
 */
function nodeOf(id, place, st) {
  const n = place.nodes.get(id);
  if (n && n.kind !== NODE_KIND.EXTERNAL) return { n, place };
  const name = n ? n.name : nameOfId(id);
  const kinds = n ? kindsOf(n) : [kindOfId(id)];
  const found = findInWorkspace(name, kinds, n ? n.ns || '' : '', st);
  return found ? { n: found.place.nodes.get(found.id), place: found.place } : { n: n || null, place };
}

/** The content a node has of its own: its particles (and attributes), or, for an element of a named type, that type's — in whichever file declares it. */
function contentOf(n, path, place, st) {
  if (!n) return null;
  if ((n.content && n.content.length) || (n.attributes && n.attributes.length)) return { particles: n.content || [], attributes: n.attributes || [], id: n.id, place };
  if (n.kind === NODE_KIND.ELEMENT) {   // a global element of a named type: the type's content
    const typeEdge = (place.outEdges.get(n.id) || []).find(e => e.label === LINK_LABEL.TYPE);
    if (typeEdge) {
      const type = nodeOf(typeEdge.to, place, st);
      if (type.n && !path.includes(type.n.id)) return contentOf(type.n, path, type.place, st);
    }
  }
  return null;
}

/** The links a family object has of its own: a WSDL's service chain, a Schematron's rules — what its model is made of. */
const chainLinksOf = (n, place) => (n && familyOf(n.kind) ? place.outEdges.get(n.id) || [] : []);

/**
 * What a box opens onto: the content model of {@code n}, or — a WSDL's or a Schematron's own
 * object, which has none — the links of its chain. {@code id} is what the recursion guard watches,
 * {@code place} the file the boxes below are read from; null when there is nothing to open.
 */
function openingOf(n, ids, place, st) {
  const content = contentOf(n, ids, place, st);
  if (content) return { content, id: content.id, place: content.place };
  const links = chainLinksOf(n, place);
  return links.length ? { links, id: n.id, place } : null;
}

/**
 * The display tree of the declaration {@code root} in the tab {@code st}: {kind, name, type, ref,
 * card, children, attributes, path, expandable, expanded, recursive}. {@code path}: the indexes from
 * the root, what {@code st.modelExpanded} holds. {@code onPath}: the node ids being expanded (recursion guard).
 * A function of the declaration and the tab alone, which is what the tests exercise.
 * {@code openAll}: every box open down to {@code EXPAND_ALL_DEPTH}, whatever the tab has open — what
 * the comparison of two models needs, since it compares the whole shape, not what the reader unfolded.
 */
export function buildTree(root, st, { openAll = false } = {}) {
  const expanded = st.modelExpanded;
  const onPath = [root.id];
  const tree = { kind: root.kind, name: root.name, id: root.id, path: '', children: [], attributes: [], root: true };
  const opening = openingOf(root, [], st, st);
  if (opening) fill(tree, opening, onPath);

  function fill(box, opening, ids) {
    const place = opening.place;
    if (opening.links) {   // a family object: its chain, one box per link
      box.children = opening.links.map((e, i) => chainBox(e, box.path + PATH_SEPARATOR + i, ids, place));
      return;
    }
    box.attributes = opening.content.attributes.map((a, i) => attributeBox(a, box.path + PATH_SEPARATOR + 'a' + i, place));
    box.children = opening.content.particles.map((p, i) => particleBox(p, box.path + PATH_SEPARATOR + i, ids, place));
  }

  /** A box of a chain: what the link leads to, with the link's word above its name (a port's name, "operation", "input"...). */
  function chainBox(edge, path, ids, place) {
    const target = nodeOf(edge.to, place, st);
    const box = {
      kind: target.n ? target.n.kind : kindOfId(edge.to), name: target.n ? target.n.name : nameOfId(edge.to),
      path, ref: edge.to, typeId: '', typeName: '', word: edge.label, children: [], attributes: [],
    };
    return opened(box, target, path, ids);
  }

  /** A box standing for another declaration: a handle when that one has something to open, {@code recursive} when it is already open above. */
  function opened(box, target, path, ids) {
    const opening = target.n ? openingOf(target.n, ids, target.place, st) : null;
    if (!opening) return box;
    if (ids.includes(opening.id)) { box.recursive = true; return box; }
    box.expandable = true;
    box.expanded = openAll ? ids.length <= EXPAND_ALL_DEPTH : expanded.has(path);
    if (box.expanded) fill(box, opening, ids.concat(opening.id));
    return box;
  }

  function attributeBox(a, path, place) {
    const type = a.type ? nodeOf(a.type, place, st).n : null;
    return { kind: NODE_KIND.ATTRIBUTE, name: a.name, path, ref: a.ref || '', typeId: a.type || '', typeName: type ? type.name : '', card: a, children: [], attributes: [] };
  }

  function particleBox(p, path, ids, place) {
    const box = { kind: p.kind, name: p.name || '', path, ref: p.ref || '', typeId: p.type || '', typeName: '', card: p, children: [], attributes: [], namespace: p.namespace || '' };
    if (p.children || p.attributes) {   // an anonymous type, or a compositor: walked in place
      fill(box, { content: { particles: p.children || [], attributes: p.attributes || [] }, place }, ids);
      return box;
    }
    // what the box refers to — a type, a global element, a group, a base type — is expanded on demand
    const targetId = p.ref || p.type;
    if (!targetId) return box;
    const target = nodeOf(targetId, place, st);
    box.typeName = target.n ? target.n.name : nameOfId(targetId);
    if (p.type && target.n && target.n.kind !== NODE_KIND.COMPLEX_TYPE && target.n.kind !== NODE_KIND.EXTERNAL) return box;   // a simple or built-in type: nothing inside
    return opened(box, target, path, ids);
  }
  return tree;
}
