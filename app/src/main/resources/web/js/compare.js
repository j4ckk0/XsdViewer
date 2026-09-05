/**
 * The Files section of the comparison: two workspaces (selected with Ctrl+click on their chips, {@code compare-selection.js})
 * compared folder-style, their files paired by name and marked identical / different / only on one
 * side, a different pair expandable to its schema and line differences.
 */
import { cardinalityText } from './cardinality.js';
import { OP, PAIR_STATUS, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE } from './constants.js';
import { compareSchemas, compareTexts, compareWorkspaces } from './api.js';
import { $, CLS, DATA, ID, dataAttr, esc, legendHtml } from './dom.js';
import { plural, t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { ensureModel } from './file-tabs.js';
import { workspaceName } from './tabs.js';

/** Equal runs longer than FOLD_ABOVE are folded to one line in the text diff, FOLD_KEEP lines kept on each side; with "differences only", one line of context. */
const FOLD_ABOVE = 6, FOLD_KEEP = 2, CONTEXT_LINES = 1;
/** How the text diff of two files folds, as the *differences only* option asks. */
const folding = () => (isDiffOnly() ? { keep: CONTEXT_LINES, above: 2 * CONTEXT_LINES } : { keep: FOLD_KEEP, above: FOLD_ABOVE });
const STATUS = PAIR_STATUS;
const STATUS_TEXT = { [STATUS.SAME]: MSG.COMPARE_SAME, [STATUS.DIFFERENT]: MSG.COMPARE_DIFFERENT, [STATUS.MOVED]: MSG.COMPARE_MOVED, [STATUS.ONLY_LEFT]: MSG.COMPARE_ONLY_IN, [STATUS.ONLY_RIGHT]: MSG.COMPARE_ONLY_IN };
const ARROW = ' → ';

/** The two options of the view, remembered across sessions: "business lines only" (on by default) and "differences only". */
const OPTIONS = [[ID.COMPARE_BUSINESS_ONLY, STORAGE_KEY.COMPARE_BUSINESS_ONLY, true], [ID.COMPARE_DIFF_ONLY, STORAGE_KEY.COMPARE_DIFF_ONLY, false]];

/** Comments, xs:annotation, the wiring tags (XML declaration, xs:schema, xs:import, xs:include), blank lines and indentation ignored. */
const isBusinessOnly = () => $(ID.COMPARE_BUSINESS_ONLY).checked;
/** Identical files hidden, identical lines reduced to one line of context. */
const isDiffOnly = () => $(ID.COMPARE_DIFF_ONLY).checked;

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

/** The pairs of the comparison being shown, by row index. */
let pairs = [];

const isExpandable = (pair) => pair.status === STATUS.DIFFERENT || pair.status === STATUS.MOVED;
/** Every file a workspace knows (open in a tab or only listed) by file name; the first one when a name appears twice. */
function filesOf(ws) {
  const byName = new Map();
  for (const f of ws.files) if (!byName.has(f.name)) byName.set(f.name, f);
  return byName;
}

/** Where a file is, for the tooltip of its cell: its path, else where it sits in the folder it came from, else its name alone. */
const shownPath = (f) => f.path || f.rel || f.name;

const canonical = (text) => text.replace(LINE_BREAK, '\n');

/** [{name, left, right, status}] for every file name of either workspace, sorted: the server pairs the names and says the status of each pair. */
async function pairFiles(left, right) {
  const l = filesOf(left), r = filesOf(right);
  const files = (byName) => [...byName.values()].map(f => ({ name: f.name, text: f.text }));
  const { pairs: paired } = await compareWorkspaces(files(l), files(r), isBusinessOnly());
  return paired.map(p => ({ name: p.name, left: l.get(p.name) || null, right: r.get(p.name) || null, status: p.status, diff: null, schemas: null }));
}

/** Only the drawing of the last call is written: the server is asked first. */
let drawing = 0;

/** Draws the Files section: every pair of the two selected workspaces, or what to do while fewer than two are selected. */
export async function renderCompare() {
  const [left, right] = session.compareSelection;
  $(ID.COMPARE_EMPTY).classList.toggle(CLS.HIDDEN, !!(left && right));
  $(ID.COMPARE_BODY).classList.toggle(CLS.HIDDEN, !(left && right));
  $(ID.COMPARE_HEADER).classList.toggle(CLS.HIDDEN, !(left && right));
  if (!(left && right)) { $(ID.COMPARE_EMPTY).textContent = t(MSG.COMPARE_SELECT_TWO); return; }
  const ln = workspaceName(left), rn = workspaceName(right);
  const token = ++drawing;
  const paired = await pairFiles(left, right);
  if (token !== drawing) return;   // the selection changed while the server compared
  pairs = paired;
  const count = (s) => pairs.filter(p => p.status === s).length;
  const side = (p) => p.status === STATUS.ONLY_LEFT ? ln : p.status === STATUS.ONLY_RIGHT ? rn : '';
  $(ID.COMPARE_TITLE).textContent = t(MSG.COMPARE_TITLE, ln, rn);
  $(ID.COMPARE_SUMMARY).textContent = t(MSG.COMPARE_SUMMARY, pairs.length, count(STATUS.SAME), count(STATUS.DIFFERENT),
    count(STATUS.MOVED), count(STATUS.ONLY_LEFT), ln, count(STATUS.ONLY_RIGHT), rn);
  // the colours of the line comparison: lines only on the left (red), only on the right (green), moved (blue)
  $(ID.COMPARE_LEGEND).innerHTML = legendHtml([[CLS.DELETED, t(MSG.COMPARE_ONLY_IN, ln)],
    [CLS.INSERTED, t(MSG.COMPARE_ONLY_IN, rn)], [CLS.MOVED, t(MSG.COMPARE_LEGEND_MOVED)]]);
  let html = '<thead><tr><th>' + esc(t(MSG.COMPARE_FILE)) + '</th><th>' + esc(ln) + '</th><th>' + esc(t(MSG.COMPARE_STATUS)) + '</th><th>' + esc(rn) + '</th></tr></thead><tbody>';
  pairs.forEach((p, i) => {
    if (isDiffOnly() && p.status === STATUS.SAME) return;
    html += '<tr class="' + CLS.COMPARE_ROW + ' ' + p.status + (isExpandable(p) ? ' ' + CLS.EXPANDABLE : '') + '"' + dataAttr(DATA.ROW_INDEX, i) + '>'
      + '<td class="' + CLS.COMPARE_NAME + '">' + esc(p.name) + '</td>'
      // the side columns say whether the workspace holds the file, by its name: the path would be long, and is the tooltip
      + '<td class="' + CLS.COMPARE_PATH + '" title="' + esc(p.left ? shownPath(p.left) : '') + '">' + esc(p.left ? p.left.name : '') + '</td>'
      + '<td class="' + CLS.COMPARE_STATUS + '">' + esc(t(STATUS_TEXT[p.status], side(p))) + '</td>'
      + '<td class="' + CLS.COMPARE_PATH + '" title="' + esc(p.right ? shownPath(p.right) : '') + '">' + esc(p.right ? p.right.name : '') + '</td></tr>';
  });
  $(ID.COMPARE_TABLE).innerHTML = html + '</tbody>';
}

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
  // the server's two comparisons of the pair, asked once
  if (!pair.schemas) pair.schemas = await compareSchemas(pair.left.text, pair.right.text);
  if (!pair.diff) pair.diff = await compareTexts(pair.left.text, pair.right.text, { businessOnly: isBusinessOnly() });
  if (!row.isConnected) return;   // the table was redrawn meanwhile
  const detail = document.createElement('tr');
  detail.className = CLS.COMPARE_DETAIL;
  detail.innerHTML = '<td colspan="4">' + schemaDiffHtml(pair.schemas) + textDiffHtml(pair.diff, folding()) + '</td>';
  row.after(detail);
}

