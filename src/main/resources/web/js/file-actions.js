/** The File menu and the drop zone: opening files, folders and workspaces, saving a workspace, closing, quitting, the start-up file. */
import { ServerUnreachableError, chooseFiles, chooseFolder, fetchCapabilities, fetchInitialFile, openWorkspaceFile, quitServer, saveWorkspaceFile } from './api.js';
import { MAX_FOLDER_FILES, PATH_SEPARATOR, XSD_FILE_PATTERN } from './constants.js';
import { $, CLS, ID, esc } from './dom.js';
import { plural, t } from './i18n.js';
import { addToLibrary, normPath } from './library.js';
import { openLinkedSchemas } from './linked-schemas.js';
import { MSG } from './message-keys.js';
import { checkPendingJump } from './navigation.js';
import { renderPage } from './page.js';
import { loadInto } from './schema-loader.js';
import { session } from './state.js';
import { activateTab, activeWorkspace, closeAllTabs, closeTab, closeWorkspace, isEmptyWorkspace, newTab, newWorkspace, renderTabBar, resetTab, tabsOf, workspaceName } from './tabs.js';
import { toast } from './toast.js';

const LIST_SEPARATOR = ', ';
const TOAST_SEPARATOR = ' — ';

/** Asks the server what it can do (native dialogs) and which language the machine speaks; nothing is applied yet. */
export async function loadCapabilities() {
  try {
    const caps = await fetchCapabilities();
    session.dialogs = !!caps.dialogs;
    session.serverLanguage = caps.language || null;
    session.serverVersion = caps.version || null;
    session.javaVersion = caps.javaVersion || null;
  } catch (e) {
    session.dialogs = false;
    session.serverLanguage = null;
  }
}

/** Enables the workspace commands when the server has dialogs, else disables them and says why. */
export function applyCapabilities() {
  for (const id of [ID.MENU_OPEN_WORKSPACE, ID.MENU_SAVE_WORKSPACE]) {
    $(id).disabled = !session.dialogs;
    if (!session.dialogs) $(id).title = t(MSG.DIALOGS_UNAVAILABLE);
  }
}

const serverError = (e) => (e instanceof ServerUnreachableError ? t(MSG.SERVER_UNREACHABLE, e.message) : t(MSG.SERVER_ERROR, e.message));

/** File ▸ Open…: the server's native dialog when it has a display (files then come with their location), else the browser's. */
export async function openSchemas() {
  if (!session.dialogs) { $(ID.FILE_INPUT).click(); return; }
  try {
    await openServerFiles(await chooseFiles());
  } catch (e) {
    toast(serverError(e));
  }
}

/** Opens files from the browser's file dialog / a drop: the first one in the current tab if it is empty, the others in new tabs. */
export async function openFiles(files) {
  for (const file of files) await openInFreshTab(file.name, await file.text(), null);
}

/** Opens files read by the server ({name, path, text}), the same way. */
export async function openServerFiles(files) {
  for (const f of files) await openInFreshTab(f.name, f.text, f.path);
}

async function openInFreshTab(name, text, path) {
  if (session.active.model) {
    activateTab(newTab());
    renderPage();
  }
  await loadText(name, text, path);
}

/** Loads a schema into the active tab, then the schemas it links to (once its location is known). */
export async function loadText(name, text, path) {
  const tab = session.active;
  if (!(await loadInto(tab, name, text, path))) return;
  renderPage();
  checkPendingJump(tab);
  if (tab.path) openLinkedSchemas(tab);
  else if (tab.located) tab.located.then(p => { if (p && tab.path === p) openLinkedSchemas(tab); });
}

/** File ▸ Close: empties the active tab. */
export function closeFile() {
  resetTab(session.active);
  renderPage();
}

/** File ▸ Close all tabs: every workspace goes. */
export function closeAll() {
  closeAllTabs();
  renderPage();
}

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

