/** The File menu on files: opening schemas (dialog, drop, start-up file), closing, quitting. Workspaces and folders: workspace-actions.js. */
import { chooseFiles, fetchInitialFile, quitServer } from './api.js';
import { busy } from './busy.js';
import { XML_FILE_PATTERN } from './constants.js';
import { $, CLS, ID, esc } from './dom.js';
import { t } from './i18n.js';
import { openLinkedSchemas } from './linked-schemas.js';
import { MSG } from './message-keys.js';
import { checkPendingJump } from './navigation.js';
import { renderPage } from './page.js';
import { stopPresence } from './presence.js';
import { loadInto } from './schema-loader.js';
import { session } from './state.js';
import { activateTab, closeAllTabs, newTab, resetTab } from './tabs.js';
import { toast, toastServerError } from './toast.js';
import { canValidate, replaceDocument, validateText } from './validate.js';
import { applyWorkspace } from './workspace-actions.js';

/** File ▸ Open…: the server's native dialog when it has a display (files then come with their location), else the browser's. */
export async function openSchemas() {
  if (!session.dialogs) { $(ID.FILE_INPUT).click(); return; }
  try {
    await openServerFiles(await chooseFiles());
  } catch (e) {
    toastServerError(e);
  }
}

/**
 * Opens files from the browser's file dialog / a drop: the first one in the current tab if it is empty, the others in new tabs.
 * An .xml file dropped on a schema that can be validated, or on a validation, is a document to validate, not a schema to open.
 */
export async function openFiles(files) {
  const documents = files.filter(f => XML_FILE_PATTERN.test(f.name) && (canValidate() || session.active.validation));
  for (const doc of documents) {
    if (session.active.validation) await replaceDocument(doc.name, await doc.text());
    else await validateText(doc.name, await doc.text());
  }
  const schemas = files.filter(f => !documents.includes(f));
  await busy(t(MSG.BUSY_OPENING), async () => {
    for (const file of schemas) await openInFreshTab(file.name, await file.text(), null);
  });
}

/** Opens files read by the server ({name, path, text}), the same way. */
async function openServerFiles(files) {
  await busy(t(MSG.BUSY_OPENING), async () => {
    for (const f of files) await openInFreshTab(f.name, f.text, f.path);
  });
}

async function openInFreshTab(name, text, path) {
  if (session.active.model || session.active.validation) {
    activateTab(newTab());
    renderPage();
  }
  await loadText(name, text, path);
}

/** Loads a schema into the active tab, then the schemas it links to (once its location is known). */
async function loadText(name, text, path) {
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
  stopPresence(false);                       // the server is stopping: no reconnection attempts, nothing to tell it
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
    else await busy(t(MSG.BUSY_OPENING), loadText(f.name, f.text, f.path));
  } catch (e) { /* server unreachable: reported when the user loads a file */ }
}
