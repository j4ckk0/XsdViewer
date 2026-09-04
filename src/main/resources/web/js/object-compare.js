/**
 * One declaration compared across two workspaces: its content model on each side, drawn by the Model
 * view, every box marked by {@link markDifferences} — red for what only the left workspace has, green
 * for what only the right one has, blue for a box whose occurrences or type changed.
 *
 * The two files need not be open in a tab: a workspace's listed file is indexed on demand
 * ({@link placeOfEntry}), which is also what lets a named type be opened from another file of that
 * same workspace, so the models are compared as deep as they can be read.
 */
import { placeOfEntry } from './declarations.js';
import { $, CLS, ID, esc } from './dom.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { DIFF, markDifferences, same } from './model-diff.js';
import { buildTree, modelSvg } from './model-view.js';
import { workspaceName } from './tabs.js';

/** The tree of {@code id} in a workspace's file, and the place it was read from; null when that side has neither. */
function treeOf(entry, ws, id) {
  if (!entry || !entry.model) return null;
  const place = placeOfEntry(entry, ws);
  const node = place.nodes.get(id);
  return node ? buildTree(node, place, { openAll: true }) : null;
}

/**
 * Draws the comparison of the declaration {@code id} between the two files of {@code pair}, and
 * returns what it says of it, for the summary line.
 */
export function renderObjectCompare(pair, id, left, right) {
  const leftTree = treeOf(pair.left, left, id);
  const rightTree = treeOf(pair.right, right, id);
  const counts = markDifferences(leftTree, rightTree);
  const label = (tree) => (tree ? t(MSG.MODEL_TITLE, kindLabel(tree.kind), tree.name) : '');
  draw(ID.COMPARE_OBJECT_LEFT, ID.COMPARE_OBJECT_LEFT_NAME, leftTree, left, label(leftTree));
  draw(ID.COMPARE_OBJECT_RIGHT, ID.COMPARE_OBJECT_RIGHT_NAME, rightTree, right, label(rightTree));
  return counts;
}

function draw(canvasId, headId, tree, ws, label) {
  const canvas = $(canvasId);
  $(headId).textContent = workspaceName(ws);
  canvas.innerHTML = tree ? modelSvg(tree, label, canvas.clientHeight) : '<div class="' + CLS.EMPTY + '">' + esc(t(MSG.COMPARE_OBJECT_ABSENT)) + '</div>';
}

/** What the comparison of two models found, in words. */
export const objectSummary = (counts) => (same(counts)
  ? t(MSG.COMPARE_OBJECT_SAME)
  : t(MSG.COMPARE_OBJECT_SUMMARY, counts[DIFF.REMOVED], counts[DIFF.ADDED], counts[DIFF.CHANGED]));
