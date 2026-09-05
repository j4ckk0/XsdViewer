/** The File menu on workspaces: new / open / save / close a workspace, and opening a folder as one. */
import { chooseFolder, openWorkspaceFile, saveWorkspaceFile } from './api.js';
import { busy } from './busy.js';
import { MAX_AUTO_OPEN, MAX_FOLDER_FILES, TEXT, XSD_FILE_PATTERN } from './constants.js';
import { ensureTab, parseInBackground } from './file-tabs.js';
import { registerFile, savableFiles } from './workspace-files.js';
import { $ } from './dom.js';
import { ID } from './dom-names.js';
import { addToLibrary, normPath } from './folder-library.js';
import { plural, t } from './i18n.js';
import { openLinkedSchemas } from './linked-schemas.js';
import { MSG } from './message-keys.js';
import { renderPage } from './page.js';
import { session } from './state.js';
import { activateTab, activeWorkspace, closeWorkspace, isEmptyWorkspace, newTab, newWorkspace, renderNavigation, tabsOf, workspaceName } from './tabs.js';
import { toast, toastServerError } from './toast.js';

/** File ▸ New workspace: an empty one, made active. */
export function startWorkspace() {
  activateTab(newTab(newWorkspace()));
  renderPage();
}

/** Warns before opening this many tabs at once. */
const OPEN_ALL_CONFIRM_FROM = 30;

/** File ▸ Open all listed files: every listed file of the active workspace gets a tab (asked first when there are many). */
export async function openAllListed() {
  const entries = listedOnly();
  if (!entries.length) return;
  if (entries.length >= OPEN_ALL_CONFIRM_FROM && !window.confirm(t(MSG.OPEN_ALL_CONFIRM, entries.length))) return;
  const ws = activeWorkspace();
  const opened = await busy(t(MSG.BUSY_OPENING), () => openInWorkspace(ws, entries));
  renderPage();
  toast(t(MSG.OPEN_ALL_DONE, opened.length));
}

/** File ▸ Close workspace: the active workspace and all its tabs. */
export function closeActiveWorkspace() {
  closeWorkspace(activeWorkspace());
  renderPage();
}

/** File ▸ Save workspace…: the active workspace (the locations of its files), written where the server's "save as" dialog says; its own file is proposed. */
export async function saveWorkspace() {
  if (!session.dialogs) { toast(t(MSG.DIALOGS_UNAVAILABLE)); return; }
  const ws = activeWorkspace();
  const { saved, skipped } = savableFiles(ws);
  if (!saved.length) { toast(t(MSG.WORKSPACE_EMPTY)); return; }
  try {
    // the file the reader is on is the one the workspace opens on
    const active = Math.max(0, saved.findIndex(entry => entry === session.active.file));
    const r = await saveWorkspaceFile(saved.map(entry => entry.path), active, ws.path);
    if (r.cancelled) return;
    ws.path = r.path;
    renderNavigation();
    toast(t(MSG.WORKSPACE_SAVED, r.path) + (skipped.length ? TEXT.TOAST_SEPARATOR + t(MSG.WORKSPACE_NOT_SAVED, skipped.join(TEXT.LIST_SEPARATOR)) : ''));
  } catch (e) {
    toastServerError(e);
  }
}

/** File ▸ Open workspace…: opens the workspace chosen in the server's dialog as a new group of tabs. */
export async function openWorkspace() {
  if (!session.dialogs) { toast(t(MSG.DIALOGS_UNAVAILABLE)); return; }
  try {
    const ws = await openWorkspaceFile();
    if (!ws.cancelled) await applyWorkspace(ws);
  } catch (e) {
    toastServerError(e);
  }
}

/** Opens a workspace answered by the server as its own group of tabs (an empty unsaved workspace is taken over; one already open is brought to front). */
export function applyWorkspace(answer) {
  return busy(t(MSG.BUSY_WORKSPACE), () => doApplyWorkspace(answer));
}

async function doApplyWorkspace(answer) {
  const already = session.workspaces.find(w => w.path === answer.workspace);
  if (already) {
    const own = tabsOf(already);
    if (own.length && activateTab(own[0])) renderPage();
    toast(t(MSG.WORKSPACE_ALREADY_OPEN, workspaceName(already)));
    return;
  }
  const ws = takeOverOrNewWorkspace();
  ws.path = answer.workspace;
  const entries = answer.files.map(f => registerFile(ws, f));
  const few = entries.length <= MAX_AUTO_OPEN;
  const opened = await openInWorkspace(ws, few ? entries : entries.slice(answer.active, answer.active + 1));
  if (!tabsOf(ws).length) newTab(ws);
  const active = (entries[answer.active] && opened.find(tab => tab.file === entries[answer.active])) || opened[0] || tabsOf(ws)[0];
  activateTab(active);
  renderPage();
  toast((few ? plural(opened.length, MSG.WORKSPACE_LOADED_ONE, MSG.WORKSPACE_LOADED_OTHER, workspaceName(ws))
    : t(MSG.WORKSPACE_LISTED, workspaceName(ws), entries.length, opened.length))
    + (answer.missing.length ? TEXT.TOAST_SEPARATOR + t(MSG.WORKSPACE_MISSING, answer.missing.join(TEXT.LIST_SEPARATOR)) : ''));
  if (!few) parseInBackground(ws);
  for (const tab of opened) openLinkedSchemas(tab);
}

