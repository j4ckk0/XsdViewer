/**
 * Comparing two workspaces (selected with Ctrl+click on their chips), folder-comparison style:
 * files paired by name and marked identical / different / only on one side, a different pair
 * expandable to its schema and line differences — or opened in a tab of its own (compare.file).
 */
import { businessLines } from './business-lines.js';
import { cardinalityText } from './cardinality.js';
import { STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc, selector } from './dom.js';
import { OP, diffLines, onlyMoves, splitLines } from './diff.js';
import { plural, t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { diffModels } from './schema-diff.js';
import { objectSummary, renderObjectCompare } from './object-compare.js';
import { kindOfId, nameOfId } from './constants.js';
import { session } from './state.js';
import { ensureModel } from './file-tabs.js';
import { activateTab, newTab, workspaceName } from './tabs.js';

export const COMPARED_WORKSPACES = 2;
/** Equal runs longer than FOLD_ABOVE are folded to one line in the text diff, FOLD_KEEP lines kept on each side; with "differences only", one line of context. */
const FOLD_ABOVE = 6, FOLD_KEEP = 2, CONTEXT_LINES = 1;
const STATUS = { SAME: 'same', DIFFERENT: 'different', MOVED: 'moved', ONLY_LEFT: 'only-left', ONLY_RIGHT: 'only-right' };
const STATUS_TEXT = { [STATUS.SAME]: MSG.COMPARE_SAME, [STATUS.DIFFERENT]: MSG.COMPARE_DIFFERENT, [STATUS.MOVED]: MSG.COMPARE_MOVED, [STATUS.ONLY_LEFT]: MSG.COMPARE_ONLY_IN, [STATUS.ONLY_RIGHT]: MSG.COMPARE_ONLY_IN };
const LINE_BREAK = /\r\n/g;
const ARROW = ' → ';

/** The two options of the view, remembered across sessions: "business lines only" (on by default) and "differences only". */
const OPTIONS = [[ID.COMPARE_BUSINESS_ONLY, STORAGE_KEY.COMPARE_BUSINESS_ONLY, true], [ID.COMPARE_DIFF_ONLY, STORAGE_KEY.COMPARE_DIFF_ONLY, false]];

/** Comments, xs:annotation, the wiring tags (XML declaration, xs:schema, xs:import, xs:include), blank lines and indentation ignored. */
export const isBusinessOnly = () => $(ID.COMPARE_BUSINESS_ONLY).checked;
/** Identical files hidden, identical lines reduced to one line of context. */
export const isDiffOnly = () => $(ID.COMPARE_DIFF_ONLY).checked;

export function initOptions() {
  for (const [id, key, fallback] of OPTIONS) {
    let on = fallback;
    try {
      const stored = localStorage.getItem(key);
      if (stored !== null) on = stored === STORAGE_TRUE;
    } catch (e) { /* storage unavailable */ }
    $(id).checked = on;
  }
}

export function rememberOptions() {
  for (const [id, key] of OPTIONS) {
    try { localStorage.setItem(key, $(id).checked ? STORAGE_TRUE : STORAGE_FALSE); } catch (e) { /* storage unavailable */ }
  }
}

/** The lines compared, with their original numbers: every line, or the business lines only. */
function comparedLines(text) {
  const raw = canonical(text);
  return isBusinessOnly() ? businessLines(raw) : splitLines(raw).map((line, i) => ({ n: i + 1, text: line }));
}

const comparedText = (text) => comparedLines(text).map(l => l.text).join('\n');

/** The line diff of a pair, computed once: {la, lb, ops} (ops null when the texts are too different to align). */
function lineDiff(pair) {
  if (!pair.diff) {
    const la = comparedLines(pair.left.text), lb = comparedLines(pair.right.text);
    pair.diff = { la, lb, ops: diffLines(la.map(l => l.text), lb.map(l => l.text)) };
  }
  return pair.diff;
}

/** The pairs of the comparison being shown, by row index. */
let pairs = [];

const isExpandable = (pair) => pair.status === STATUS.DIFFERENT || pair.status === STATUS.MOVED;
const sameSides = (tab, left, right) => tab.compare && tab.compare.left === left && tab.compare.right === right;

/** Ctrl+click on a chip: toggles the workspace's selection; the oldest selection gives way to a third. */
export function toggleSelection(ws) {
  const sel = session.compareSelection;
  const i = sel.indexOf(ws);
  if (i >= 0) sel.splice(i, 1);
  else { sel.push(ws); if (sel.length > COMPARED_WORKSPACES) sel.shift(); }
}

export const canCompare = () => session.compareSelection.length === COMPARED_WORKSPACES;

/**
 * The two files holding {@code fileName} in the two selected workspaces, when one declaration of it
 * can be compared across them; null when fewer than two are selected, or one of them lacks the file.
 */
export function comparablePair(fileName) {
  if (!canCompare() || !fileName) return null;
  const [left, right] = session.compareSelection;
  const l = filesOf(left).get(fileName), r = filesOf(right).get(fileName);
  return l && r ? { name: fileName, left: l, right: r } : null;
}

/** Opens (or brings to front) the tab comparing the declaration {@code id} of {@code fileName} across the two selected workspaces. */
export function openObjectCompare(id, fileName) {
  if (!comparablePair(fileName)) return false;
  const [left, right] = session.compareSelection;
  let tab = session.tabs.find(x => sameSides(x, left, right) && x.compare.file === fileName && x.compare.object === id);
  if (!tab) {
    tab = newTab();
    tab.compare = { left, right, file: fileName, object: id };
  }
  activateTab(tab);
  return true;
}

/** Clear: no workspace is selected for a comparison any more; the caller redraws the bars. */
export function clearSelection() {
  session.compareSelection = [];
}

/** Opens (or brings to front) the comparison tab of the two selected workspaces; the caller redraws the page. Returns false when two are not selected. */
export function startCompare() {
  if (!canCompare()) return false;
  const [left, right] = session.compareSelection;
  let tab = session.tabs.find(x => sameSides(x, left, right) && !x.compare.file);
  if (!tab) {
    tab = newTab();
    tab.compare = { left, right, file: null };
  }
  activateTab(tab);
  return true;
}

/**
 * The row's button, or a double-click on it: opens (or brings to front) a tab showing the differences of that file
 * pair only, next to the comparison; the caller redraws the page. Returns false for a row without differences.
 */
export function openPairTab(row) {
  const pair = pairs[+row.dataset[DATA.ROW_INDEX]];
  if (!pair || !isExpandable(pair)) return false;
  const { left, right } = session.active.compare;
  let tab = session.tabs.find(x => sameSides(x, left, right) && x.compare.file === pair.name);
  if (!tab) {
    tab = newTab();
    tab.compare = { left, right, file: pair.name };
  }
  activateTab(tab);
  return true;
}

/** Every file a workspace knows (open in a tab or only listed) by file name; the first one when a name appears twice. */
function filesOf(ws) {
  const byName = new Map();
  for (const f of ws.files) if (!byName.has(f.name)) byName.set(f.name, f);
  return byName;
}

/** Where a file is, for the tooltip of its cell: its path, else where it sits in the folder it came from, else its name alone. */
const shownPath = (f) => f.path || f.rel || f.name;

const canonical = (text) => text.replace(LINE_BREAK, '\n');

/** [{name, left, right, status}] for every file name of either workspace, sorted. */
function pairFiles(left, right) {
  const l = filesOf(left), r = filesOf(right);
  const names = [...new Set([...l.keys(), ...r.keys()])].sort((a, b) => a.localeCompare(b));
  return names.map(name => {
    const a = l.get(name) || null, b = r.get(name) || null;
    const pair = { name, left: a, right: b, status: STATUS.DIFFERENT, diff: null };
    if (!a) pair.status = STATUS.ONLY_RIGHT;
    else if (!b) pair.status = STATUS.ONLY_LEFT;
    else if (comparedText(a.text) === comparedText(b.text)) pair.status = STATUS.SAME;
    else if (lineDiff(pair).ops && onlyMoves(lineDiff(pair).ops)) pair.status = STATUS.MOVED;
    return pair;
  });
}

/** Draws the active comparison tab: every pair of the two workspaces, or the one file pair of a tab opened from a row (its differences shown at once). */
export function renderCompare() {
  const { left, right, file, object } = session.active.compare;
  const ln = workspaceName(left), rn = workspaceName(right);
  $(ID.COMPARE_OBJECT).classList.toggle(CLS.HIDDEN, !object);
  $(ID.COMPARE_TABLE).classList.toggle(CLS.HIDDEN, !!object);
  // the two options are the file comparison's: what a model holds is neither a line nor a business line
  for (const opt of $(ID.COMPARE).querySelectorAll(selector(CLS.OPTION))) opt.classList.toggle(CLS.HIDDEN, !!object);
  if (object) { renderObject(object, file, left, right, ln, rn); return; }
  pairs = pairFiles(left, right);
  if (file) pairs = pairs.filter(p => p.name === file);
  const count = (s) => pairs.filter(p => p.status === s).length;
  const one = file ? pairs[0] : null;
  const side = (p) => p.status === STATUS.ONLY_LEFT ? ln : p.status === STATUS.ONLY_RIGHT ? rn : '';
  $(ID.COMPARE_TITLE).textContent = file ? t(MSG.COMPARE_FILE_TITLE, file, ln, rn) : t(MSG.COMPARE_TITLE, ln, rn);
  $(ID.COMPARE_SUMMARY).textContent = file ? (one ? t(STATUS_TEXT[one.status], side(one)) : '')
    : t(MSG.COMPARE_SUMMARY, pairs.length, count(STATUS.SAME), count(STATUS.DIFFERENT), count(STATUS.MOVED), count(STATUS.ONLY_LEFT), ln, count(STATUS.ONLY_RIGHT), rn)
;
  $(ID.COMPARE_TOOLS).classList.toggle(CLS.HIDDEN, !!file);
  // the colours of the line comparison: lines only on the left (red), only on the right (green), moved (blue)
  $(ID.COMPARE_LEGEND).innerHTML = [[CLS.DELETED, t(MSG.COMPARE_ONLY_IN, ln)], [CLS.INSERTED, t(MSG.COMPARE_ONLY_IN, rn)], [CLS.MOVED, t(MSG.COMPARE_LEGEND_MOVED)]]
    .map(([cls, text]) => '<span class="' + CLS.LEGEND_ENTRY + ' ' + cls + '">' + esc(text) + '</span>').join('');
  let html = '<thead><tr><th>' + esc(t(MSG.COMPARE_FILE)) + '</th><th>' + esc(ln) + '</th><th>' + esc(t(MSG.COMPARE_STATUS)) + '</th><th>' + esc(rn) + '</th></tr></thead><tbody>';
  const openButton = '<button class="' + CLS.COMPARE_OPEN + '" type="button" title="' + esc(t(MSG.COMPARE_OPEN_TAB)) + '">' + esc(t(MSG.COMPARE_OPEN_TAB_LABEL)) + '</button>';
  pairs.forEach((p, i) => {
    if (!file && isDiffOnly() && p.status === STATUS.SAME) return;
    html += '<tr class="' + CLS.COMPARE_ROW + ' ' + p.status + (isExpandable(p) ? ' ' + CLS.EXPANDABLE : '') + '"' + dataAttr(DATA.ROW_INDEX, i) + '>'
      + '<td class="' + CLS.COMPARE_NAME + '">' + esc(p.name) + '</td>'
      // the side columns say whether the workspace holds the file, by its name: the path would be long, and is the tooltip
      + '<td class="' + CLS.COMPARE_PATH + '" title="' + esc(p.left ? shownPath(p.left) : '') + '">' + esc(p.left ? p.left.name : '') + '</td>'
      + '<td class="' + CLS.COMPARE_STATUS + '">' + esc(t(STATUS_TEXT[p.status], side(p))) + (isExpandable(p) && !file ? openButton : '') + '</td>'
      + '<td class="' + CLS.COMPARE_PATH + '" title="' + esc(p.right ? shownPath(p.right) : '') + '">' + esc(p.right ? p.right.name : '') + '</td></tr>';
  });
  $(ID.COMPARE_TABLE).innerHTML = html + '</tbody>';
  if (one && isExpandable(one)) toggleDetail($(ID.COMPARE_TABLE).querySelector('.' + CLS.EXPANDABLE));
}

/**
 * One declaration compared across the two workspaces: the two content models side by side. The files
 * are parsed first when they were only listed, so the drawing follows rather than blocks.
 */
async function renderObject(id, fileName, left, right, ln, rn) {
  $(ID.COMPARE_TITLE).textContent = t(MSG.COMPARE_OBJECT_TITLE, kindLabel(kindOfId(id)), nameOfId(id), fileName, ln, rn);
  $(ID.COMPARE_TOOLS).classList.add(CLS.HIDDEN);
  $(ID.COMPARE_LEGEND).innerHTML = [[CLS.DELETED, t(MSG.COMPARE_ONLY_IN, ln)], [CLS.INSERTED, t(MSG.COMPARE_ONLY_IN, rn)], [CLS.MOVED, t(MSG.COMPARE_OBJECT_CHANGED)]]
    .map(([cls, text]) => '<span class="' + CLS.LEGEND_ENTRY + ' ' + cls + '">' + esc(text) + '</span>').join('');
  const pair = comparablePairIn(left, right, fileName);
  for (const entry of [pair.left, pair.right]) if (entry) await ensureModel(entry, false);
  const tab = session.active;
  if (!tab.compare || tab.compare.object !== id) return;   // the tab changed while the files were being parsed
  $(ID.COMPARE_SUMMARY).textContent = objectSummary(renderObjectCompare(pair, id, left, right));
}

/** The two files named {@code fileName} in two given workspaces (the selection may have moved on since the tab was opened). */
const comparablePairIn = (left, right, fileName) => ({ name: fileName, left: filesOf(left).get(fileName) || null, right: filesOf(right).get(fileName) || null });

/** Click on a row: shows / hides the differences of that pair under it (the files are parsed first when they were only listed). */
export async function toggleDetail(row) {
  const pair = pairs[+row.dataset[DATA.ROW_INDEX]];
  if (!pair || !isExpandable(pair)) return;
  const next = row.nextElementSibling;
  if (next && next.classList.contains(CLS.COMPARE_DETAIL)) { next.remove(); row.classList.remove(CLS.OPEN); return; }
  if (row.classList.contains(CLS.OPEN)) return;   // being opened
  row.classList.add(CLS.OPEN);
  await ensureModel(pair.left, false);
  await ensureModel(pair.right, false);
  if (!row.isConnected) return;   // the table was redrawn meanwhile
  const detail = document.createElement('tr');
  detail.className = CLS.COMPARE_DETAIL;
  detail.innerHTML = '<td colspan="4">' + schemaDiffHtml(pair) + textDiffHtml(pair) + '</td>';
  row.after(detail);
}

/** Opens the differences of every expandable row, or closes them all. */
export async function setAllDetails(open) {
  for (const row of [...$(ID.COMPARE_TABLE).querySelectorAll('.' + CLS.EXPANDABLE)]) {
    const isOpen = row.classList.contains(CLS.OPEN);
    if (open !== isOpen) await toggleDetail(row);
  }
}

function schemaDiffHtml(pair) {
  if (!pair.left.model || !pair.right.model) return '<p class="' + CLS.META + '">' + esc(t(MSG.FILES_NOT_A_SCHEMA)) + '</p>';
  const d = diffModels(pair.left.model, pair.right.model);
  if (d.same) return '<p class="' + CLS.META + '">' + esc(t(MSG.COMPARE_SAME_MODEL)) + '</p>';
  const ln = workspaceName(session.active.compare.left), rn = workspaceName(session.active.compare.right);
  const node = (n) => '<li><span class="' + CLS.DOT + ' ' + n.kind + '"></span>' + esc(kindLabel(n.kind) + ' ' + n.name) + '</li>';
  const edge = (e) => '<li>' + esc(e.from + ARROW + e.label + (cardinalityText(e) ? ' ' + cardinalityText(e) : '') + ARROW + e.to) + '</li>';
  const block = (title, items, render) => items.length ? '<h4>' + esc(title) + '</h4><ul>' + items.map(render).join('') + '</ul>' : '';
  return '<div class="' + CLS.COMPARE_MODEL + '">'
    + '<div>' + block(t(MSG.COMPARE_DECLARATIONS_ONLY_IN, ln, d.nodesOnlyLeft.length), d.nodesOnlyLeft, node) + block(t(MSG.COMPARE_LINKS_ONLY_IN, ln, d.edgesOnlyLeft.length), d.edgesOnlyLeft, edge) + '</div>'
    + '<div>' + block(t(MSG.COMPARE_DECLARATIONS_ONLY_IN, rn, d.nodesOnlyRight.length), d.nodesOnlyRight, node) + block(t(MSG.COMPARE_LINKS_ONLY_IN, rn, d.edgesOnlyRight.length), d.edgesOnlyRight, edge) + '</div>'
    + '</div>';
}

/**
 * Side by side, one row per line pair (original line numbers); long identical runs folded; moved blocks in their own colour.
 * One table per side, each scrolling sideways on its own: the rows are one line high on both sides, so they stay aligned.
 */
function textDiffHtml(pair) {
  const { la, lb, ops } = lineDiff(pair);
  if (!ops) return '<p class="' + CLS.META + '">' + esc(t(MSG.COMPARE_TEXT_TOO_LARGE)) + '</p>';
  const row = (rowCls, lines, i, cls, op) => {
    const moved = op && op.moved;
    const note = !moved ? '' : op.movedTo != null ? t(MSG.COMPARE_MOVED_TO, lb[op.movedTo].n) : t(MSG.COMPARE_MOVED_FROM, la[op.movedFrom].n);
    return '<tr class="' + rowCls + '"><td class="' + CLS.LINE_NUMBER + '"' + (note ? ' title="' + esc(note) + '"' : '') + '>' + (i == null ? '' : lines[i].n) + '</td>'
      + '<td class="' + CLS.CODE + (cls ? ' ' + cls : '') + (moved ? ' ' + CLS.MOVED : '') + '">' + esc(i == null ? '' : lines[i].text) + '</td></tr>';
  };
  const keep = isDiffOnly() ? CONTEXT_LINES : FOLD_KEEP, foldAbove = isDiffOnly() ? 2 * CONTEXT_LINES : FOLD_ABOVE;
  let left = '', right = '';
  const equal = (op) => { left += row(CLS.EQUAL, la, op.a); right += row(CLS.EQUAL, lb, op.b); };
  let i = 0;
  while (i < ops.length) {
    if (ops[i].op === OP.EQUAL) {
      let j = i;
      while (j < ops.length && ops[j].op === OP.EQUAL) j++;
      const run = j - i;
      const folded = run > foldAbove;
      const keepEnd = folded ? i + keep : j;
      for (let k = i; k < keepEnd; k++) equal(ops[k]);
      if (folded) {
        const fold = '<tr class="' + CLS.FOLD + '"><td colspan="2"><span>' + esc(plural(run - 2 * keep, MSG.COMPARE_IDENTICAL_LINES_ONE, MSG.COMPARE_IDENTICAL_LINES_OTHER)) + '</span></td></tr>';
        left += fold; right += fold;
        for (let k = j - keep; k < j; k++) equal(ops[k]);
      }
      i = j;
    } else {
      // a change block: deletions on the left, insertions on the right, aligned row by row
      const del = [], ins = [];
      while (i < ops.length && ops[i].op !== OP.EQUAL) { (ops[i].op === OP.DELETE ? del : ins).push(ops[i]); i++; }
      for (let k = 0; k < Math.max(del.length, ins.length); k++) {
        left += k < del.length ? row(CLS.CHANGE, la, del[k].a, CLS.DELETED, del[k]) : row(CLS.CHANGE, la, null);
        right += k < ins.length ? row(CLS.CHANGE, lb, ins[k].b, CLS.INSERTED, ins[k]) : row(CLS.CHANGE, lb, null);
      }
    }
  }
  const side = (rows) => '<div class="' + CLS.DIFF_SIDE + '"><table class="' + CLS.DIFF + '">' + rows + '</table></div>';
  return '<div class="' + CLS.DIFF_SIDES + '">' + side(left) + side(right) + '</div>';
}
