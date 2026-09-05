/** What the server can do for the page (native dialogs, its language and versions), and the menu entries that depend on it. */
import { fetchCapabilities } from './api.js';
import { $ } from './dom.js';
import { ID } from './dom-names.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

/** Asks the server; nothing is applied yet (the texts are not loaded when this runs). */
export async function loadCapabilities() {
  try {
    const caps = await fetchCapabilities();
    session.dialogs = !!caps.dialogs;
    session.serverLanguage = caps.language || null;
    session.serverVersion = caps.version || null;
    session.javaVersion = caps.javaVersion || null;
    session.logFile = caps.logFile || null;
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
