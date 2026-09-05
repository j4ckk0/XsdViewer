/**
 * The Files panel: every file the active workspace knows, as a tree by folder, each unfoldable to
 * its objects; a click shows the file's tab (events.js opens it when needed). While the search box
 * holds a text, only the objects whose name contains it are listed, in the files holding one (unfolded),
 * and a last row counts the files still being parsed, whose objects are not searchable yet.
 */
import { KINDS, NODE_KIND, PATH_SEPARATOR, STORAGE_KEY, TEXT } from './constants.js';
import { $, dataAttr, esc, selector } from './dom.js';
import { CLS, DATA, ID } from './dom-names.js';
import { foldable } from './foldable.js';
import { plural, t } from './i18n.js';
import { MSG } from './message-keys.js';
import { matchedBy, matches } from './search.js';
import { session } from './state.js';
import { fileKeys, tabOfFile } from './workspace-files.js';

const SEPARATORS = /[\\/]/;
const COLLAPSE_GLYPH = '▾', EXPAND_GLYPH = '▸';
/** Folders folded by the user (their path in the tree), and the files the user unfolded / folded (key -> true / false); a session-long memory. */
const foldedDirs = new Set();
const fileUnfolded = new Map();
/** What "expand all" / "collapse all" decided for the files not folded / unfolded one by one since; null = the shown file only. */
let allFilesUnfolded = null;
/** The workspace files beneath each folder of the last drawn tree, by folder path (for "open as workspace"). */
const dirEntries = new Map();

/** A file shows its objects when the user said so, else when it is the one shown. */
const showsObjects = (entry, active) => (fileUnfolded.has(fileKeys(entry)[0]) ? fileUnfolded.get(fileKeys(entry)[0]) : allFilesUnfolded ?? active);
const KIND_ORDER = new Map(KINDS.map((k, i) => [k, i]));
const declared = (n) => n.kind !== NODE_KIND.BUILTIN && n.kind !== NODE_KIND.EXTERNAL;

/** The rows to list: the workspace's files, plus its tabs showing no file (empty tabs). */
function rows() {
  const ws = session.active.workspace;
  // listed by their path in the opened folder when they came from one (the path on disk, learnt later, would scatter them)
  const out = ws.files.map(entry => ({ entry, tab: tabOfFile(entry), name: entry.name, path: entry.rel || entry.path || entry.name }));
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
  const root = { dirs: new Map(), files: [], entries: [] };
  list.forEach((r, i) => {
    const parts = paths[i].length > 1 ? paths[i].slice(skip) : paths[i];
    let node = root;
    for (const dir of parts.slice(0, -1)) {
      if (!node.dirs.has(dir)) node.dirs.set(dir, { dirs: new Map(), files: [], entries: [] });
      node = node.dirs.get(dir);
      if (r.entry) node.entries.push(r.entry);
    }
    node.files.push(Object.assign({ shown: parts[parts.length - 1] }, r));
  });
  return root;
}

/** The file rows the last drawing wrote: while a search runs, how many files answer it. */
let shownFiles = 0;

const byKindThenName = (a, b) => (KIND_ORDER.get(a.kind) - KIND_ORDER.get(b.kind)) || a.name.localeCompare(b.name);

/** The objects of a file to list: its declared ones — only those whose name contains {@code filter} when there is one —, by kind then name. */
const listedObjects = (entry, filter) => entry.model.nodes.filter(n => declared(n) && matches(n, filter)).sort(byKindThenName);

function objectsHtml(entry, filter, active) {
  if (!entry.model) return entry.failed && !filter ? '<div class="' + CLS.ITEM + ' ' + CLS.EMPTY + '">' + esc(t(MSG.FILES_NOT_A_SCHEMA)) + '</div>' : '';
  return listedObjects(entry, filter).map(n => {
    const why = matchedBy(n, filter);
    // the object being read is marked here as it is in the object list, but only in the file it is read from
    const selected = active && n.id === session.active.selected ? ' ' + CLS.SELECTED : '';
    return '<div class="' + CLS.ITEM + ' ' + CLS.OBJECT + selected + '"' + dataAttr(DATA.ID, n.id) + ' title="' + esc(n.id) + '">'
      + '<span class="' + CLS.DOT + ' ' + n.kind + '"></span><span>' + esc(n.name) + '</span>'
      + (why ? '<span class="' + CLS.WHY + '" title="' + esc(why) + '">' + esc(why) + '</span>' : '') + '</div>';
  }).join('');
}