/** Opens the differences of every expandable row, or closes them all. */
export async function setAllDetails(open) {
  for (const row of [...$(ID.COMPARE_TABLE).querySelectorAll('.' + CLS.EXPANDABLE)]) {
    const isOpen = row.classList.contains(CLS.OPEN);
    if (open !== isOpen) await toggleDetail(row);
  }
}

/** What the two schemas declare and link that the other does not, as the server answered (POST /api/compare/schemas). */
function schemaDiffHtml(d) {
  if (!d.schemas) return '<p class="' + CLS.META + '">' + esc(t(MSG.FILES_NOT_A_SCHEMA)) + '</p>';
  if (d.same) return '<p class="' + CLS.META + '">' + esc(t(MSG.COMPARE_SAME_MODEL)) + '</p>';
  const [left, right] = session.compareSelection;
  const ln = workspaceName(left), rn = workspaceName(right);
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
 *
 * @param diff     {la, lb, ops}: the lines of each side ({n, text}) and the edit script turning one into the other
 * @param folding  {keep, above}: identical runs longer than {@code above} fold to one line, {@code keep} lines left on each
 *                 side — what two whole files need; null shows every line, as a block of a few lines wants
 */
export function textDiffHtml({ la, lb, ops }, folding) {
  if (!ops) return '<p class="' + CLS.META + '">' + esc(t(MSG.COMPARE_TEXT_TOO_LARGE)) + '</p>';
  const row = (rowCls, lines, i, cls, op) => {
    const moved = op && op.moved;
    const note = !moved ? '' : op.movedTo != null ? t(MSG.COMPARE_MOVED_TO, lb[op.movedTo].n) : t(MSG.COMPARE_MOVED_FROM, la[op.movedFrom].n);
    return '<tr class="' + rowCls + '"><td class="' + CLS.LINE_NUMBER + '"' + (note ? ' title="' + esc(note) + '"' : '') + '>' + (i == null ? '' : lines[i].n) + '</td>'
      + '<td class="' + CLS.CODE + (cls ? ' ' + cls : '') + (moved ? ' ' + CLS.MOVED : '') + '">' + esc(i == null ? '' : lines[i].text) + '</td></tr>';
  };
  const keep = folding ? folding.keep : 0, foldAbove = folding ? folding.above : Infinity;
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
