/** The File menu and the drop zone: opening files and folders, closing, quitting, the start-up file. */
import { fetchInitialFile, quitServer } from './api.js';
import { $, CLS, ID, esc } from './dom.js';
import { plural, t } from './i18n.js';
import { addToLibrary } from './library.js';
import { MSG } from './message-keys.js';
import { checkPendingJump } from './navigation.js';
import { renderPage } from './page.js';
import { loadInto } from './schema-loader.js';
import { session } from './state.js';
import { activateTab, newTab, resetTab } from './tabs.js';
import { toast } from './toast.js';

/** Opens files from the file dialog / a drop: the first one in the current tab if it is empty, the others in new tabs. */
export async function openFiles(files) {
  for (const file of files) {
    if (session.active.model) {
      activateTab(newTab());
      renderPage();
    }
    await loadText(file.name, await file.text(), null);
  }
}

/** Loads a schema into the active tab. */
export async function loadText(name, text, path) {
  if (!(await loadInto(session.active, name, text, path))) return;
  renderPage();
  checkPendingJump(session.active);
}

/** File ▸ Close: empties the active tab. */
export function closeFile() {
  resetTab(session.active);
  renderPage();
}

/** Registers the schema files of an opened / dropped folder and says how many. */
export function addFolder(files, relOf, folderLabel) {
  const n = addToLibrary(files, relOf);
  toast(plural(n, MSG.LIBRARY_ADDED_ONE, MSG.LIBRARY_ADDED_OTHER, folderLabel));
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

/** Opens the file given on the server's command line, if any. */
export async function loadInitialFile() {
  try {
    const f = await fetchInitialFile();
    if (f) await loadText(f.name, f.text, f.path);
  } catch (e) { /* server unreachable: reported when the user loads a file */ }
}