/** File ▸ Open folder…: the server's folder chooser when it has a display (files come with their location), else the browser's. */
export async function openFolder() {
  if (!session.dialogs) { $(ID.FOLDER_INPUT).click(); return; }
  try {
    const r = await chooseFolder();
    if (r.cancelled) return;
    const name = r.folder.split(PATH_SEPARATOR).filter(Boolean).pop() || r.folder;
    await openFolderAsWorkspace(name, r.files, r.truncated);
  } catch (e) {
    toast(serverError(e));
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

/** Opens files ({name, path, text}) as a new workspace named {@code name} (an empty unsaved active workspace is taken over). */
async function openFolderAsWorkspace(name, files, truncated) {
  if (!files.length) { toast(t(MSG.FOLDER_EMPTY, name)); return; }
  const ws = isEmptyWorkspace(activeWorkspace()) ? activeWorkspace() : newWorkspace();
  ws.label = name;
  const opened = await openInWorkspace(ws, files);
  if (!tabsOf(ws).length) newTab(ws);
  activateTab(opened[0] || tabsOf(ws)[0]);
  renderPage();
  toast(plural(opened.length, MSG.FOLDER_OPENED_ONE, MSG.FOLDER_OPENED_OTHER, name)
    + (truncated ? TOAST_SEPARATOR + t(MSG.FOLDER_TRUNCATED, files.length) : ''));
  for (const tab of opened) openLinkedSchemas(tab);
}

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
    toast(t(MSG.WORKSPACE_SAVED, r.path) + (skipped.length ? TOAST_SEPARATOR + t(MSG.WORKSPACE_NOT_SAVED, skipped.join(LIST_SEPARATOR)) : ''));
  } catch (e) {
    toast(serverError(e));
  }
}

/** File ▸ Open workspace…: opens the workspace chosen in the server's dialog as a new group of tabs. */
export async function openWorkspace() {
  if (!session.dialogs) { toast(t(MSG.DIALOGS_UNAVAILABLE)); return; }
  try {
    const ws = await openWorkspaceFile();
    if (!ws.cancelled) await applyWorkspace(ws);
  } catch (e) {
    toast(serverError(e));
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
  const ws = isEmptyWorkspace(activeWorkspace()) ? activeWorkspace() : newWorkspace();
  ws.path = answer.workspace;
  const opened = await openInWorkspace(ws, answer.files);
  if (!tabsOf(ws).length) newTab(ws);
  const activeFile = answer.files[answer.active];
  const active = (activeFile && opened.find(tab => tab.path === activeFile.path)) || opened[0] || tabsOf(ws)[0];
  activateTab(active);
  renderPage();
  toast(plural(opened.length, MSG.WORKSPACE_LOADED_ONE, MSG.WORKSPACE_LOADED_OTHER, workspaceName(ws))
    + (answer.missing.length ? TOAST_SEPARATOR + t(MSG.WORKSPACE_MISSING, answer.missing.join(LIST_SEPARATOR)) : ''));
  for (const tab of opened) openLinkedSchemas(tab);
}

/** File ▸ Quit: stops the server, then closes the page (browsers only let a script close a
 *  window it opened, so a "stopped" notice is shown for the other case). */
export async function quit() {
  if (!confirm(t(MSG.QUIT_CONFIRM))) return;
  try {
    await quitServer();
  } catch (e) {
    toast(t(MSG.CANNOT_STOP, e.message));
    return;
  }
  document.body.innerHTML = '<div id="' + ID.EMPTY + '" class="' + CLS.VIEW + '"><div class="' + CLS.EMPTY_BOX + '">'
    + '<div class="' + CLS.BIG + '">' + esc(t(MSG.QUIT_STOPPED_TITLE)) + '</div><div>' + esc(t(MSG.QUIT_STOPPED_HINT)) + '</div></div></div>';
  window.close();
}

/** Opens the file given on the server's command line, if any: a schema, or a workspace. */
export async function loadInitialFile() {
  try {
    const f = await fetchInitialFile();
    if (!f) return;
    if (f.files) await applyWorkspace(f);
    else await loadText(f.name, f.text, f.path);
  } catch (e) { /* server unreachable: reported when the user loads a file */ }
}
