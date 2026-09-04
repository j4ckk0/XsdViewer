/**
 * Comparing two declarations, wherever each of them lives: two versions of the same type in two
 * workspaces, two types of different names, two files that have nothing else in common.
 *
 * A declaration is *marked* from its details panel; two marks at a time, the oldest giving way to a
 * third. The **Compare** view then draws the content model of each side by side, every box marked by
 * {@link markDifferences} — red for what only the left one has, green for what only the right one
 * has, blue for a box whose occurrences or type changed. With one mark it compares that one with the
 * declaration selected in the active tab, so a reference can be held while the rest is browsed.
 *
 * Neither file need be open in a tab: a workspace's listed file is indexed on demand
 * ({@link placeOfEntry}), which is also what lets a named type be opened from another file of that
 * same workspace, so the models are compared as deep as they can be read.
 */
import { TEXT, kindOfId, nameOfId } from './constants.js';
import { placeOfEntry } from './declarations.js';
import { $, CLS, ID, esc, legendHtml } from './dom.js';
import { ensureModel } from './file-tabs.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
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
  $(ID.OBJECT_COMPARE_TITLE).textContent = t(MSG.OBJECT_COMPARE_TITLE, nameOf(left), nameOf(right));
  $(ID.OBJECT_COMPARE_LEGEND).innerHTML = legendHtml([[CLS.DELETED, t(MSG.COMPARE_ONLY_IN, sideName(left))],
    [CLS.INSERTED, t(MSG.COMPARE_ONLY_IN, sideName(right))], [CLS.MOVED, t(MSG.COMPARE_OBJECT_CHANGED)]]);
  for (const m of pair) if (m.entry && !m.entry.model) await ensureModel(m.entry, false);
  if (token !== drawing) return;   // marked or selected something else while the files were parsed
  const trees = pair.map(treeOf);
  const counts = markDifferences(trees[0], trees[1]);
  drawn = trees;
  drawSides(trees, pair);
  $(ID.OBJECT_COMPARE_SUMMARY).textContent = same(counts)
    ? t(MSG.COMPARE_OBJECT_SAME)
    : t(MSG.COMPARE_OBJECT_SUMMARY, counts[DIFF.REMOVED], counts[DIFF.ADDED], counts[DIFF.CHANGED]);
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

function drawSides(trees, pair) {
  for (const tree of trees) for (const box of boxesOf(tree)) box.folded = folded().has(box.foldKey);
  draw(ID.OBJECT_COMPARE_LEFT, ID.OBJECT_COMPARE_LEFT_NAME, trees[0], pair[0]);
  draw(ID.OBJECT_COMPARE_RIGHT, ID.OBJECT_COMPARE_RIGHT_NAME, trees[1], pair[1]);
  applyZoom();   // both models are drawn now, and take the tab's level
}

function draw(canvasId, headId, tree, mark) {
  const canvas = $(canvasId);
  $(headId).textContent = nameOf(mark) + TEXT.TOAST_SEPARATOR + sideName(mark);
  canvas.innerHTML = tree
    ? modelSvg(tree, nameOf(mark), canvas.clientHeight, { foldable: true })
    : '<div class="' + CLS.EMPTY + '">' + esc(t(MSG.COMPARE_OBJECT_ABSENT)) + '</div>';
}
