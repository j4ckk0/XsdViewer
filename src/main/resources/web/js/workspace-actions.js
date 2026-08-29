/** The File menu on workspaces: new / open / save / close a workspace, and opening a folder as one. */
import { chooseFolder, openWorkspaceFile, saveWorkspaceFile } from './api.js';
import { MAX_FOLDER_FILES, PATH_SEPARATOR, TEXT, XSD_FILE_PATTERN } from './constants.js';
import { $, ID } from './dom.js';
import { addToLibrary, normPath } from './folder-library.js';
import { plural, t } from './i18n.js';
import { openLinkedSchemas } from './linked-schemas.js';
import { MSG } from './message-keys.js';
import { renderPage } from './page.js';
import { loadInto } from './schema-loader.js';
import { session } from './state.js';
import { activateTab, activeWorkspace, closeTab, closeWorkspace, isEmptyWorkspace, newTab, newWorkspace, renderTabBar, tabsOf, workspaceName } from './tabs.js';
import { toast, toastServerError } from './toast.js';

/** File ▸ New workspace: an empty one, made active. */
export function startWorkspace() {
  activateTab(newTab(newWorkspace()));
  renderPage();
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
  const own = tabsOf(ws);
  const saved = own.filter(tab => tab.model && tab.path);
  const skipped = own.filter(tab => tab.model && !tab.path).map(tab => tab.fileName);
  if (!saved.length) { toast(t(MSG.WORKSPACE_EMPTY)); return; }
  try {
    const r = await saveWorkspaceFile(saved.map(tab => tab.path), Math.max(0, saved.indexOf(session.active)), ws.path);
    if (r.cancelled) return;
    ws.path = r.path;
    renderTabBar();
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

/**
 * Opens a workspace answered by the server ({workspace, active, files, missing}) as its own group
 * of tabs, next to the workspaces already open (an empty unsaved active workspace is taken over).
 * A workspace already open is only brought to front. Workspaces are independent: the same file
 * may be open in two of them.
 */
export async function applyWorkspace(answer) {
  const already = session.workspaces.find(w => w.path === answer.workspace);
  if (already) {
    const own = tabsOf(already);
    if (own.length && activateTab(own[0])) renderPage();
    toast(t(MSG.WORKSPACE_ALREADY_OPEN, workspaceName(already)));
    return;
  }
  const ws = takeOverOrNewWorkspace();
  ws.path = answer.workspace;
  const opened = await openInWorkspace(ws, answer.files);
  if (!tabsOf(ws).length) newTab(ws);
  const activeFile = answer.files[answer.active];
  const active = (activeFile && opened.find(tab => tab.path === activeFile.path)) || opened[0] || tabsOf(ws)[0];
  activateTab(active);
  renderPage();
  toast(plural(opened.length, MSG.WORKSPACE_LOADED_ONE, MSG.WORKSPACE_LOADED_OTHER, workspaceName(ws))
    + (answer.missing.length ? TEXT.TOAST_SEPARATOR + t(MSG.WORKSPACE_MISSING, answer.missing.join(TEXT.LIST_SEPARATOR)) : ''));
  for (const tab of opened) openLinkedSchemas(tab);
}

/** File ▸ Open folder…: the server's folder chooser when it has a display (files come with their location), else the browser's. */
export async function openFolder() {
  if (!session.dialogs) { $(ID.FOLDER_INPUT).click(); return; }
  try {
    const r = await chooseFolder();
    if (r.cancelled) return;
    const name = r.folder.split(PATH_SEPARATOR).filter(Boolean).pop() || r.folder;
    await openFolderAsWorkspace(name, r.files, r.truncated);
  } catch (e) {
    toastServerError(e);
  }
}

/**
 * A folder opened or dropped in the browser: its schema files are kept at hand for following
 * links (the library) and its .xsd files are opened as a workspace named after the folder.
 * {@code relOf} gives a File's path in the folder.
 */
export async function openBrowserFolder(files, relOf, folderName) {
  addToLibrary(files, relOf);
  const schemas = files.filter(f => XSD_FILE_PATTERN.test(relOf(f))).sort((a, b) => relOf(a).localeCompare(relOf(b)));
  const kept = schemas.slice(0, MAX_FOLDER_FILES);
  const read = [];
  for (const f of kept) read.push({ name: f.name, path: null, text: await f.text(), rel: normPath(relOf(f)) });   // rel: what links resolve to in the library
  await openFolderAsWorkspace(folderName, read, schemas.length > kept.length);
}

/** Opens files ({name, path, text}) as a new workspace named {@code name}. */
async function openFolderAsWorkspace(name, files, truncated) {
  if (!files.length) { toast(t(MSG.FOLDER_EMPTY, name)); return; }
  const ws = takeOverOrNewWorkspace();
  ws.label = name;
  const opened = await openInWorkspace(ws, files);
  if (!tabsOf(ws).length) newTab(ws);
  activateTab(opened[0] || tabsOf(ws)[0]);
  renderPage();
  toast(plural(opened.length, MSG.FOLDER_OPENED_ONE, MSG.FOLDER_OPENED_OTHER, name)
    + (truncated ? TEXT.TOAST_SEPARATOR + t(MSG.FOLDER_TRUNCATED, files.length) : ''));
  for (const tab of opened) openLinkedSchemas(tab);
}

/** The workspace a new set of files goes to: the active one when it is empty and unsaved, else a new one. */
const takeOverOrNewWorkspace = () => (isEmptyWorkspace(activeWorkspace()) ? activeWorkspace() : newWorkspace());

/** Loads files ({name, path, text, rel?}) into tabs of {@code ws} (its empty tabs first); returns the tabs loaded, in order. */
async function openInWorkspace(ws, files) {
  const tabs = [];
  for (const f of files) {
    const tab = tabsOf(ws).find(x => !x.model) || newTab(ws);
    if (await loadInto(tab, f.name, f.text, f.path)) { tab.rel = f.rel || null; tabs.push(tab); }
    else if (tab !== session.active) closeTab(tab);
  }
  return tabs;
}