/** File ▸ Open folder…: the server's folder chooser when it has a display (files come with their location), else the browser's. */
export async function openFolder() {
  if (!session.dialogs) { $(ID.FOLDER_INPUT).click(); return; }
  try {
    const r = await busy(t(MSG.BUSY_READING_FOLDER), chooseFolder());
    if (r.cancelled) return;
    await busy(t(MSG.BUSY_WORKSPACE), openFolderAsWorkspace(r.name || r.folder, r.files, r.truncated));
  } catch (e) {
    toastServerError(e);
  }
}

/** A folder opened or dropped in the browser: its files feed the library, its .xsd files become a workspace named after it ({@code relOf}: a File's path in the folder). */
export function openBrowserFolder(files, relOf, folderName) {
  return busy(t(MSG.BUSY_READING_FOLDER), async () => {
    addToLibrary(files, relOf);
    const schemas = files.filter(f => XSD_FILE_PATTERN.test(relOf(f))).sort((a, b) => relOf(a).localeCompare(relOf(b)));
    const kept = schemas.slice(0, MAX_FOLDER_FILES);
    const read = [];
    for (const f of kept) read.push({ name: f.name, path: null, text: await f.text(), rel: normPath(relOf(f)) });   // rel: what links resolve to in the library
    await openFolderAsWorkspace(folderName, read, schemas.length > kept.length);
  });
}

/** A sub-folder of the Files panel opened as its own workspace: the files beneath it, with their text and model already at hand. */
export function openEntriesAsWorkspace(name, entries) {
  return busy(t(MSG.BUSY_WORKSPACE), () => doOpenEntriesAsWorkspace(name, entries));
}

async function doOpenEntriesAsWorkspace(name, entries) {
  const ws = newWorkspace();
  ws.label = name;
  const copies = entries.map(e => registerFile(ws, { name: e.name, path: e.path, rel: e.rel, text: e.text, model: e.model }));
  const opened = await openInWorkspace(ws, copies.length <= MAX_AUTO_OPEN ? copies : copies.slice(0, 1));
  if (!tabsOf(ws).length) newTab(ws);
  activateTab(opened[0] || tabsOf(ws)[0]);
  renderPage();
  toast(copies.length <= MAX_AUTO_OPEN ? plural(opened.length, MSG.FOLDER_OPENED_ONE, MSG.FOLDER_OPENED_OTHER, name) : t(MSG.FOLDER_LISTED, copies.length, name, opened.length));
  if (copies.length > MAX_AUTO_OPEN) parseInBackground(ws);
}

/** Lists files as a new workspace named {@code name}; opens them all up to MAX_AUTO_OPEN, else only the first (the others wait in the Files panel, parsed in the background). */
async function openFolderAsWorkspace(name, files, truncated) {
  if (!files.length) { toast(t(MSG.FOLDER_EMPTY, name)); return; }
  const ws = takeOverOrNewWorkspace();
  ws.label = name;
  const entries = files.map(f => registerFile(ws, f));
  const few = entries.length <= MAX_AUTO_OPEN;
  const opened = await openInWorkspace(ws, few ? entries : entries.slice(0, 1));
  if (!tabsOf(ws).length) newTab(ws);
  activateTab(opened[0] || tabsOf(ws)[0]);
  renderPage();
  toast((few ? plural(opened.length, MSG.FOLDER_OPENED_ONE, MSG.FOLDER_OPENED_OTHER, name) : t(MSG.FOLDER_LISTED, entries.length, name, opened.length))
    + (truncated ? TEXT.TOAST_SEPARATOR + t(MSG.FOLDER_TRUNCATED, files.length) : ''));
  if (!few) parseInBackground(ws);
  for (const tab of opened) openLinkedSchemas(tab);
}

/** The workspace a new set of files goes to: the active one when it is empty and unsaved, else a new one. */
const takeOverOrNewWorkspace = () => (isEmptyWorkspace(activeWorkspace()) ? activeWorkspace() : newWorkspace());

/** Opens workspace files (entries of {@code ws.files}) in tabs; returns the tabs opened, in order (files that fail to parse are skipped). */
async function openInWorkspace(ws, entries) {
  const tabs = [];
  for (const entry of entries) {
    const tab = await ensureTab(entry);
    if (tab) tabs.push(tab);
  }
  return tabs;
}
