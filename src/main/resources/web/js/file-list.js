/**
 * The Files panel of the sidebar: every file the active workspace knows (open in a tab or not)
 * as a tree by folder (paths relative to their common root), each file unfoldable to its global
 * objects; the shown file is highlighted. Clicking a file or an object shows its tab, opening it
 * when needed (events.js).
 */
import { KINDS, NODE_KIND, PATH_SEPARATOR, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { fileKeys, tabOfFile } from './workspace-files.js';

const SEPARATORS = /[\\/]/;
const COLLAPSE_GLYPH = '▾', EXPAND_GLYPH = '▸';
/** Folders and files folded by the user (their path in the tree / their key); a session-long memory. */
const foldedDirs = new Set();
const unfoldedFiles = new Set();
const KIND_ORDER = new Map(KINDS.map((k, i) => [k, i]));
const declared = (n) => n.kind !== NODE_KIND.BUILTIN && n.kind !== NODE_KIND.EXTERNAL;

/** The rows to list: the workspace's files, plus its tabs showing no file (empty tabs). */
function rows() {
  const ws = session.active.workspace;
  const out = ws.files.map(entry => ({ entry, tab: tabOfFile(entry), name: entry.name, path: entry.path || entry.rel || entry.name }));
  for (const tab of session.tabs) {
    if (tab.workspace === ws && !tab.file) out.push({ entry: null, tab, name: tab.fileName || t(MSG.TAB_UNTITLED), path: tab.fileName || t(MSG.TAB_UNTITLED) });
  }
  return out;
}

/** The directories every listed path shares, dropped from the display. */
function commonPrefix(paths) {
  const withDirs = paths.filter(p => p.length > 1);
  if (!withDirs.length) return 0;
  let n = 0;
  while (withDirs.every(p => p.length - 1 > n && p[n] === withDirs[0][n])) n++;
  return n;
}

/** Builds the tree {dirs: Map(name -> node), files: [row]} of the rows. */
function tree(list) {
  const paths = list.map(r => r.path.split(SEPARATORS).filter(Boolean));
  const skip = commonPrefix(paths);
  const root = { dirs: new Map(), files: [] };
  list.forEach((r, i) => {
    const parts = paths[i].length > 1 ? paths[i].slice(skip) : paths[i];
    let node = root;
    for (const dir of parts.slice(0, -1)) {
      if (!node.dirs.has(dir)) node.dirs.set(dir, { dirs: new Map(), files: [] });
      node = node.dirs.get(dir);
    }
    node.files.push(Object.assign({ shown: parts[parts.length - 1] }, r));
  });
  return root;
}

const byKindThenName = (a, b) => (KIND_ORDER.get(a.kind) - KIND_ORDER.get(b.kind)) || a.name.localeCompare(b.name);

function objectsHtml(entry) {
  if (!entry.model) return entry.failed ? '<div class="' + CLS.ITEM + ' ' + CLS.EMPTY + '">' + esc(t(MSG.FILES_NOT_A_SCHEMA)) + '</div>' : '';
  return entry.model.nodes.filter(declared).sort(byKindThenName).map(n =>
    '<div class="' + CLS.ITEM + ' ' + CLS.OBJECT + '"' + dataAttr(DATA.ID, n.id) + ' title="' + esc(n.id) + '">'
    + '<span class="' + CLS.DOT + ' ' + n.kind + '"></span><span>' + esc(n.name) + '</span></div>').join('');
}

function nodeHtml(node, dirPath) {
  let html = '';
  for (const [name, child] of [...node.dirs].sort((a, b) => a[0].localeCompare(b[0]))) {
    const path = dirPath + PATH_SEPARATOR + name;
    html += '<div class="' + CLS.GROUP_HEADER + ' ' + CLS.DIR + (foldedDirs.has(path) ? ' ' + CLS.COLLAPSED : '') + '"' + dataAttr(DATA.DIR, path) + '>'
      + '<span>' + esc(name) + '</span></div><div class="' + CLS.GROUP_ITEMS + '">' + nodeHtml(child, path) + '</div>';
  }
  for (const f of node.files.sort((a, b) => a.shown.localeCompare(b.shown))) {
    const active = f.tab === session.active;
    const key = f.entry ? fileKeys(f.entry)[0] : '';
    const unfolded = f.entry && (unfoldedFiles.has(key) || (active && !unfoldedFiles.has(key) && !foldedDirs.has(key)));
    html += '<div class="' + CLS.ITEM + ' ' + CLS.FILE + (active ? ' ' + CLS.SELECTED : '') + (f.tab ? ' ' + CLS.OPEN : '') + '"'
      + (f.entry ? dataAttr(DATA.FILE, session.active.workspace.files.indexOf(f.entry)) : dataAttr(DATA.TAB_INDEX, session.tabs.indexOf(f.tab)))
      + ' title="' + esc(f.path) + '">'
      + (f.entry ? '<span class="' + CLS.EXPANDER + '">' + (unfolded ? COLLAPSE_GLYPH : EXPAND_GLYPH) + '</span>' : '')
      + '<span class="' + (f.entry ? '' : CLS.EMPTY) + '">' + esc(f.shown) + '</span></div>';
    if (f.entry && unfolded) html += '<div class="' + CLS.GROUP_ITEMS + ' ' + CLS.OBJECTS + '">' + objectsHtml(f.entry) + '</div>';
  }
  return html;
}

export function renderFileList() {
  const list = rows();
  $(ID.FILES_COUNT).textContent = list.length;
  $(ID.FILES_CONTENT).innerHTML = nodeHtml(tree(list), '');
}

/**
 * A click in the panel. A folder header or a file's expander folds / unfolds (handled here, null
 * returned); otherwise {entry} for a file, {entry, id} for one of its objects, {tab} for an empty tab.
 */
export function fileListClick(target) {
  const header = target.closest('.' + CLS.DIR);
  if (header) {
    const path = header.dataset[DATA.DIR];
    if (foldedDirs.has(path)) foldedDirs.delete(path); else foldedDirs.add(path);
    header.classList.toggle(CLS.COLLAPSED);
    return null;
  }
  const item = target.closest('.' + CLS.ITEM);
  if (!item) return null;
  const files = session.active.workspace.files;
  if (item.classList.contains(CLS.OBJECT)) {
    const fileRow = item.parentElement.previousElementSibling;
    return { entry: files[+fileRow.dataset[DATA.FILE]], id: item.dataset[DATA.ID] };
  }
  if (item.dataset[DATA.FILE] == null) return { tab: session.tabs[+item.dataset[DATA.TAB_INDEX]] };
  const entry = files[+item.dataset[DATA.FILE]];
  if (target.closest('.' + CLS.EXPANDER)) {
    const key = fileKeys(entry)[0];
    const unfolded = item.nextElementSibling && item.nextElementSibling.classList.contains(CLS.OBJECTS);
    if (unfolded) { unfoldedFiles.delete(key); foldedDirs.add(key); } else { unfoldedFiles.add(key); foldedDirs.delete(key); }
    renderFileList();
    return null;
  }
  return { entry };
}

export function setFilesCollapsed(collapsed) {
  $(ID.FILES).classList.toggle(CLS.COLLAPSED, collapsed);
  const toggle = $(ID.FILES_TOGGLE);
  toggle.textContent = collapsed ? EXPAND_GLYPH : COLLAPSE_GLYPH;
  toggle.title = t(collapsed ? MSG.FILES_EXPAND : MSG.FILES_COLLAPSE);
  try { localStorage.setItem(STORAGE_KEY.FILES_COLLAPSED, collapsed ? STORAGE_TRUE : STORAGE_FALSE); } catch (e) { /* storage unavailable */ }
}

export const isFilesCollapsed = () => $(ID.FILES).classList.contains(CLS.COLLAPSED);

export function toggleFiles() {
  setFilesCollapsed(!isFilesCollapsed());
}

/** Restores the folded state remembered in the browser; unfolded by default. */
export function initFiles() {
  let collapsed = false;
  try { collapsed = localStorage.getItem(STORAGE_KEY.FILES_COLLAPSED) === STORAGE_TRUE; } catch (e) { /* storage unavailable */ }
  setFilesCollapsed(collapsed);
}
