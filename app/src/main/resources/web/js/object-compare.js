/**
 * The Objects section of the comparison: the two declarations {@code comparison.js} holds, drawn
 * side by side the way the comparison's own view asks. The comparing itself is the server's
 * ({@code POST /api/compare/declarations}, {@code POST /api/compare/texts}): this module asks, keeps
 * the answer for the pair drawn last, and draws. Each view is one entry of {@link VIEWS}: what it draws
 * into the two panes, the summary it writes in the header, and whether its legend has a chip for a
 * changed thing (only the content models tell a changed box from a missing one).
 *
 * - **Model**: their content models, every box marked by the server; a box holding something can be
 *   put aside, and folds with the box matching it on the other side.
 * - **Text**: the source of each declaration alone, cut out of its file here, the two aligned line by
 *   line by the server on the shape of their lines rather than their spacing.
 * - **Graph**: the neighbourhood of each, drawn by {@link renderGraph}, the links the server says the
 *   other side does not have marked.
 *
 * Neither file need be open in a tab: the request carries the parsed files of each side's workspace,
 * which is also what lets a named type be opened from another file of that same workspace.
 */
import { compareDeclarations, compareTexts } from './api.js';
import { DIFF, OP, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE, TEXT, VIEW, kindOfId, nameOfId } from './constants.js';
import { cardinalityText } from './cardinality.js';
import { textDiffHtml } from './compare.js';
import { SIDES, comparedPair, foldedBoxes, placeOf } from './comparison.js';
import { declarationLines } from './declaration-source.js';
import { $, CLS, ID, esc, legendHtml } from './dom.js';
import { ensureModel } from './file-tabs.js';
import { renderGraph } from './graph.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { libraryKey, sideOf } from './model-requests.js';
import { modelSvg } from './model-view.js';
import { session } from './state.js';
import { workspaceName } from './tabs.js';
import { toast } from './toast.js';
import { applyZoom } from './zoom.js';

/** A side in a sentence: the declaration alone. */
const nameOf = (m) => t(MSG.MODEL_TITLE, kindLabel(kindOfId(m.id)), nameOfId(m.id));
/** A side of the legend and of a pane's heading: where it comes from, which is what tells the two apart. */
const sideName = (m) => m.fileName + TEXT.LIST_SEPARATOR + workspaceName(m.ws);

const nodeOf = (mark) => { const place = placeOf(mark); return place ? place.nodes.get(mark.id) : null; };

const absent = () => '<div class="' + CLS.EMPTY + '">' + esc(t(MSG.COMPARE_OBJECT_ABSENT)) + '</div>';

/** The summary of a view that only has sides: what each side alone has, or nothing. */
const sidesSummary = (found, summaryKey, sameKey) => (found && (found.left || found.right) ? t(summaryKey, found.left, found.right) : t(sameKey));

// ---- the server's comparison of the pair ----

/** What the request carries for a side: the parsed files of its workspace, its file among them, its declaration. */
const sideOfMark = (m) => sideOf(m.ws, m.entry, m.tab, m.id);

/** What tells two pairs apart, and a pair from itself once more files of a workspace are parsed. */
const pairKey = (pair) => pair.map(m => [workspaceName(m.ws), m.fileName, m.id, libraryKey(m.ws)].join('|')).join('||');

/**
 * The server's answer for the pair drawn last — the two trees, every box marked; the counts; the links
 * only one side has — kept so that folding a box or switching between the models and the graphs asks
 * nothing again.
 */
let compared = null;

async function comparedOf(pair) {
  const key = pairKey(pair);
  if (!compared || compared.key !== key) compared = Object.assign({ key }, await compareDeclarations(sideOfMark(pair[0]), sideOfMark(pair[1])));
  return compared;
}

// ---- differences only ----

/** The option of the section, remembered across sessions: only what differs is drawn, in whichever view. */
const isDiffOnly = () => $(ID.OBJECT_COMPARE_DIFF_ONLY).checked;

