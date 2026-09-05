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
 * The tree it draws is the server's ({@code POST /api/model}, {@code model-requests.js} says what the
 * request carries), asked for again when the selection or the opened boxes change and kept on the tab
 * meanwhile; the drawing is an SVG, which the PNG and SVG exports serve as they serve the graph.
 */
import { NODE_KIND, PARTICLE, SVG_NS, TEXT, VIEW, isSchematron, isWsdl } from './constants.js';
import { FAMILY, familyOf } from './link-categories.js';
import { cardinalityText, isOptional } from './cardinality.js';
import { usersInWorkspace } from './declaration-lookup.js';
import { $, dataAttr, esc, selector } from './dom.js';
import { CLS, DATA, ID } from './dom-names.js';
import { fetchModel } from './api.js';
import { tabSide, libraryKey } from './model-requests.js';
import { toast } from './toast.js';
import { applyZoom } from './zoom.js';
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
/** The handle to the graph, and the mark before the count of objects sharing what a box stands for. */
const TO_GRAPH_GLYPH = '◎', SHARED_MARK = '×';
const ATTRIBUTE_PREFIX = '@', OPTIONAL_MARK = '?', TYPE_SEPARATOR = ' : ';
const shorten = (s, max) => (s.length > max ? s.slice(0, max - 1) + ELLIPSIS : s);

/**
 * Wires the Model view: its two buttons, and the clicks in its canvas — a handle opens or folds a
 * box, a box that refers to a global declaration selects it ({@code select}, from the navigation), its ◎
 * handle shows that declaration in the graph ({@code showView}, from the router).
 */
export function initModelView(select, showView) {
  $(ID.MODEL_EXPAND_ALL).addEventListener('click', expandAll);
  $(ID.MODEL_COLLAPSE_ALL).addEventListener('click', collapseAll);
  $(ID.MODEL_CANVAS).addEventListener('click', (e) => {
    const handle = e.target.closest(selector(CLS.MODEL_HANDLE));
    if (handle) { toggleExpanded(handle.dataset[DATA.PATH]); return; }
    const box = e.target.closest(selector(CLS.MODEL_BOX));
    const id = box ? box.dataset[DATA.ID] : null;
    if (!id || !session.active.nodes.has(id)) return;
    select(id);
    if (e.target.closest(selector(CLS.MODEL_TO_GRAPH))) showView(VIEW.GRAPH);   // the same object, the other question
  });
}

