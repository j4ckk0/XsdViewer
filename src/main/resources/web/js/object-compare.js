/**
 * The Objects section of the comparison: the two declarations {@code comparison.js} holds, drawn
 * side by side the way the comparison's own view asks. Each view is one entry of {@link VIEWS}: what
 * it draws into the two panes, the summary it writes in the header, and whether its legend has a
 * chip for a changed thing (only the content models tell a changed box from a missing one).
 *
 * - **Model**: their content models, every box marked by {@link markDifferences}; a box holding
 *   something can be put aside, and folds with the box matching it on the other side.
 * - **Text**: the source of each declaration alone, the two aligned line by line by the renderer
 *   of the Files section, on the shape of their lines rather than their spacing.
 * - **Graph**: the neighbourhood of each, drawn by {@link renderGraph}, the links the other side
 *   does not have marked.
 *
 * Neither file need be open in a tab: a workspace's listed file is indexed on demand, which is also
 * what lets a named type be opened from another file of that same workspace.
 */
import { TEXT, VIEW, kindOfId, nameOfId } from './constants.js';
import { textDiffHtml } from './compare.js';
import { SIDES, comparedPair, foldedBoxes, placeOf } from './comparison.js';
import { declarationLines, shapeOf } from './declaration-source.js';
import { $, CLS, ID, esc, legendHtml } from './dom.js';
import { OP, diffLines } from './diff.js';
import { ensureModel } from './file-tabs.js';
import { renderGraph } from './graph.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { DIFF, markDifferences, same } from './model-diff.js';
import { buildTree } from './model-tree.js';
import { modelSvg } from './model-view.js';
import { linkKey, neighbourhoodKeys } from './schema-diff.js';
import { session } from './state.js';
import { workspaceName } from './tabs.js';
import { applyZoom } from './zoom.js';

/** A side in a sentence: the declaration alone. */
const nameOf = (m) => t(MSG.MODEL_TITLE, kindLabel(kindOfId(m.id)), nameOfId(m.id));
/** A side of the legend and of a pane's heading: where it comes from, which is what tells the two apart. */
const sideName = (m) => m.fileName + TEXT.LIST_SEPARATOR + workspaceName(m.ws);

const nodeOf = (mark) => { const place = placeOf(mark); return place ? place.nodes.get(mark.id) : null; };

const absent = () => '<div class="' + CLS.EMPTY + '">' + esc(t(MSG.COMPARE_OBJECT_ABSENT)) + '</div>';

/** How many keys of {@code these} the other side does not have. */
const only = (these, others) => [...these].filter(k => !others.has(k)).length;

/** The summary of a view that only has sides: what each side alone has, or nothing. */
const sidesSummary = (found, summaryKey, sameKey) => (found && (found.left || found.right) ? t(summaryKey, found.left, found.right) : t(sameKey));

// ---- the content models ----

/** The two trees drawn last, so that folding a box redraws them without comparing them again. */
let drawn = null;

const treeOf = (mark) => { const node = nodeOf(mark); return node ? buildTree(node, placeOf(mark), { openAll: true }) : null; };
const boxesOf = (box) => (box ? [box, ...[...box.attributes, ...box.children].flatMap(boxesOf)] : []);

function drawModel(canvasId, tree, mark) {
  const canvas = $(canvasId);
  canvas.innerHTML = tree ? modelSvg(tree, nameOf(mark), canvas.clientHeight, { foldable: true }) : absent();
}

/** Draws the two trees as they stand, the folded boxes shown as leaves. */
function drawTrees(trees, pair) {
  for (const tree of trees) for (const box of boxesOf(tree)) box.folded = foldedBoxes().has(box.foldKey);
  drawModel(ID.OBJECT_COMPARE_LEFT, trees[0], pair[0]);
  drawModel(ID.OBJECT_COMPARE_RIGHT, trees[1], pair[1]);
}

function drawModels(pair) {
  const trees = pair.map(treeOf);
  const counts = markDifferences(trees[0], trees[1]);
  drawn = trees;
  drawTrees(trees, pair);
  return same(counts) ? t(MSG.COMPARE_OBJECT_SAME)
    : t(MSG.COMPARE_OBJECT_SUMMARY, counts[DIFF.REMOVED], counts[DIFF.ADDED], counts[DIFF.CHANGED]);
}

/** Folds a box, or opens it when it was folded, and redraws the models. */
export function toggleFolded(key) {
  if (!foldedBoxes().delete(key)) foldedBoxes().add(key);
  redrawModels();
}

/** Every box holding something folded, or all of them open. */
export function foldAll(fold) {
  foldedBoxes().clear();
  if (fold) for (const tree of drawn || []) for (const box of boxesOf(tree)) if (box.children.length || box.attributes.length) foldedBoxes().add(box.foldKey);
  redrawModels();
}

function redrawModels() {
  const pair = comparedPair();
  if (drawn && pair) { drawTrees(drawn, pair); applyZoom(); }
}

