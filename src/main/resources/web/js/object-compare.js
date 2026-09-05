/**
 * Comparing two declarations, wherever each of them lives: two versions of the same type in two
 * workspaces, two types of different names, two files that have nothing else in common.
 *
 * A declaration is put on the left or on the right from its details panel, each side chosen. Once
 * both are filled, the comparison draws them side by side, the way its own view asks:
 *
 * - **Model**: their content models, every box marked by {@link markDifferences} — red for what only
 *   the left one has, green for what only the right one has, blue for a box whose occurrences or
 *   type changed; a box holding something can be put aside, and folds with the box matching it.
 * - **Text**: the source of each declaration alone, aligned line by line by {@link textDiffHtml}.
 *   Lines are matched on their shape, spacing ignored, so the same declaration written at another
 *   depth still matches; what is shown is the source as it is written.
 * - **Graph**: the neighbourhood of each, drawn by {@link renderGraph}, the links the other side
 *   does not have marked. A link is the same when its name, its target and its cardinality are.
 *
 * Neither file need be open in a tab: a workspace's listed file is indexed on demand
 * ({@link placeOfEntry}), which is also what lets a named type be opened from another file of that
 * same workspace, so the models are compared as deep as they can be read.
 */
import { COMPARE_SECTION, TEXT, VIEW, kindOfId, nameOfId } from './constants.js';
import { cardinalityText } from './cardinality.js';
import { canCompare, textDiffHtml } from './compare.js';
import { placeOfEntry } from './declarations.js';
import { $, CLS, ID, esc, legendHtml } from './dom.js';
import { ensureModel } from './file-tabs.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { OP, diffLines, splitLines } from './diff.js';
import { renderGraph } from './graph.js';
import { DIFF, markDifferences, same } from './model-diff.js';
import { buildTree } from './model-tree.js';
import { modelSvg } from './model-view.js';
import { session } from './state.js';
import { workspaceName } from './tabs.js';
import { applyZoom } from './zoom.js';

/** The two sides of the comparison, each holding one declaration or nothing. */
export const SIDE = { LEFT: 'left', RIGHT: 'right' };
export const SIDES = [SIDE.LEFT, SIDE.RIGHT];

/** Where a marked declaration lives and which one it is; the tab may close, the file of its workspace stays. */
const markOf = (tab, id) => ({ tab, ws: tab.workspace, entry: tab.file, fileName: tab.fileName, id });

const holds = (mark, tab, id) => !!mark && mark.tab === tab && mark.id === id;

/** The side holding this declaration, or null: what the details panel says of it. */
export const sideOf = (tab, id) => SIDES.find(side => holds(session.compared[side], tab, id)) || null;

/**
 * Puts a declaration on one side of the comparison, replacing what that side held; clicking the
 * side it already holds takes it off. Each side is chosen, so which one a declaration lands on is
 * never a matter of the order things were marked in.
 */
export function markSide(side, tab, id) {
  session.compared[side] = holds(session.compared[side], tab, id) ? null : markOf(tab, id);
  folded().clear();   // the boxes put aside belonged to the pair drawn before
}

export const clearMarks = () => { session.compared = { left: null, right: null }; folded().clear(); };

/**
 * ⇄ Compare: the comparison's chip appears on the workspace bar if it is not there, and is the place
 * shown. It opens on the section the selection is ready for: two workspaces picked on the bar are a
 * file-by-file comparison, anything else the objects, which the details panel fills side by side.
 */
export function openComparison() {
  session.comparison.open = true;
  session.comparison.shown = true;
  session.comparison.section = canCompare() ? COMPARE_SECTION.FILES : COMPARE_SECTION.OBJECTS;
}

/** Its × : the place goes and takes what it was comparing with it, so it opens on nothing next time. */
export function closeComparison() {
  session.comparison = { open: false, shown: false, section: COMPARE_SECTION.OBJECTS, view: VIEW.MODEL };
  session.compareSelection.length = 0;
  clearMarks();
}

/** Which of the two comparisons the place draws. */
export function showSection(section) {
  session.comparison.section = section;
}

export function swapSides() {
  const { left, right } = session.compared;
  session.compared = { left: right, right: left };
  folded().clear();   // what only one side had swapped sides with it
}

/** The two declarations to draw, or null while either side is empty. */
export function comparedPair() {
  const { left, right } = session.compared;
  return left && right ? [left, right] : null;
}

/** The place a marked declaration is read from: its tab while it is open and parsed, else its file in the workspace. */
function placeOf(mark) {
  if (mark.tab && session.tabs.includes(mark.tab) && mark.tab.model) return mark.tab;
  return mark.entry && mark.entry.model ? placeOfEntry(mark.entry, mark.ws) : null;
}

function treeOf(mark) {
  const place = placeOf(mark);
  const node = place && place.nodes.get(mark.id);
  return node ? buildTree(node, place, { openAll: true }) : null;
}

/** The source of the file a place reads: an open tab holds it, a place made of a workspace file reads the file's. */
const sourceOf = (place) => (place ? place.text || (place.entry && place.entry.text) : null);