/** Every expandable box open, as deep as the server opens them: the paths of the whole tree become the opened ones. */
async function expandAll() {
  const st = session.active;
  if (!st.model || !st.selected) return;
  try {
    const tree = await fetchModel(Object.assign(tabSide(st, st.selected), { openAll: true }));
    if (session.active !== st) return;
    st.modelExpanded = new Set();
    const walk = (box) => {
      if (box.expandable && box.expanded) st.modelExpanded.add(box.path);
      box.attributes.forEach(walk);
      box.children.forEach(walk);
    };
    walk(tree);
    renderModel();
  } catch (e) {
    toast(e.message);
  }
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

/** What a box shows under it: its attributes then its particles, nothing while it is folded. */
const rowsOf = (box) => (box.folded ? [] : [...box.attributes, ...box.children]);
/** True when a box holds something, whether or not it is folded. */
const hasRows = (box) => !!(box.attributes.length || box.children.length);

/** The layout: rows of {@code ROW} pixels, a box centred on its children (attributes first, then particles), columns per depth. */
function layout(box, depth, top) {
  box.depth = depth;
  const rows = rowsOf(box);
  if (!rows.length) { box.y = top; box.height = 1; return 1; }
  let y = top;
  for (const r of rows) y += layout(r, depth + 1, y);
  box.height = y - top;
  box.y = top + (box.height - 1) / 2;
  return box.height;
}

const widthOf = (box) => (COMPOSITOR_GLYPH[box.kind] ? COMPOSITOR_W : box.kind === NODE_KIND.ATTRIBUTE ? ATTRIBUTE_W : BOX_W);

/**
 * The SVG of a content model: the tree laid out in columns, the elbow connectors and the boxes.
 * {@code label} names the picture for a reader who cannot see it, {@code minHeight} is the room it
 * is drawn in. {@code foldable}: every box holding something carries a handle that folds it, which
 * is how the comparison of two models lets a reader put a whole subtree aside; the Model view opens
 * a box on demand instead, so only what stands for another declaration carries one. {@code traces}: what
 * the graph knows of the objects the boxes stand for, drawn on them in the Model view — nothing in a
 * comparison, whose two models come from anywhere.
 */
export function modelSvg(tree, label, minHeight = 0, { foldable = false, traces = null } = {}) {
  const rows = layout(tree, 0, 0);
  let maxDepth = 0;
  const all = [];
  const collect = (b) => { all.push(b); maxDepth = Math.max(maxDepth, b.depth); rowsOf(b).forEach(collect); };
  collect(tree);
  const colX = (depth) => MARGIN + depth * (BOX_W + GAP);
  const W = colX(maxDepth) + BOX_W + MARGIN, H = Math.max(minHeight, rows * ROW + 2 * MARGIN);
  const cy = (b) => MARGIN + b.y * ROW + ROW / 2;
  let links = '', boxes = '';
  for (const b of all) {
    const x = colX(b.depth), y = cy(b), w = widthOf(b);
    for (const c of rowsOf(b)) {
      // an elbow: out of the parent, along a bus halfway to the next column, into the child
      const busX = colX(b.depth + 1) - GAP / 2;
      links += '<path class="' + CLS.MODEL_LINK + (isOptional(c.card || {}) ? ' ' + CLS.OPTIONAL : '') + '" d="M' + (x + w) + ',' + y
        + ' H' + busX + ' V' + cy(c) + ' H' + colX(c.depth) + '"/>';
    }
    boxes += boxSvg(b, x, y - BOX_H / 2, w, foldable, traces);
  }
  return '<svg xmlns="' + SVG_NS + '" width="' + W + '" height="' + H + '" viewBox="0 0 ' + W + ' ' + H
    + '" role="img" aria-label="' + esc(label) + '">' + links + boxes + '</svg>';
}

/** What a request depends on, short of the texts: the declaration, the opened boxes, and how many files of the workspace are parsed. */
const requestKey = (st) => [st.selected, [...st.modelExpanded].sort().join(','), libraryKey(st.workspace)].join('|');

/** The tree kept on the tab, when it answers the request the tab would make now; null otherwise. */
export const cachedTree = (st) => (st.modelTree && st.modelTree.key === requestKey(st) ? st.modelTree.tree : null);

/**
 * The model tree of the tab's selection with its opened boxes: the one kept when the request would be the
 * same, else the server's answer — asked once, however many views wait for it — kept on the tab as long as
 * the request stays the same.
 */
export function modelTree(st) {
  const cached = cachedTree(st);
  if (cached) return Promise.resolve(cached);
  const key = requestKey(st);
  if (st.modelLoading && st.modelLoading.key === key) return st.modelLoading.promise;
  const promise = fetchModel(Object.assign(tabSide(st, st.selected), { expanded: [...st.modelExpanded] }))
    .then(tree => { if (requestKey(st) === key) st.modelTree = { key, tree }; return tree; })
    .finally(() => { if (st.modelLoading && st.modelLoading.key === key) st.modelLoading = null; });
  st.modelLoading = { key, promise };
  return promise;
}

/** The declared objects a model walks through: what its boxes opened in place stand for. */
export function openedIn(tree) {
  const ids = new Set();
  const walk = (b) => {
    if (!b.root && b.expanded && (b.ref || b.typeId)) ids.add(b.ref || b.typeId);
    b.attributes.forEach(walk);
    b.children.forEach(walk);
  };
  walk(tree);
  return ids;
}

/**
 * Draws the model of the selected node into the canvas: from the tree kept on the tab when the
 * request would be the same (a resize, a zoom), else from the server's answer once it comes — unless
 * the reader has moved on meanwhile.
 */
export function renderModel() {
  const st = session.active;
  $(ID.MODEL_BACK_BUTTON).disabled = st.history.length === 0;   // the selection's history, as the graph's Back walks it
  const canvas = $(ID.MODEL_CANVAS);
  if (!st.model || !st.selected) { canvas.innerHTML = ''; return; }
  const cached = cachedTree(st);
  if (cached) { draw(st, cached); aim(st, cached); return; }
  const key = requestKey(st);
  canvas.dataset[DATA.LOADING] = key;
  modelTree(st)
    .then(tree => {
      if (session.active !== st || requestKey(st) !== key) return;
      draw(st, tree);
      applyZoom();   // a new SVG, which takes the tab's level
      aim(st, tree);
    })
    .catch(e => toast(e.message))
    .finally(() => { if (canvas.dataset[DATA.LOADING] === key) delete canvas.dataset[DATA.LOADING]; });   // a later request has its own key and clears its own mark
}

function draw(st, tree) {
  const canvas = $(ID.MODEL_CANVAS);
  const root = st.nodes.get(st.selected);
  const label = t(MSG.MODEL_TITLE, kindLabel(root.kind), root.name);
  canvas.innerHTML = modelSvg(tree, label, canvas.clientHeight, { traces: tracesOf(st) });
  $(ID.MODEL_TITLE).textContent = label;
  // the legend names the kinds of box the shown file can have: its family's, and the schema's own
  const family = isWsdl(st.model) ? FAMILY.WSDL : isSchematron(st.model) ? FAMILY.SCHEMATRON : null;
  $(ID.MODEL_LEGEND).classList.toggle(CLS.WSDL, family === FAMILY.WSDL);
  $(ID.MODEL_LEGEND).classList.toggle(CLS.SCHEMATRON, family === FAMILY.SCHEMATRON);
  $(ID.MODEL_EMPTY).classList.toggle(CLS.HIDDEN, tree.children.length > 0 || tree.attributes.length > 0);
}

/** What a box stands for, when it is not the root: the object it refers to, else its type. */
const standsFor = (b) => (b.root ? '' : b.ref || b.typeId);

/**
 * Brings into view the box standing for the node Graph → Model was left on ({@code st.modelAim}) and marks
 * it. A second-level node of the graph lies under a box not opened yet — the first-level object that
 * links to it —: that box is opened first, and the redrawing comes back here. The aim is dropped once
 * met, or when the model has no box for it.
 */
function aim(st, tree) {
  const id = st.modelAim;
  if (!id) return;
  const boxes = allBoxes(tree);
  const box = boxes.find(b => standsFor(b) === id);
  if (box) {
    st.modelAim = null;
    const canvas = $(ID.MODEL_CANVAS);
    const el = canvas.querySelector(selector(CLS.MODEL_BOX) + '[data-' + DATA.PATH + '="' + box.path + '"]');
    if (!el) return;
    el.classList.add(CLS.MODEL_AIMED);
    const r = el.getBoundingClientRect(), c = canvas.getBoundingClientRect();
    canvas.scrollLeft += r.left - c.left - (c.width - r.width) / 2;
    canvas.scrollTop += r.top - c.top - (c.height - r.height) / 2;
    return;
  }
  const firstLevel = new Set((st.outEdges.get(st.selected) || []).map(e => e.to));
  const above = boxes.find(b => b.expandable && !b.expanded && firstLevel.has(standsFor(b))
    && (st.outEdges.get(standsFor(b)) || []).some(e => e.to === id));
  if (!above) { st.modelAim = null; return; }
  st.modelExpanded.add(above.path);
  renderModel();
}

/** Every box of a tree, the attributes of each before its children. */
function allBoxes(tree) {
  const out = [];
  const walk = (b) => { out.push(b); b.attributes.forEach(walk); b.children.forEach(walk); };
  walk(tree);
  return out;
}

/**
 * What the graph knows of the objects the boxes stand for: which are declared in the file — those have a
 * handle to the graph — and how many objects of the workspace use each, counted once per box drawn.
 */
function tracesOf(st) {
  const counts = new Map();
  const declared = (id) => { const n = st.nodes.get(id); return !!n && n.kind !== NODE_KIND.BUILTIN && n.kind !== NODE_KIND.EXTERNAL; };
  const usersOf = (id) => {
    if (!counts.has(id)) {
      const here = new Set((st.inEdges.get(id) || []).map(e => e.from).filter(from => from !== id));
      counts.set(id, here.size + usersInWorkspace(st.nodes.get(id), st).length);
    }
    return counts.get(id);
  };
  return { declared, usersOf };
}

/** What a box stands for when the graph has it as a declared object, else empty: the root stands for itself. */
const declaredTarget = (b, traces) => (traces && !b.root && (b.ref || b.typeId) && traces.declared(b.ref || b.typeId) ? b.ref || b.typeId : '');

/** One box: a compositor (its glyph), an attribute (@name : type), an element / group / base / wildcard / chain object (name, type or kind, handles). */
function boxSvg(b, x, y, w, foldable, traces) {
  const compositor = COMPOSITOR_GLYPH[b.kind];
  const card = b.card ? cardinalityText(b.card) : '';
  const optional = b.card && isOptional(b.card);
  const kindClass = b.kind === PARTICLE.EXTENDS || b.kind === PARTICLE.RESTRICTS ? NODE_KIND.COMPLEX_TYPE : b.kind === PARTICLE.ANY ? NODE_KIND.EXTERNAL : b.kind;
  const cls = CLS.MODEL_BOX + ' ' + kindClass + (b.root ? ' ' + CLS.CENTER : '') + (optional ? ' ' + CLS.OPTIONAL : '')
    + (b.expandable || b.ref || b.typeId ? ' ' + CLS.CLICKABLE : '')
    + (b.diff ? ' ' + b.diff : '');   // how it differs from the other side, when two models are compared
  const target = b.root ? '' : b.ref || (b.kind === PARTICLE.EXTENDS || b.kind === PARTICLE.RESTRICTS ? b.typeId : '') || b.typeId;
  const radius = familyOf(b.kind) ? FAMILY_RADIUS : b.root ? 0 : 3;   // a family object is rounded, as the graph draws it
  let g = '<g class="' + cls + '"' + dataAttr(DATA.PATH, b.path) + (target ? dataAttr(DATA.ID, target) : '') + ' transform="translate(' + x + ',' + y + ')">';
  g += '<title>' + esc(titleOf(b, traces)) + '</title>';
  const foldHandle = foldable && (hasRows(b) || b.folded);
  if (compositor) {
    // the glyph gives way to the handle when there is one, the box being no wider than its mark
    g += '<rect width="' + w + '" height="' + BOX_H + '" rx="4"/><text class="' + CLS.MODEL_GLYPH + '" x="' + (foldHandle ? (w - HANDLE) / 2 : w / 2) + '" y="' + (BOX_H / 2 + 5) + '" text-anchor="middle">' + compositor + '</text>'
      + (foldHandle ? handleSvg(w, b.foldKey, !b.folded) : '');
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
    const opens = b.expandable || b.recursive || (foldable && hasRows(b));   // a handle opens or folds the box, or ↺ says it is open above
    const declared = declaredTarget(b, traces);   // the box stands for an object the graph has: a handle shows it there
    const handleRoom = ((opens ? 1 : 0) + (declared ? 1 : 0)) * (HANDLE + 4);
    const room = w - 2 * PAD - handleRoom;   // what a line has for its texts
    const cornerRoom = corner ? corner.length * WORD_CHAR_W + TEXT_GAP : 0;
    const wordMax = Math.floor((room - (withWord ? cornerRoom : 0)) / WORD_CHAR_W);
    const nameMax = Math.min(NAME_MAX_CHARS, Math.floor((room - (withWord ? 0 : cornerRoom)) / NAME_CHAR_W));
    g += '<rect width="' + w + '" height="' + BOX_H + '" rx="' + radius + '"/>'
      + (word ? '<text class="' + CLS.MODEL_WORD + '" x="' + PAD + '" y="12">' + esc(shorten(word, wordMax)) + '</text>' : '')
      + '<text class="' + CLS.MODEL_NAME + '" x="' + PAD + '" y="' + (word ? BOX_H - 8 : BOX_H / 2 + 5) + '">' + esc(shorten(name, nameMax)) + '</text>'
      + (corner ? '<text class="' + CLS.MODEL_TYPE + '" x="' + (w - PAD - handleRoom) + '" y="' + (withWord ? 12 : BOX_H - 6) + '" text-anchor="end">' + esc(corner) + '</text>' : '');
    if (foldable ? foldHandle : b.expandable) {
      g += handleSvg(w, foldable ? b.foldKey : b.path, foldable ? !b.folded : b.expanded);
    } else if (b.recursive) {
      g += '<text class="' + CLS.MODEL_RECURSION + '" x="' + (w - 6) + '" y="' + (BOX_H / 2 + 4) + '" text-anchor="end">' + RECURSION_GLYPH + '</text>';
    }
    if (declared) {
      g += toGraphSvg(opens ? w - 2 * HANDLE - 7 : w - HANDLE - 3, t(MSG.MODEL_TO_GRAPH_TITLE, kindLabel(b.kind) + ' ' + b.name));
      // the graph's count of what uses the object, at the top right — unless a chain box already writes its kind there
      const users = traces.usersOf(declared);
      if (users > 1 && !(withWord && corner)) g += '<text class="' + CLS.MODEL_SHARED + '" x="' + (w - PAD - handleRoom) + '" y="10" text-anchor="end">' + SHARED_MARK + users + '</text>';
    }
  }
  // the occurrences under the box, unless one and only one (an attribute's use is the ? of its label)
  if (card && card !== '1' && card !== '1..1' && b.kind !== NODE_KIND.ATTRIBUTE) g += '<text class="' + CLS.CARDINALITY + '" x="' + (compositor ? w / 2 : 8) + '" y="' + (BOX_H + 12) + '"' + (compositor ? ' text-anchor="middle"' : '') + '>' + esc(card) + '</text>';
  return g + '</g>';
}

/** The handle at the right edge of a box: − while what it holds is shown, + while it is put aside. */
const handleSvg = (w, key, open) => '<g class="' + CLS.MODEL_HANDLE + '"' + dataAttr(DATA.PATH, key) + '>'
  + '<rect x="' + (w - HANDLE - 3) + '" y="' + ((BOX_H - HANDLE) / 2) + '" width="' + HANDLE + '" height="' + HANDLE + '" rx="2"/>'
  + '<text x="' + (w - HANDLE / 2 - 3) + '" y="' + (BOX_H / 2 + 4) + '" text-anchor="middle">' + (open ? COLLAPSE_GLYPH : EXPAND_GLYPH) + '</text></g>';

/** The handle to the graph, left of the opening handle when there is one: a ◎ in a small square. */
const toGraphSvg = (x, title) => '<g class="' + CLS.MODEL_TO_GRAPH + '"><title>' + esc(title) + '</title>'
  + '<rect x="' + x + '" y="' + ((BOX_H - HANDLE) / 2) + '" width="' + HANDLE + '" height="' + HANDLE + '" rx="2"/>'
  + '<text x="' + (x + HANDLE / 2) + '" y="' + (BOX_H / 2 + 4) + '" text-anchor="middle">' + TO_GRAPH_GLYPH + '</text></g>';

/** The tooltip of a box: what it is, its occurrences, its type; how many objects share it; how to open it. */
function titleOf(b, traces) {
  const parts = b.word && !b.root ? [b.word] : [];
  if (COMPOSITOR_GLYPH[b.kind]) parts.push(kindLabel(b.kind));
  else if (b.kind === NODE_KIND.ATTRIBUTE) parts.push(kindLabel(NODE_KIND.ATTRIBUTE) + ' ' + b.name);
  else parts.push((b.root ? kindLabel(b.kind) : kindLabel(b.kind === PARTICLE.ANY ? NODE_KIND.EXTERNAL : b.kind)) + ' ' + (b.kind === PARTICLE.ANY ? b.namespace : b.name));
  if (b.card && cardinalityText(b.card)) parts.push(cardinalityText(b.card));
  if (b.typeName) parts.push(b.typeName);
  const declared = declaredTarget(b, traces);
  if (declared && traces.usersOf(declared) > 1) parts.push(t(MSG.MODEL_SHARED_TITLE, traces.usersOf(declared)));
  if (b.expandable) parts.push(t(b.expanded ? MSG.MODEL_FOLD : MSG.MODEL_UNFOLD));
  if (b.recursive) parts.push(t(MSG.MODEL_RECURSIVE));
  return parts.join(TEXT.TOAST_SEPARATOR);
}