export function initDiffOnly() {
  try { $(ID.OBJECT_COMPARE_DIFF_ONLY).checked = localStorage.getItem(STORAGE_KEY.OBJECT_COMPARE_DIFF_ONLY) === STORAGE_TRUE; } catch (e) { /* storage unavailable */ }
}

export function rememberDiffOnly() {
  try { localStorage.setItem(STORAGE_KEY.OBJECT_COMPARE_DIFF_ONLY, isDiffOnly() ? STORAGE_TRUE : STORAGE_FALSE); } catch (e) { /* storage unavailable */ }
}

/** With differences only, the text keeps one line of context around a change, as the Files section does. */
const CONTEXT = { keep: 1, above: 2 };

/**
 * What "differences only" keeps of a tree: the boxes that differ and those on the way to one, as
 * copies so the tree itself stays whole for the folds and the next redraw. Null when nothing differs.
 */
function differing(box) {
  const attributes = box.attributes.map(differing).filter(Boolean);
  const children = box.children.map(differing).filter(Boolean);
  if (box.diff === DIFF.SAME && !attributes.length && !children.length) return null;
  return Object.assign({}, box, { attributes, children });
}

/** The tree as drawn: whole, or its differences under its root when the option is on. */
const shownTree = (tree) => (!tree || !isDiffOnly() ? tree : differing(tree) || Object.assign({}, tree, { attributes: [], children: [] }));

// ---- the content models ----

const boxesOf = (box) => (box ? [box, ...[...box.attributes, ...box.children].flatMap(boxesOf)] : []);

function drawModel(canvasId, tree, mark) {
  const canvas = $(canvasId);
  canvas.innerHTML = tree ? modelSvg(tree, nameOf(mark), canvas.clientHeight, { foldable: true }) : absent();
}

/** Draws the two trees as they stand, the folded boxes shown as leaves. */
function drawTrees(trees, pair) {
  for (const tree of trees) for (const box of boxesOf(tree)) box.folded = foldedBoxes().has(box.foldKey);
  drawModel(ID.OBJECT_COMPARE_LEFT, shownTree(trees[0]), pair[0]);
  drawModel(ID.OBJECT_COMPARE_RIGHT, shownTree(trees[1]), pair[1]);
}

async function drawModels(pair) {
  const c = await comparedOf(pair);
  drawTrees([c.left, c.right], pair);
  const { changed, removed, added } = c.counts;
  return !changed && !removed && !added ? t(MSG.COMPARE_OBJECT_SAME) : t(MSG.COMPARE_OBJECT_SUMMARY, removed, added, changed);
}

/** Folds a box, or opens it when it was folded, and redraws the models. */
export function toggleFolded(key) {
  if (!foldedBoxes().delete(key)) foldedBoxes().add(key);
  redrawModels();
}

/** Every box holding something folded, or all of them open. */
export function foldAll(fold) {
  foldedBoxes().clear();
  if (fold && compared) {
    for (const tree of [compared.left, compared.right]) for (const box of boxesOf(tree)) if (box.children.length || box.attributes.length) foldedBoxes().add(box.foldKey);
  }
  redrawModels();
}

function redrawModels() {
  const pair = comparedPair();
  if (compared && pair && compared.key === pairKey(pair)) {
    drawTrees([compared.left, compared.right], pair);
    applyZoom();
  }
}

// ---- the source ----

/** The source of the file a place reads: an open tab holds it, a place made of a workspace file reads the file's. */
const sourceOf = (place) => (place ? place.text || (place.entry && place.entry.text) : null);

const linesOf = (mark) => declarationLines(sourceOf(placeOf(mark)), nodeOf(mark));

/** The lines the server numbered from one, numbered as in their file again. */
const numbered = (lines, first) => lines.map(l => ({ n: l.n + first - 1, text: l.text }));

