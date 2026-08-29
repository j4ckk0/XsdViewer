/**
 * The Files panel of the sidebar: the files of the active workspace as a tree by folder (paths
 * relative to their common root), the active file highlighted; clicking one shows its tab.
 */
import { PATH_SEPARATOR, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

const SEPARATORS = /[\\/]/;
const COLLAPSE_GLYPH = '▾', EXPAND_GLYPH = '▸';
/** Folders folded by the user (their path in the tree); a session-long memory. */
const foldedDirs = new Set();

/** The path a tab is listed under: on disk, in its folder, or just its name. */
const pathOf = (tab) => (tab.path || tab.rel || tab.fileName || t(MSG.TAB_UNTITLED)).split(SEPARATORS).filter(Boolean);

/** The directories every listed path shares, dropped from the display. */
function commonPrefix(paths) {
  const withDirs = paths.filter(p => p.length > 1);
  if (withDirs.length < 1) return 0;
  let n = 0;
  while (withDirs.every(p => p.length - 1 > n && p[n] === withDirs[0][n])) n++;
  return n;
}

/** Builds the tree {dirs: Map(name -> node), files: [{name, tab}]} of the active workspace's tabs. */
function tree() {
  const tabs = session.tabs.filter(tab => tab.workspace === session.active.workspace);
  const paths = tabs.map(pathOf);
  const skip = commonPrefix(paths);
  const root = { dirs: new Map(), files: [] };
  tabs.forEach((tab, i) => {
    const parts = paths[i].length > 1 ? paths[i].slice(skip) : paths[i];
    let node = root;
    for (const dir of parts.slice(0, -1)) {
      if (!node.dirs.has(dir)) node.dirs.set(dir, { dirs: new Map(), files: [] });
      node = node.dirs.get(dir);
    }
    node.files.push({ name: parts[parts.length - 1], tab });
  });
  return root;
}

function nodeHtml(node, dirPath) {
  let html = '';
  for (const [name, child] of [...node.dirs].sort((a, b) => a[0].localeCompare(b[0]))) {
    const path = dirPath + PATH_SEPARATOR + name;
    html += '<div class="' + CLS.GROUP_HEADER + ' ' + CLS.DIR + (foldedDirs.has(path) ? ' ' + CLS.COLLAPSED : '') + '"' + dataAttr(DATA.DIR, path) + '>'
      + '<span>' + esc(name) + '</span></div><div class="' + CLS.GROUP_ITEMS + '">' + nodeHtml(child, path) + '</div>';
  }
  for (const f of node.files.sort((a, b) => a.name.localeCompare(b.name))) {
    html += '<div class="' + CLS.ITEM + (f.tab === session.active ? ' ' + CLS.SELECTED : '') + '"'
      + dataAttr(DATA.TAB_INDEX, session.tabs.indexOf(f.tab)) + ' title="' + esc(f.tab.path || f.tab.rel || f.name) + '">'
      + '<span class="' + CLS.FILE + (f.tab.model ? '' : ' ' + CLS.EMPTY) + '">' + esc(f.name) + '</span></div>';
  }
  return html;
}

export function renderFileList() {
  const root = tree();
  $(ID.FILES_COUNT).textContent = session.tabs.filter(tab => tab.workspace === session.active.workspace).length;
  $(ID.FILES_CONTENT).innerHTML = nodeHtml(root, '');
}

/** Click in the panel: a folder header folds / unfolds; a file is the caller's to activate (returns its tab, or null). */
export function fileListClick(target) {
  const header = target.closest('.' + CLS.DIR);
  if (header) {
    const path = header.dataset[DATA.DIR];
    if (foldedDirs.has(path)) foldedDirs.delete(path); else foldedDirs.add(path);
    header.classList.toggle(CLS.COLLAPSED);
    return null;
  }
  const item = target.closest('.' + CLS.ITEM);
  return item ? session.tabs[+item.dataset[DATA.TAB_INDEX]] : null;
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