/** The HTML of a folder of the tree; while filtering (lower-cased {@code filter}), folders and files without a matching object are left out and the others unfolded. */
function nodeHtml(node, dirPath, filter) {
  let html = '';
  for (const [name, child] of [...node.dirs].sort((a, b) => a[0].localeCompare(b[0]))) {
    const path = dirPath + PATH_SEPARATOR + name;
    dirEntries.set(path, { name, entries: child.entries });
    const inner = nodeHtml(child, path, filter);
    if (filter && !inner) continue;
    html += '<div class="' + CLS.GROUP_HEADER + ' ' + CLS.DIR + (!filter && foldedDirs.has(path) ? ' ' + CLS.COLLAPSED : '') + '"' + dataAttr(DATA.DIR, path) + '>'
      + '<span>' + esc(name) + '</span>'
      + '<button class="' + CLS.WORKSPACE_OPEN + '" type="button" title="' + esc(t(MSG.FILES_OPEN_AS_WORKSPACE, name)) + '">⧉</button>'
      + '</div><div class="' + CLS.GROUP_ITEMS + '">' + inner + '</div>';
  }
  for (const f of node.files.sort((a, b) => a.shown.localeCompare(b.shown))) {
    const active = f.tab === session.active;
    const unfolded = f.entry && (!!filter || showsObjects(f.entry, active));
    const objects = unfolded ? objectsHtml(f.entry, filter, active) : '';
    if (filter && !objects) continue;   // no matching object (or not parsed yet: the panel is redrawn once it is)
    shownFiles++;
    html += '<div class="' + CLS.ITEM + ' ' + CLS.FILE + (active ? ' ' + CLS.SELECTED : '') + (f.tab ? ' ' + CLS.OPEN : '') + '"'
      + (f.entry ? dataAttr(DATA.FILE, session.active.workspace.files.indexOf(f.entry)) : dataAttr(DATA.TAB_INDEX, session.tabs.indexOf(f.tab)))
      + ' title="' + esc(f.path) + '">'
      + (f.entry ? '<span class="' + CLS.EXPANDER + '">' + (unfolded ? COLLAPSE_GLYPH : EXPAND_GLYPH) + '</span>' : '')
      + '<span class="' + (f.entry ? '' : CLS.EMPTY) + '">' + esc(f.shown) + '</span></div>';
    if (unfolded) html += '<div class="' + CLS.GROUP_ITEMS + ' ' + CLS.OBJECTS + (active ? ' ' + CLS.ACTIVE : '') + '">' + objects + '</div>';
  }
  return html;
}

/** Moves the highlight to the object being read, among the objects of the file being shown; nothing is rebuilt. */
export function renderFileListSelection() {
  const selected = session.active.selected;
  const shown = $(ID.FILES_CONTENT).querySelector(selector(CLS.OBJECTS) + selector(CLS.ACTIVE));
  for (const el of $(ID.FILES_CONTENT).querySelectorAll(selector(CLS.OBJECT))) {
    const on = !!shown && shown.contains(el) && el.dataset[DATA.ID] === selected;
    el.classList.toggle(CLS.SELECTED, on);
    if (on) el.scrollIntoView({ block: 'nearest' });
  }
}

export function renderFileList() {
  dirEntries.clear();
  const list = rows();
  const filter = session.active.filter.toLowerCase();
  shownFiles = 0;
  let html = nodeHtml(tree(list), '', filter);
  // while a search runs, the head says how many files answer it: the panel may be folded
  $(ID.FILES_COUNT).textContent = filter ? t(MSG.FILES_MATCHING, shownFiles, list.length) : String(list.length);
  if (!html && filter) html = '<div class="' + CLS.ITEM + ' ' + CLS.NO_MATCH + '">' + esc(t(MSG.LIST_NO_MATCH)) + '</div>';
  // files not parsed yet, or that could not be parsed at all, cannot answer the search: say so rather than let them silently miss
  const files = session.active.workspace.files;
  const parsing = filter ? files.filter(entry => !entry.model && !entry.failed).length : 0;
  if (parsing) {
    html += '<div class="' + CLS.ITEM + ' ' + CLS.EMPTY + ' ' + CLS.PARSING + '" title="' + esc(t(MSG.FILES_PARSING_TITLE)) + '">'
      + esc(plural(parsing, MSG.FILES_PARSING_ONE, MSG.FILES_PARSING_OTHER)) + '</div>';
  }
  const failed = filter ? files.filter(entry => entry.failed) : [];
  if (failed.length) {
    html += '<div class="' + CLS.ITEM + ' ' + CLS.EMPTY + ' ' + CLS.FAILED + '" title="' + esc(t(MSG.FILES_FAILED_TITLE, failed.map(f => f.name).join(TEXT.LIST_SEPARATOR))) + '">'
      + esc(plural(failed.length, MSG.FILES_FAILED_ONE, MSG.FILES_FAILED_OTHER)) + '</div>';
  }
  $(ID.FILES_CONTENT).innerHTML = html;
}

/** A click in the panel: folds are handled here (null); else what was hit — {entry}, {entry, id} for an object, {tab} for an empty tab, {folder, entries} for a folder's "open as workspace". */
export function fileListClick(target) {
  const header = target.closest('.' + CLS.DIR);
  if (header && target.closest('.' + CLS.WORKSPACE_OPEN)) {
    const dir = dirEntries.get(header.dataset[DATA.DIR]);
    return dir ? { folder: dir.name, entries: dir.entries } : null;
  }
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
    fileUnfolded.set(fileKeys(entry)[0], !showsObjects(entry, tabOfFile(entry) === session.active));
    renderFileList();
    return null;
  }
  return { entry };
}

/** Unfolds every folder and every file's objects, or folds them all. */
export function setAllUnfolded(unfolded) {
  foldedDirs.clear();
  if (!unfolded) for (const path of dirEntries.keys()) foldedDirs.add(path);
  fileUnfolded.clear();
  allFilesUnfolded = unfolded;
  renderFileList();
}

/** The Files panel folds to its title line. */
export const filesPanel = foldable({
  element: ID.FILES, toggle: ID.FILES_TOGGLE, storageKey: STORAGE_KEY.FILES_COLLAPSED,
  titles: { fold: MSG.FILES_COLLAPSE, unfold: MSG.FILES_EXPAND },
});
