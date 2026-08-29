/** The File menu and the drop zone: opening files, folders and workspaces, saving a workspace, closing, quitting, the start-up file. */
import { ServerUnreachableError, chooseFiles, fetchCapabilities, fetchInitialFile, openWorkspaceFile, quitServer, saveWorkspaceFile } from './api.js';
import { $, CLS, ID, esc } from './dom.js';
import { plural, t } from './i18n.js';
import { addToLibrary } from './library.js';
import { openLinkedSchemas } from './linked-schemas.js';
import { MSG } from './message-keys.js';
import { checkPendingJump } from './navigation.js';
import { renderPage } from './page.js';
import { loadInto } from './schema-loader.js';
import { session } from './state.js';
import { activateTab, closeAllTabs, closeTab, newTab, resetTab } from './tabs.js';
import { toast } from './toast.js';

const LIST_SEPARATOR = ', ';
const TOAST_SEPARATOR = ' — ';

/** Asks the server what it can do (native dialogs) and enables the menu accordingly. */
export async function initCapabilities() {
  try {
    session.dialogs = !!(await fetchCapabilities()).dialogs;
  } catch (e) {
    session.dialogs = false;
  }
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

/** File ▸ Close all tabs. */
export function closeAll() {
  closeAllTabs();
  renderPage();
}

/** Registers the schema files of an opened / dropped folder and says how many. */
export function addFolder(files, relOf, folderLabel) {
  const n = addToLibrary(files, relOf);
  toast(plural(n, MSG.LIBRARY_ADDED_ONE, MSG.LIBRARY_ADDED_OTHER, folderLabel));
}

/** File ▸ Save workspace…: the locations of the open files, written where the server's "save as" dialog says. */
export async function saveWorkspace() {
  if (!session.dialogs) { toast(t(MSG.DIALOGS_UNAVAILABLE)); return; }
  const saved = session.tabs.filter(tab => tab.model && tab.path);
  const skipped = session.tabs.filter(tab => tab.model && !tab.path).map(tab => tab.fileName);
  if (!saved.length) { toast(t(MSG.WORKSPACE_EMPTY)); return; }
  try {
    const r = await saveWorkspaceFile(saved.map(tab => tab.path), Math.max(0, saved.indexOf(session.active)));
    if (r.cancelled) return;
    session.workspacePath = r.path;
    toast(t(MSG.WORKSPACE_SAVED, r.path) + (skipped.length ? TOAST_SEPARATOR + t(MSG.WORKSPACE_NOT_SAVED, skipped.join(LIST_SEPARATOR)) : ''));
  } catch (e) {
    toast(serverError(e));
  }
}

/** File ▸ Open workspace…: replaces the open tabs with the files of the workspace chosen in the server's dialog. */
export async function openWorkspace() {
  if (!session.dialogs) { toast(t(MSG.DIALOGS_UNAVAILABLE)); return; }
  try {
    const ws = await openWorkspaceFile();
    if (!ws.cancelled) await applyWorkspace(ws);
  } catch (e) {
    toast(serverError(e));
  }
}

/** Replaces the open tabs with the files of a workspace answered by the server ({workspace, active, files, missing}). */
async function applyWorkspace(ws) {
  session.workspacePath = ws.workspace;
  const first = closeAllTabs();
  const loaded = [];
  for (const f of ws.files) {
    const tab = loaded.length ? newTab() : first;
    if (await loadInto(tab, f.name, f.text, f.path)) loaded.push(tab);
    else if (tab !== first) closeTab(tab);
  }
  if (loaded.length) session.active = loaded[Math.min(ws.active, loaded.length - 1)];
  renderPage();
  toast(plural(loaded.length, MSG.WORKSPACE_LOADED_ONE, MSG.WORKSPACE_LOADED_OTHER, ws.workspace)
    + (ws.missing.length ? TOAST_SEPARATOR + t(MSG.WORKSPACE_MISSING, ws.missing.join(LIST_SEPARATOR)) : ''));
  for (const tab of loaded) openLinkedSchemas(tab);
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
