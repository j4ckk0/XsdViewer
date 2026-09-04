/**
 * The Model view: the content model of the selected declaration drawn left to right, as XSD editors
 * do — the declaration, then the compositors (a box per sequence, choice, all), then the elements
 * they hold, each with its occurrences and its type; the attributes as their own rows. An anonymous
 * type is walked in place; a named type, a global element, a group or a base type is expanded on
 * demand (a handle on the box), its content being that node's own, from this file or from another
 * file of the workspace.
 *
 * A declaration of a WSDL or of a Schematron has no content model — no particle is written for a
 * service or a rule — but it has a chain of its own, and that chain is the model such a file has:
 * a service holds its ports, a portType its operations, an operation its messages, a message the
 * elements of its parts, where the schema's own content model takes over; a phase holds its
 * patterns, they their rules, they their assertions. Such a box is named after what the link leads
 * to, with the link's word above it, and opens the same way.
 *
 * The tree is an SVG, which the PNG and SVG exports serve as they serve the graph.
 */
import { LINK_LABEL, NODE_KIND, PARTICLE, SVG_NS, TEXT, isSchematron, isWsdl, kindOfId, nameOfId } from './constants.js';
import { FAMILY, familyOf } from './link-categories.js';
import { cardinalityText, isOptional } from './cardinality.js';
import { findInWorkspace, kindsOf } from './declarations.js';
import { $, CLS, DATA, ID, dataAttr, esc, selector } from './dom.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

/** Boxes: the width of a declaration's or an element's, of a compositor's, of an attribute's; the height; the row pitch; the column gap. */
const BOX_W = 190, COMPOSITOR_W = 44, ATTRIBUTE_W = 190, BOX_H = 34, ROW = 52, GAP = 46, MARGIN = 24;
/** The expand handle: a small square at the right edge of an expandable box. */
const HANDLE = 14;
/** Corner radius of the box of a family object (a WSDL's service, a Schematron's rules), as the graph rounds their nodes. */
const FAMILY_RADIUS = 9;
const NAME_MAX_CHARS = 22, TYPE_MAX_CHARS = 24;
/** Room in a box: its padding, the gap between two texts of one line, and the width of a character of the name (13px) and of the small words (10px). */
const PAD = 8, TEXT_GAP = 6, NAME_CHAR_W = 6.5, WORD_CHAR_W = 5.2;
const ELLIPSIS = '…';
const COMPOSITOR_GLYPH = { [PARTICLE.SEQUENCE]: '···', [PARTICLE.CHOICE]: '◇', [PARTICLE.ALL]: '○' };
const EXPAND_GLYPH = '+', COLLAPSE_GLYPH = '−', RECURSION_GLYPH = '↺';
const ATTRIBUTE_PREFIX = '@', OPTIONAL_MARK = '?', TYPE_SEPARATOR = ' : ';
/** How deep "expand all" goes: enough for a schema, bounded for a recursive one. */
const EXPAND_ALL_DEPTH = 6;
const PATH_SEPARATOR = '/';

const shorten = (s, max) => (s.length > max ? s.slice(0, max - 1) + ELLIPSIS : s);

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
 */
