/**
 * Comparing two workspaces, folder-comparison style: Ctrl+click two workspace chips to select
 * them, then Compare. Files are paired by name and marked identical / different / only on one
 * side; a different pair expands to the schema differences (declarations and links on one
 * side only) and a side-by-side line diff.
 */
import { cardinalityText } from './cardinality.js';
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { OP, diffLines, splitLines } from './diff.js';
import { plural, t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { diffModels } from './schema-diff.js';
import { session } from './state.js';
import { tabsOf, workspaceName } from './tabs.js';

export const COMPARED_WORKSPACES = 2;
/** Equal runs longer than this are folded to one line in the text diff. */
const FOLD_ABOVE = 6, FOLD_KEEP = 2;
const STATUS = { SAME: 'same', DIFFERENT: 'different', ONLY_LEFT: 'only-left', ONLY_RIGHT: 'only-right' };
const STATUS_TEXT = { [STATUS.SAME]: MSG.COMPARE_SAME, [STATUS.DIFFERENT]: MSG.COMPARE_DIFFERENT, [STATUS.ONLY_LEFT]: MSG.COMPARE_ONLY_IN, [STATUS.ONLY_RIGHT]: MSG.COMPARE_ONLY_IN };
const LINE_BREAK = /\r\n/g;
const ARROW = ' → ';

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

/** Starts comparing the two selected workspaces (the caller redraws the page). Returns false when two are not selected. */
export function startCompare() {
  if (!canCompare()) return false;
  const [left, right] = session.compareSelection;
  session.compare = { left, right };
  return true;
}

export function closeCompare() {
  session.compare = null;
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
    const status = !a ? STATUS.ONLY_RIGHT : !b ? STATUS.ONLY_LEFT : canonical(a.text) === canonical(b.text) ? STATUS.SAME : STATUS.DIFFERENT;
    return { name, left: a, right: b, status };
  });
}

export function renderCompare() {
  const { left, right } = session.compare;
  const ln = workspaceName(left), rn = workspaceName(right);
  pairs = pairFiles(left, right);
  const count = (s) => pairs.filter(p => p.status === s).length;
  $(ID.COMPARE_TITLE).textContent = t(MSG.COMPARE_TITLE, ln, rn);
  $(ID.COMPARE_SUMMARY).textContent = t(MSG.COMPARE_SUMMARY, pairs.length, count(STATUS.SAME), count(STATUS.DIFFERENT), count(STATUS.ONLY_LEFT), ln, count(STATUS.ONLY_RIGHT), rn);
  let html = '<thead><tr><th>' + esc(t(MSG.COMPARE_FILE)) + '</th><th>' + esc(ln) + '</th><th>' + esc(t(MSG.COMPARE_STATUS)) + '</th><th>' + esc(rn) + '</th></tr></thead><tbody>';
  pairs.forEach((p, i) => {
    const side = p.status === STATUS.ONLY_LEFT ? ln : p.status === STATUS.ONLY_RIGHT ? rn : '';
    html += '<tr class="' + CLS.COMPARE_ROW + ' ' + p.status + (p.status === STATUS.DIFFERENT ? ' ' + CLS.EXPANDABLE : '') + '"' + dataAttr(DATA.ROW_INDEX, i) + '>'
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
  if (!pair || pair.status !== STATUS.DIFFERENT) return;
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
  const ln = workspaceName(session.compare.left), rn = workspaceName(session.compare.right);
  const node = (n) => '<li><span class="' + CLS.DOT + ' ' + n.kind + '"></span>' + esc(kindLabel(n.kind) + ' ' + n.name) + '</li>';
  const edge = (e) => '<li>' + esc(e.from + ARROW + e.label + (cardinalityText(e) ? ' ' + cardinalityText(e) : '') + ARROW + e.to) + '</li>';
  const block = (title, items, render) => items.length ? '<h4>' + esc(title) + '</h4><ul>' + items.map(render).join('') + '</ul>' : '';
  return '<div class="' + CLS.COMPARE_MODEL + '">'
    + '<div>' + block(t(MSG.COMPARE_DECLARATIONS_ONLY_IN, ln, d.nodesOnlyLeft.length), d.nodesOnlyLeft, node) + block(t(MSG.COMPARE_LINKS_ONLY_IN, ln, d.edgesOnlyLeft.length), d.edgesOnlyLeft, edge) + '</div>'
    + '<div>' + block(t(MSG.COMPARE_DECLARATIONS_ONLY_IN, rn, d.nodesOnlyRight.length), d.nodesOnlyRight, node) + block(t(MSG.COMPARE_LINKS_ONLY_IN, rn, d.edgesOnlyRight.length), d.edgesOnlyRight, edge) + '</div>'
    + '</div>';
}

/** Side by side, one row per line pair; long identical runs folded. */
function textDiffHtml(pair) {
  const a = splitLines(canonical(pair.left.text)), b = splitLines(canonical(pair.right.text));
  const ops = diffLines(a, b);
  if (!ops) return '<p class="' + CLS.META + '">' + esc(t(MSG.COMPARE_TEXT_TOO_LARGE)) + '</p>';
  const cell = (n, text, cls) => '<td class="' + CLS.LINE_NUMBER + '">' + (n == null ? '' : n + 1) + '</td><td class="' + CLS.CODE + (cls ? ' ' + cls : '') + '">' + esc(text == null ? '' : text) + '</td>';
  let html = '<table class="' + CLS.DIFF + '">';
  let i = 0;
  while (i < ops.length) {
    if (ops[i].op === OP.EQUAL) {
      let j = i;
      while (j < ops.length && ops[j].op === OP.EQUAL) j++;
      const run = j - i;
      const keepEnd = run > FOLD_ABOVE ? i + FOLD_KEEP : j;
      for (let k = i; k < keepEnd; k++) html += '<tr class="' + CLS.EQUAL + '">' + cell(ops[k].a, a[ops[k].a]) + cell(ops[k].b, b[ops[k].b]) + '</tr>';
      if (run > FOLD_ABOVE) {
        html += '<tr class="' + CLS.FOLD + '"><td colspan="4">' + esc(plural(run - 2 * FOLD_KEEP, MSG.COMPARE_IDENTICAL_LINES_ONE, MSG.COMPARE_IDENTICAL_LINES_OTHER)) + '</td></tr>';
        for (let k = j - FOLD_KEEP; k < j; k++) html += '<tr class="' + CLS.EQUAL + '">' + cell(ops[k].a, a[ops[k].a]) + cell(ops[k].b, b[ops[k].b]) + '</tr>';
      }
      i = j;
    } else {
      // a change block: deletions on the left, insertions on the right, aligned row by row
      const del = [], ins = [];
      while (i < ops.length && ops[i].op !== OP.EQUAL) { (ops[i].op === OP.DELETE ? del : ins).push(ops[i]); i++; }
      for (let k = 0; k < Math.max(del.length, ins.length); k++) {
        html += '<tr class="' + CLS.CHANGE + '">'
          + (k < del.length ? cell(del[k].a, a[del[k].a], CLS.DELETED) : cell(null, null))
          + (k < ins.length ? cell(ins[k].b, b[ins[k].b], CLS.INSERTED) : cell(null, null)) + '</tr>';
      }
    }
  }
  return html + '</table>';
}
