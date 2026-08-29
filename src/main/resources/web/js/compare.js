/**
 * Comparing two workspaces (selected with Ctrl+click on their chips), folder-comparison style:
 * files paired by name and marked identical / different / only on one side, a different pair
 * expandable to its schema and line differences.
 */
import { businessLines } from './business-lines.js';
import { cardinalityText } from './cardinality.js';
import { STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { OP, diffLines, onlyMoves, splitLines } from './diff.js';
import { plural, t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { diffModels } from './schema-diff.js';
import { session } from './state.js';
import { activateTab, newTab, tabsOf, workspaceName } from './tabs.js';

export const COMPARED_WORKSPACES = 2;
/** Equal runs longer than FOLD_ABOVE are folded to one line in the text diff, FOLD_KEEP lines kept on each side; with "differences only", one line of context. */
const FOLD_ABOVE = 6, FOLD_KEEP = 2, CONTEXT_LINES = 1;
const STATUS = { SAME: 'same', DIFFERENT: 'different', MOVED: 'moved', ONLY_LEFT: 'only-left', ONLY_RIGHT: 'only-right' };
const STATUS_TEXT = { [STATUS.SAME]: MSG.COMPARE_SAME, [STATUS.DIFFERENT]: MSG.COMPARE_DIFFERENT, [STATUS.MOVED]: MSG.COMPARE_MOVED, [STATUS.ONLY_LEFT]: MSG.COMPARE_ONLY_IN, [STATUS.ONLY_RIGHT]: MSG.COMPARE_ONLY_IN };
const LINE_BREAK = /\r\n/g;
const ARROW = ' → ';

/** The two options of the view, remembered across sessions: "business lines only" (on by default) and "differences only". */
const OPTIONS = [[ID.COMPARE_BUSINESS_ONLY, STORAGE_KEY.COMPARE_BUSINESS_ONLY, true], [ID.COMPARE_DIFF_ONLY, STORAGE_KEY.COMPARE_DIFF_ONLY, false]];

/** Comments, xs:annotation, blank lines and indentation ignored. */
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

/** Ctrl+click on a chip: toggles the workspace's selection; the oldest selection gives way to a third. */
export function toggleSelection(ws) {
  const sel = session.compareSelection;
  const i = sel.indexOf(ws);
  if (i >= 0) sel.splice(i, 1);
  else { sel.push(ws); if (sel.length > COMPARED_WORKSPACES) sel.shift(); }
}

export const canCompare = () => session.compareSelection.length === COMPARED_WORKSPACES;

/** Opens (or brings to front) the comparison tab of the two selected workspaces; the caller redraws the page. Returns false when two are not selected. */
export function startCompare() {
  if (!canCompare()) return false;
  const [left, right] = session.compareSelection;
  let tab = session.tabs.find(x => x.compare && x.compare.left === left && x.compare.right === right);
  if (!tab) {
    tab = newTab();
    tab.compare = { left, right };
  }
  activateTab(tab);
  return true;
}

/** The loaded tabs of a workspace by file name (the first one when a name appears twice). */
function filesOf(ws) {
  const byName = new Map();
  for (const tab of tabsOf(ws)) if (tab.model && !byName.has(tab.fileName)) byName.set(tab.fileName, tab);
  return byName;
}

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

export function renderCompare() {
  const { left, right } = session.active.compare;
  const ln = workspaceName(left), rn = workspaceName(right);
  pairs = pairFiles(left, right);
  const count = (s) => pairs.filter(p => p.status === s).length;
  $(ID.COMPARE_TITLE).textContent = t(MSG.COMPARE_TITLE, ln, rn);
  $(ID.COMPARE_SUMMARY).textContent = t(MSG.COMPARE_SUMMARY, pairs.length, count(STATUS.SAME), count(STATUS.DIFFERENT), count(STATUS.MOVED), count(STATUS.ONLY_LEFT), ln, count(STATUS.ONLY_RIGHT), rn);
  let html = '<thead><tr><th>' + esc(t(MSG.COMPARE_FILE)) + '</th><th>' + esc(ln) + '</th><th>' + esc(t(MSG.COMPARE_STATUS)) + '</th><th>' + esc(rn) + '</th></tr></thead><tbody>';
  pairs.forEach((p, i) => {
    if (isDiffOnly() && p.status === STATUS.SAME) return;
    const side = p.status === STATUS.ONLY_LEFT ? ln : p.status === STATUS.ONLY_RIGHT ? rn : '';
    html += '<tr class="' + CLS.COMPARE_ROW + ' ' + p.status + (p.status === STATUS.DIFFERENT || p.status === STATUS.MOVED ? ' ' + CLS.EXPANDABLE : '') + '"' + dataAttr(DATA.ROW_INDEX, i) + '>'
      + '<td class="' + CLS.COMPARE_NAME + '">' + esc(p.name) + '</td>'
      + '<td class="' + CLS.COMPARE_PATH + '" title="' + esc(p.left ? p.left.path || '' : '') + '">' + esc(p.left ? p.left.path || p.left.fileName : '') + '</td>'
      + '<td class="' + CLS.COMPARE_STATUS + '">' + esc(t(STATUS_TEXT[p.status], side)) + '</td>'
      + '<td class="' + CLS.COMPARE_PATH + '" title="' + esc(p.right ? p.right.path || '' : '') + '">' + esc(p.right ? p.right.path || p.right.fileName : '') + '</td></tr>';
  });
  $(ID.COMPARE_TABLE).innerHTML = html + '</tbody>';
}

/** Click on a row: shows / hides the differences of that pair under it. */
export function toggleDetail(row) {
  const pair = pairs[+row.dataset[DATA.ROW_INDEX]];
  if (!pair || (pair.status !== STATUS.DIFFERENT && pair.status !== STATUS.MOVED)) return;
  const next = row.nextElementSibling;
  if (next && next.classList.contains(CLS.COMPARE_DETAIL)) { next.remove(); row.classList.remove(CLS.OPEN); return; }
  const detail = document.createElement('tr');
  detail.className = CLS.COMPARE_DETAIL;
  detail.innerHTML = '<td colspan="4">' + schemaDiffHtml(pair) + textDiffHtml(pair) + '</td>';
  row.after(detail);
  row.classList.add(CLS.OPEN);
}

function schemaDiffHtml(pair) {
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

/** Side by side, one row per line pair (original line numbers); long identical runs folded; moved blocks in their own colour. */
function textDiffHtml(pair) {
  const { la, lb, ops } = lineDiff(pair);
  if (!ops) return '<p class="' + CLS.META + '">' + esc(t(MSG.COMPARE_TEXT_TOO_LARGE)) + '</p>';
  const cell = (lines, i, cls, op) => {
    const moved = op && op.moved;
    const note = !moved ? '' : op.movedTo != null ? t(MSG.COMPARE_MOVED_TO, lb[op.movedTo].n) : t(MSG.COMPARE_MOVED_FROM, la[op.movedFrom].n);
    return '<td class="' + CLS.LINE_NUMBER + '"' + (note ? ' title="' + esc(note) + '"' : '') + '>' + (i == null ? '' : lines[i].n) + '</td>'
      + '<td class="' + CLS.CODE + (cls ? ' ' + cls : '') + (moved ? ' ' + CLS.MOVED : '') + '">' + esc(i == null ? '' : lines[i].text) + '</td>';
  };
  const keep = isDiffOnly() ? CONTEXT_LINES : FOLD_KEEP, foldAbove = isDiffOnly() ? 2 * CONTEXT_LINES : FOLD_ABOVE;
  let html = '<table class="' + CLS.DIFF + '">';
  let i = 0;
  while (i < ops.length) {
    if (ops[i].op === OP.EQUAL) {
      let j = i;
      while (j < ops.length && ops[j].op === OP.EQUAL) j++;
      const run = j - i;
      const folded = run > foldAbove;
      const keepEnd = folded ? i + keep : j;
      for (let k = i; k < keepEnd; k++) html += '<tr class="' + CLS.EQUAL + '">' + cell(la, ops[k].a) + cell(lb, ops[k].b) + '</tr>';
      if (folded) {
        html += '<tr class="' + CLS.FOLD + '"><td colspan="4">' + esc(plural(run - 2 * keep, MSG.COMPARE_IDENTICAL_LINES_ONE, MSG.COMPARE_IDENTICAL_LINES_OTHER)) + '</td></tr>';
        for (let k = j - keep; k < j; k++) html += '<tr class="' + CLS.EQUAL + '">' + cell(la, ops[k].a) + cell(lb, ops[k].b) + '</tr>';
      }
      i = j;
    } else {
      // a change block: deletions on the left, insertions on the right, aligned row by row
      const del = [], ins = [];
      while (i < ops.length && ops[i].op !== OP.EQUAL) { (ops[i].op === OP.DELETE ? del : ins).push(ops[i]); i++; }
      for (let k = 0; k < Math.max(del.length, ins.length); k++) {
        html += '<tr class="' + CLS.CHANGE + '">'
          + (k < del.length ? cell(la, del[k].a, CLS.DELETED, del[k]) : cell(la, null))
          + (k < ins.length ? cell(lb, ins[k].b, CLS.INSERTED, ins[k]) : cell(lb, null)) + '</tr>';
      }
    }
  }
  return html + '</table>';
}