/** Indentation and the runs of blanks inside a line are ignored, so two declarations written at different depths still match. */
const SPACING = /\s+/g;
const normalise = (line) => line.replace(SPACING, ' ').trim();

/**
 * The source of a declaration: the lines it spans, numbered as in its file ({n, text}), empty when
 * the file does not declare it (a built-in, or an object of another schema).
 */
function declarationLines(mark) {
  const place = placeOf(mark);
  const node = place && place.nodes.get(mark.id);
  const text = sourceOf(place);
  if (!node || !text || node.line <= 0 || !node.endLine || node.endLine < node.line) return [];
  return splitLines(text).slice(node.line - 1, node.endLine).map((line, i) => ({ n: node.line + i, text: line }));
}

/** Only the drawing of the last call is written: the files may have to be parsed first. */
let drawing = 0;
/** A cache of the last comparison, so that folding a box redraws the two models without comparing them again. */
let drawn = null;

const folded = () => session.comparedFolded;

/** Folds a box, or opens it when it was folded, and redraws. */
export function toggleFolded(key) {
  if (!folded().delete(key)) folded().add(key);
  drawPair();
}

/** Every box holding something folded, or all of them open. */
export function foldAll(fold) {
  folded().clear();
  if (fold) for (const tree of drawn || []) for (const box of boxesOf(tree)) if (box.children.length || box.attributes.length) folded().add(box.foldKey);
  drawPair();
}

const boxesOf = (box) => (box ? [box, ...[...box.attributes, ...box.children].flatMap(boxesOf)] : []);

/** Draws the Compare view of the active tab: the two declarations, or what to do when there are not two. */
export async function renderObjectCompare() {
  const pair = comparedPair();
  const token = ++drawing;
  $(ID.OBJECT_COMPARE_EMPTY).classList.toggle(CLS.HIDDEN, !!pair);
  $(ID.OBJECT_COMPARE_BODY).classList.toggle(CLS.HIDDEN, !pair);
  if (!pair) {
    const held = SIDES.filter(side => session.compared[side]).length;
    $(ID.OBJECT_COMPARE_EMPTY).textContent = t(held ? MSG.OBJECT_COMPARE_ONE_MARKED : MSG.OBJECT_COMPARE_NONE_MARKED);
    $(ID.OBJECT_COMPARE_TITLE).textContent = t(MSG.OBJECT_COMPARE_TITLE_EMPTY);
    $(ID.OBJECT_COMPARE_SUMMARY).textContent = '';
    $(ID.OBJECT_COMPARE_LEGEND).innerHTML = '';
    return;
  }
  const [left, right] = pair;
  const view = session.comparison.view;
  $(ID.OBJECT_COMPARE_TITLE).textContent = t(MSG.OBJECT_COMPARE_TITLE, nameOf(left), nameOf(right));
  // the content models tell a changed box from a missing one; the source and the neighbourhood only have sides
  const chips = [[CLS.DELETED, t(MSG.COMPARE_ONLY_IN, sideName(left))], [CLS.INSERTED, t(MSG.COMPARE_ONLY_IN, sideName(right))]];
  if (view === VIEW.MODEL) chips.push([CLS.MOVED, t(MSG.COMPARE_OBJECT_CHANGED)]);
  $(ID.OBJECT_COMPARE_LEGEND).innerHTML = legendHtml(chips);
  for (const m of pair) if (m.entry && !m.entry.model) await ensureModel(m.entry, false);
  if (token !== drawing) return;   // marked or selected something else while the files were parsed
  const trees = pair.map(treeOf);
  const counts = markDifferences(trees[0], trees[1]);
  drawn = trees;
  const sides = drawSides(trees, pair);
  $(ID.OBJECT_COMPARE_SUMMARY).textContent = summaryText(view, counts, sides);
}

/** What the drawn view found: boxes for the content models, lines for the source, links for the neighbourhood. */
function summaryText(view, counts, sides) {
  if (view === VIEW.MODEL) {
    return same(counts) ? t(MSG.COMPARE_OBJECT_SAME)
      : t(MSG.COMPARE_OBJECT_SUMMARY, counts[DIFF.REMOVED], counts[DIFF.ADDED], counts[DIFF.CHANGED]);
  }
  if (!sides || (!sides.left && !sides.right)) return t(view === VIEW.TEXT ? MSG.OBJECT_COMPARE_TEXT_SAME : MSG.OBJECT_COMPARE_GRAPH_SAME);
  return t(view === VIEW.TEXT ? MSG.OBJECT_COMPARE_TEXT_SUMMARY : MSG.OBJECT_COMPARE_GRAPH_SUMMARY, sides.left, sides.right);
}

/** A side in a sentence: the declaration alone. */
const nameOf = (m) => t(MSG.MODEL_TITLE, kindLabel(kindOfMark(m)), nameOfMark(m));
/** A side of the legend: where it comes from, which is what tells the two apart. */
const sideName = (m) => m.fileName + TEXT.LIST_SEPARATOR + workspaceName(m.ws);
const kindOfMark = (m) => kindOfId(m.id);
const nameOfMark = (m) => nameOfId(m.id);