// ---- the source ----

/** The source of the file a place reads: an open tab holds it, a place made of a workspace file reads the file's. */
const sourceOf = (place) => (place ? place.text || (place.entry && place.entry.text) : null);

const linesOf = (mark) => declarationLines(sourceOf(placeOf(mark)), nodeOf(mark));

/** The two sources in one scrolling area, each line beside the one it matches; what is shown is the source as it is written. */
function drawText(pair) {
  const [la, lb] = pair.map(linesOf);
  const ops = la.length && lb.length ? diffLines(la.map(l => shapeOf(l.text)), lb.map(l => shapeOf(l.text))) : null;
  $(ID.OBJECT_COMPARE_TEXT).innerHTML = ops ? textDiffHtml({ la, lb, ops }, null) : absent();
  const found = ops && { left: ops.filter(o => o.op === OP.DELETE).length, right: ops.filter(o => o.op === OP.INSERT).length };
  return sidesSummary(found, MSG.OBJECT_COMPARE_TEXT_SUMMARY, MSG.OBJECT_COMPARE_TEXT_SAME);
}

// ---- the neighbourhoods ----

const neighbourhoodOf = (mark) => { const place = placeOf(mark); return place ? neighbourhoodKeys(place, mark.id) : new Set(); };

/** One side's neighbourhood, the links the other side does not have wearing {@code markClass}. */
function drawGraph(canvasId, mark, otherLinks, markClass) {
  const canvas = $(canvasId);
  const place = placeOf(mark);
  if (!place || !place.nodes.get(mark.id)) { canvas.innerHTML = absent(); return; }
  const side = Object.assign({}, place, { selected: mark.id });
  renderGraph(side, canvas, { toolbar: false, markOf: (n, e) => (n && !otherLinks.has(linkKey(n, e)) ? markClass : '') });
}

function drawGraphs(pair) {
  const links = pair.map(neighbourhoodOf);
  drawGraph(ID.OBJECT_COMPARE_LEFT, pair[0], links[1], CLS.DELETED);
  drawGraph(ID.OBJECT_COMPARE_RIGHT, pair[1], links[0], CLS.INSERTED);
  return sidesSummary({ left: only(links[0], links[1]), right: only(links[1], links[0]) }, MSG.OBJECT_COMPARE_GRAPH_SUMMARY, MSG.OBJECT_COMPARE_GRAPH_SAME);
}

// ---- the section ----

/** How each view draws the two sides: {@code draw(pair)} fills the panes and answers the header's summary. */
const VIEWS = {
  [VIEW.MODEL]: { draw: drawModels, changedChip: true, foldable: true, asText: false },
  [VIEW.TEXT]: { draw: drawText, changedChip: false, foldable: false, asText: true },
  [VIEW.GRAPH]: { draw: drawGraphs, changedChip: false, foldable: false, asText: false },
};

/** Only the drawing of the last call is written: the files may have to be parsed first. */
let drawing = 0;

/** Draws the Objects section: the two declarations, or what to do when there are not two. */
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
  const view = VIEWS[session.comparison.view];
  header(pair, view);
  for (const m of pair) if (m.entry && !m.entry.model) await ensureModel(m.entry, false);
  if (token !== drawing) return;   // marked or selected something else while the files were parsed
  $(ID.OBJECT_COMPARE_SUMMARY).textContent = view.draw(pair);
  applyZoom();   // the panes hold new drawings, which take the tab's level
}

/** The title, the legend, the pane headings, and the panes arranged for the view: two canvases, or one text area under the two names. */
function header([left, right], view) {
  $(ID.OBJECT_COMPARE_TITLE).textContent = t(MSG.OBJECT_COMPARE_TITLE, nameOf(left), nameOf(right));
  const chips = [[CLS.DELETED, t(MSG.COMPARE_ONLY_IN, sideName(left))], [CLS.INSERTED, t(MSG.COMPARE_ONLY_IN, sideName(right))]];
  if (view.changedChip) chips.push([CLS.MOVED, t(MSG.COMPARE_OBJECT_CHANGED)]);
  $(ID.OBJECT_COMPARE_LEGEND).innerHTML = legendHtml(chips);
  $(ID.OBJECT_COMPARE_LEFT_NAME).textContent = nameOf(left) + TEXT.TOAST_SEPARATOR + sideName(left);
  $(ID.OBJECT_COMPARE_RIGHT_NAME).textContent = nameOf(right) + TEXT.TOAST_SEPARATOR + sideName(right);
  $(ID.OBJECT_COMPARE_BODY).classList.toggle(CLS.AS_TEXT, view.asText);
  $(ID.OBJECT_COMPARE_TEXT).classList.toggle(CLS.HIDDEN, !view.asText);
  $(ID.OBJECT_COMPARE_FOLDS).classList.toggle(CLS.HIDDEN, !view.foldable);
}