export function buildTree(root, st) {
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
    box.expanded = expanded.has(path);
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

/**
 * Wires the Model view: its two buttons, and the clicks in its canvas — a handle opens or folds a
 * box, a box that refers to a global declaration selects it ({@code select}, from the navigation).
 */
export function initModelView(select) {
  $(ID.MODEL_EXPAND_ALL).addEventListener('click', expandAll);
  $(ID.MODEL_COLLAPSE_ALL).addEventListener('click', collapseAll);
  $(ID.MODEL_CANVAS).addEventListener('click', (e) => {
    const handle = e.target.closest(selector(CLS.MODEL_HANDLE));
    if (handle) { toggleExpanded(handle.dataset[DATA.PATH]); return; }
    const box = e.target.closest(selector(CLS.MODEL_BOX));
    const id = box ? box.dataset[DATA.ID] : null;
    if (id && session.active.nodes.has(id)) select(id);
  });
}

/** Every expandable box open, down to EXPAND_ALL_DEPTH levels. */
function expandAll() {
  const st = session.active;
  st.modelExpanded = new Set();
  for (let depth = 0; depth < EXPAND_ALL_DEPTH; depth++) {
    const root = st.nodes.get(st.selected);
    if (!root) return;
    let added = false;
    const walk = (box) => {
      if (box.expandable && !box.expanded) { st.modelExpanded.add(box.path); added = true; }
      box.children.forEach(walk);
    };
    walk(buildTree(root, st));
    if (!added) break;
  }
  renderModel();
}

function collapseAll() {
  session.active.modelExpanded = new Set();
  renderModel();
}

/** The handle of a box clicked: its type, group or base type shown or folded. */
function toggleExpanded(path) {
  const set = session.active.modelExpanded;
  if (!set.delete(path)) set.add(path);
  renderModel();
}

/** The layout: rows of {@code ROW} pixels, a box centred on its children (attributes first, then particles), columns per depth. */
function layout(box, depth, top) {
  box.depth = depth;
  const rows = [...box.attributes, ...box.children];
  if (!rows.length) { box.y = top; box.height = 1; return 1; }
  let y = top;
  for (const r of rows) y += layout(r, depth + 1, y);
  box.height = y - top;
  box.y = top + (box.height - 1) / 2;
  return box.height;
}

const widthOf = (box) => (COMPOSITOR_GLYPH[box.kind] ? COMPOSITOR_W : box.kind === NODE_KIND.ATTRIBUTE ? ATTRIBUTE_W : BOX_W);

/** Draws the model of the selected node into the canvas. */
export function renderModel() {
  const st = session.active;
  const canvas = $(ID.MODEL_CANVAS);
  if (!st.model || !st.selected) { canvas.innerHTML = ''; return; }
  const root = st.nodes.get(st.selected);
  const tree = buildTree(root, st);
  const rows = layout(tree, 0, 0);
  let maxDepth = 0;
  const all = [];
  const collect = (b) => { all.push(b); maxDepth = Math.max(maxDepth, b.depth); [...b.attributes, ...b.children].forEach(collect); };
  collect(tree);
  const colX = (depth) => MARGIN + depth * (BOX_W + GAP);
  const W = colX(maxDepth) + BOX_W + MARGIN, H = Math.max(canvas.clientHeight, rows * ROW + 2 * MARGIN);
  const cy = (b) => MARGIN + b.y * ROW + ROW / 2;
  let links = '', boxes = '';
  for (const b of all) {
    const x = colX(b.depth), y = cy(b), w = widthOf(b);
    for (const c of [...b.attributes, ...b.children]) {
      // an elbow: out of the parent, along a bus halfway to the next column, into the child
      const busX = colX(b.depth + 1) - GAP / 2;
      links += '<path class="' + CLS.MODEL_LINK + (isOptional(c.card || {}) ? ' ' + CLS.OPTIONAL : '') + '" d="M' + (x + w) + ',' + y
        + ' H' + busX + ' V' + cy(c) + ' H' + colX(c.depth) + '"/>';
    }
    boxes += boxSvg(b, x, y - BOX_H / 2, w);
  }
  canvas.innerHTML = '<svg xmlns="' + SVG_NS + '" width="' + W + '" height="' + H + '" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="'
    + esc(t(MSG.MODEL_TITLE, kindLabel(root.kind), root.name)) + '">' + links + boxes + '</svg>';
  $(ID.MODEL_TITLE).textContent = t(MSG.MODEL_TITLE, kindLabel(root.kind), root.name);
  // the legend names the kinds of box the shown file can have: its family's, and the schema's own
  const family = isWsdl(st.model) ? FAMILY.WSDL : isSchematron(st.model) ? FAMILY.SCHEMATRON : null;
  $(ID.MODEL_LEGEND).classList.toggle(CLS.WSDL, family === FAMILY.WSDL);
  $(ID.MODEL_LEGEND).classList.toggle(CLS.SCHEMATRON, family === FAMILY.SCHEMATRON);
  $(ID.MODEL_EMPTY).classList.toggle(CLS.HIDDEN, tree.children.length > 0 || tree.attributes.length > 0);
}

/** One box: a compositor (its glyph), an attribute (@name : type), an element / group / base / wildcard / chain object (name, type or kind, handle). */
function boxSvg(b, x, y, w) {
  const compositor = COMPOSITOR_GLYPH[b.kind];
  const card = b.card ? cardinalityText(b.card) : '';
  const optional = b.card && isOptional(b.card);
  const kindClass = b.kind === PARTICLE.EXTENDS || b.kind === PARTICLE.RESTRICTS ? NODE_KIND.COMPLEX_TYPE : b.kind === PARTICLE.ANY ? NODE_KIND.EXTERNAL : b.kind;
  const cls = CLS.MODEL_BOX + ' ' + kindClass + (b.root ? ' ' + CLS.CENTER : '') + (optional ? ' ' + CLS.OPTIONAL : '') + (b.expandable || b.ref || b.typeId ? ' ' + CLS.CLICKABLE : '');
  const target = b.root ? '' : b.ref || (b.kind === PARTICLE.EXTENDS || b.kind === PARTICLE.RESTRICTS ? b.typeId : '') || b.typeId;
  const radius = familyOf(b.kind) ? FAMILY_RADIUS : b.root ? 0 : 3;   // a family object is rounded, as the graph draws it
  let g = '<g class="' + cls + '"' + dataAttr(DATA.PATH, b.path) + (target ? dataAttr(DATA.ID, target) : '') + ' transform="translate(' + x + ',' + y + ')">';
  g += '<title>' + esc(titleOf(b)) + '</title>';
  if (compositor) {
    g += '<rect width="' + w + '" height="' + BOX_H + '" rx="4"/><text class="' + CLS.MODEL_GLYPH + '" x="' + (w / 2) + '" y="' + (BOX_H / 2 + 5) + '" text-anchor="middle">' + compositor + '</text>';
  } else if (b.kind === NODE_KIND.ATTRIBUTE) {
    const label = ATTRIBUTE_PREFIX + b.name + (b.typeName ? TYPE_SEPARATOR + shorten(b.typeName, TYPE_MAX_CHARS) : '') + (optional ? ' ' + OPTIONAL_MARK : '');
    g += '<rect width="' + w + '" height="' + BOX_H + '" rx="2"/><text class="' + CLS.MODEL_NAME + '" x="8" y="' + (BOX_H / 2 + 4) + '">' + esc(shorten(label, NAME_MAX_CHARS + TYPE_MAX_CHARS)) + '</text>';
  } else {
    // the root, a box of a chain (the link's word) and the boxes standing for something else than an element say what they are above their name
    const word = b.word || (b.root || b.kind === PARTICLE.EXTENDS || b.kind === PARTICLE.RESTRICTS || b.kind === PARTICLE.GROUP || b.kind === PARTICLE.ANY ? kindLabel(b.kind) : '');
    const name = b.root ? b.name : b.kind === PARTICLE.ANY ? b.namespace : b.name;
    // at the right: the type of an element, under its name; for a box of a chain, what kind of object it
    // leads to, on the line of the link's word — a chain's names are long, and want the whole line below
    // (a link whose word is already the kind it leads to — a portType to its operations — says it once)
    const chainKind = b.word && kindLabel(b.kind) !== b.word ? kindLabel(b.kind) : '';
    const corner = shorten(b.word ? chainKind : (word ? '' : b.typeName), TYPE_MAX_CHARS);
    const withWord = !!b.word;
    const handleRoom = b.expandable || b.recursive ? HANDLE + 4 : 0;
    const room = w - 2 * PAD - handleRoom;   // what a line has for its texts
    const cornerRoom = corner ? corner.length * WORD_CHAR_W + TEXT_GAP : 0;
    const wordMax = Math.floor((room - (withWord ? cornerRoom : 0)) / WORD_CHAR_W);
    const nameMax = Math.min(NAME_MAX_CHARS, Math.floor((room - (withWord ? 0 : cornerRoom)) / NAME_CHAR_W));
    g += '<rect width="' + w + '" height="' + BOX_H + '" rx="' + radius + '"/>'
      + (word ? '<text class="' + CLS.MODEL_WORD + '" x="' + PAD + '" y="12">' + esc(shorten(word, wordMax)) + '</text>' : '')
      + '<text class="' + CLS.MODEL_NAME + '" x="' + PAD + '" y="' + (word ? BOX_H - 8 : BOX_H / 2 + 5) + '">' + esc(shorten(name, nameMax)) + '</text>'
      + (corner ? '<text class="' + CLS.MODEL_TYPE + '" x="' + (w - PAD - handleRoom) + '" y="' + (withWord ? 12 : BOX_H - 6) + '" text-anchor="end">' + esc(corner) + '</text>' : '');
    if (b.expandable) {
      g += '<g class="' + CLS.MODEL_HANDLE + '"' + dataAttr(DATA.PATH, b.path) + '><rect x="' + (w - HANDLE - 3) + '" y="' + ((BOX_H - HANDLE) / 2) + '" width="' + HANDLE + '" height="' + HANDLE + '" rx="2"/>'
        + '<text x="' + (w - HANDLE / 2 - 3) + '" y="' + (BOX_H / 2 + 4) + '" text-anchor="middle">' + (b.expanded ? COLLAPSE_GLYPH : EXPAND_GLYPH) + '</text></g>';
    } else if (b.recursive) {
      g += '<text class="' + CLS.MODEL_RECURSION + '" x="' + (w - 6) + '" y="' + (BOX_H / 2 + 4) + '" text-anchor="end">' + RECURSION_GLYPH + '</text>';
    }
  }
  // the occurrences under the box, unless one and only one (an attribute's use is the ? of its label)
  if (card && card !== '1' && card !== '1..1' && b.kind !== NODE_KIND.ATTRIBUTE) g += '<text class="' + CLS.CARDINALITY + '" x="' + (compositor ? w / 2 : 8) + '" y="' + (BOX_H + 12) + '"' + (compositor ? ' text-anchor="middle"' : '') + '>' + esc(card) + '</text>';
  return g + '</g>';
}

/** The tooltip of a box: what it is, its occurrences, its type; how to open it. */
function titleOf(b) {
  const parts = b.word && !b.root ? [b.word] : [];
  if (COMPOSITOR_GLYPH[b.kind]) parts.push(kindLabel(b.kind));
  else if (b.kind === NODE_KIND.ATTRIBUTE) parts.push(kindLabel(NODE_KIND.ATTRIBUTE) + ' ' + b.name);
  else parts.push((b.root ? kindLabel(b.kind) : kindLabel(b.kind === PARTICLE.ANY ? NODE_KIND.EXTERNAL : b.kind)) + ' ' + (b.kind === PARTICLE.ANY ? b.namespace : b.name));
  if (b.card && cardinalityText(b.card)) parts.push(cardinalityText(b.card));
  if (b.typeName) parts.push(b.typeName);
  if (b.expandable) parts.push(t(b.expanded ? MSG.MODEL_FOLD : MSG.MODEL_UNFOLD));
  if (b.recursive) parts.push(t(MSG.MODEL_RECURSIVE));
  return parts.join(TEXT.TOAST_SEPARATOR);
}