/** Draws the two models as they stand, the folded boxes shown as leaves. */
function drawPair() {
  if (!drawn) return;
  const pair = comparedPair();
  if (pair) drawSides(drawn, pair);
}

/** The two sides, drawn the way the comparison's view asks: their content models, their source, or their neighbourhood. */
function drawSides(trees, pair) {
  const asText = session.comparison.view === VIEW.TEXT;
  head(ID.OBJECT_COMPARE_LEFT_NAME, pair[0]);
  head(ID.OBJECT_COMPARE_RIGHT_NAME, pair[1]);
  $(ID.OBJECT_COMPARE_BODY).classList.toggle(CLS.AS_TEXT, asText);
  $(ID.OBJECT_COMPARE_TEXT).classList.toggle(CLS.HIDDEN, !asText);
  // boxes are put aside in the content models only: the source and the neighbourhood have none
  $(ID.OBJECT_COMPARE_FOLDS).classList.toggle(CLS.HIDDEN, session.comparison.view !== VIEW.MODEL);
  if (asText) { const found = drawText(pair); applyZoom(); return found; }
  if (session.comparison.view === VIEW.GRAPH) {
    const links = pair.map(neighbourhood);
    drawGraph(ID.OBJECT_COMPARE_LEFT, pair[0], links[1], CLS.DELETED);
    drawGraph(ID.OBJECT_COMPARE_RIGHT, pair[1], links[0], CLS.INSERTED);
    applyZoom();
    return { left: only(links[0], links[1]), right: only(links[1], links[0]) };
  }
  for (const tree of trees) for (const box of boxesOf(tree)) box.folded = folded().has(box.foldKey);
  drawModel(ID.OBJECT_COMPARE_LEFT, trees[0], pair[0]);
  drawModel(ID.OBJECT_COMPARE_RIGHT, trees[1], pair[1]);
  applyZoom();   // both drawings are written now, and take the tab's level
  return null;   // the content models are counted by the marks on their own boxes
}

/** How many keys of {@code these} the other side does not have. */
const only = (these, others) => [...these].filter(k => !others.has(k)).length;

/** Nothing a name, a kind or a label can hold, so the parts of a key cannot run into one another. */
const KEY_SEPARATOR = '\u0000';

/** A link of a neighbourhood, told apart by what it is rather than by the file it is written in. */
const linkKey = (node, edge) => [edge.label, node.kind, node.name, cardinalityText(edge)].join(KEY_SEPARATOR);

/** The links around a declaration, both ways: what its side of the comparison holds. */
function neighbourhood(mark) {
  const place = placeOf(mark);
  const node = place && place.nodes.get(mark.id);
  if (!node) return new Set();
  const keys = new Set();
  for (const e of place.outEdges.get(node.id) || []) { const n = place.nodes.get(e.to); if (n) keys.add(linkKey(n, e)); }
  for (const e of place.inEdges.get(node.id) || []) { const n = place.nodes.get(e.from); if (n) keys.add(linkKey(n, e)); }
  return keys;
}

/** One side's neighbourhood, the links the other side does not have marked with {@code mark}. */
function drawGraph(canvasId, mark, otherLinks, markClass) {
  const canvas = $(canvasId);
  const place = placeOf(mark);
  if (!place || !place.nodes.get(mark.id)) { canvas.innerHTML = absent(); return; }
  const side = Object.assign({}, place, { selected: mark.id });
  renderGraph(side, canvas, { toolbar: false, markOf: (n, e) => (n && !otherLinks.has(linkKey(n, e)) ? markClass : '') });
}

const head = (headId, mark) => { $(headId).textContent = nameOf(mark) + TEXT.TOAST_SEPARATOR + sideName(mark); };

const absent = () => '<div class="' + CLS.EMPTY + '">' + esc(t(MSG.COMPARE_OBJECT_ABSENT)) + '</div>';

function drawModel(canvasId, tree, mark) {
  const canvas = $(canvasId);
  canvas.innerHTML = tree ? modelSvg(tree, nameOf(mark), canvas.clientHeight, { foldable: true }) : absent();
}

/**
 * The source of the two declarations, each line beside the one it matches. The lines are aligned on
 * their shape rather than their spacing, so the same declaration written at another depth still
 * matches line for line; what is shown is the source as it is written.
 */
function drawText(pair) {
  const [la, lb] = pair.map(declarationLines);
  const ops = la.length && lb.length ? diffLines(la.map(l => normalise(l.text)), lb.map(l => normalise(l.text))) : null;
  $(ID.OBJECT_COMPARE_TEXT).innerHTML = ops ? textDiffHtml({ la, lb, ops }, false) : absent();
  if (!ops) return null;
  return { left: ops.filter(o => o.op === OP.DELETE).length, right: ops.filter(o => o.op === OP.INSERT).length };
}