/** The two sources in one scrolling area, each line beside the one it matches; what is shown is the source as it is written. */
async function drawText(pair) {
  const [la, lb] = pair.map(linesOf);
  if (!la.length || !lb.length) {
    $(ID.OBJECT_COMPARE_TEXT).innerHTML = absent();
    return sidesSummary(null, MSG.OBJECT_COMPARE_TEXT_SUMMARY, MSG.OBJECT_COMPARE_TEXT_SAME);
  }
  const join = (lines) => lines.map(l => l.text).join('\n');
  const r = await compareTexts(join(la), join(lb), { ignoreSpacing: true });
  $(ID.OBJECT_COMPARE_TEXT).innerHTML = textDiffHtml({ la: numbered(r.la, la[0].n), lb: numbered(r.lb, lb[0].n), ops: r.ops }, isDiffOnly() ? CONTEXT : null);
  const found = r.ops && { left: r.ops.filter(o => o.op === OP.DELETE).length, right: r.ops.filter(o => o.op === OP.INSERT).length };
  return sidesSummary(found, MSG.OBJECT_COMPARE_TEXT_SUMMARY, MSG.OBJECT_COMPARE_TEXT_SAME);
}

// ---- the neighbourhoods ----

/** Nothing a name, a kind or a label can hold, so the parts of a key cannot run into one another. */
const KEY_SEPARATOR = '\u0000';

/** A link of a neighbourhood as the server keys it too: its word, the other end's kind and name, its occurrences. */
const linkKey = (label, kind, name, edge) => [label, kind, name, cardinalityText(edge)].join(KEY_SEPARATOR);

/** The keys of the links the server says one side alone has. */
const keysOf = (links) => new Set(links.map(l => linkKey(l.label, l.kind, l.name, l)));

/** One side's neighbourhood, the links the other side does not have wearing {@code markClass}. */
function drawGraph(canvasId, mark, onlyHere, markClass) {
  const canvas = $(canvasId);
  const place = placeOf(mark);
  if (!place || !place.nodes.get(mark.id)) { canvas.innerHTML = absent(); return; }
  const side = Object.assign({}, place, { selected: mark.id });
  renderGraph(side, canvas, { toolbar: false, onlyMarked: isDiffOnly(), markOf: (n, e) => (n && onlyHere.has(linkKey(e.label, n.kind, n.name, e)) ? markClass : '') });
}

async function drawGraphs(pair) {
  const c = await comparedOf(pair);
  const onlyLeft = keysOf(c.links.onlyLeft), onlyRight = keysOf(c.links.onlyRight);
  drawGraph(ID.OBJECT_COMPARE_LEFT, pair[0], onlyLeft, CLS.DELETED);
  drawGraph(ID.OBJECT_COMPARE_RIGHT, pair[1], onlyRight, CLS.INSERTED);
  return sidesSummary({ left: onlyLeft.size, right: onlyRight.size }, MSG.OBJECT_COMPARE_GRAPH_SUMMARY, MSG.OBJECT_COMPARE_GRAPH_SAME);
}

// ---- the section ----

/** How each view draws the two sides: {@code draw(pair)} fills the panes and answers the header's summary. */
const VIEWS = {
  [VIEW.MODEL]: { draw: drawModels, changedChip: true, foldable: true, asText: false },
  [VIEW.TEXT]: { draw: drawText, changedChip: false, foldable: false, asText: true },
  [VIEW.GRAPH]: { draw: drawGraphs, changedChip: false, foldable: false, asText: false },
};

/** Only the drawing of the last call is written: the files may have to be parsed, and the server asked. */
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
  try {
    const summary = await view.draw(pair);
    if (token !== drawing) return;   // ... or while the server compared
    $(ID.OBJECT_COMPARE_SUMMARY).textContent = summary;
    applyZoom();   // the panes hold new drawings, which take the tab's level
  } catch (e) {
    toast(e.message);
  }
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
