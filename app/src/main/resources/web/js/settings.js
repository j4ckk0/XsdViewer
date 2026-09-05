/** The Settings menu: the server's automatic stop (GET / POST /api/settings), kept by the server from one run to the next. */
import { fetchSettings, saveSettings } from './api.js';
import { $ } from './dom.js';
import { CLS, ID } from './dom-names.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { toast, toastServerError } from './toast.js';

let autoStop = true;

/** Asks the server; nothing is applied yet (the texts are not loaded when this runs). */
export async function loadSettings() {
  try {
    autoStop = (await fetchSettings()).autoStop !== false;
  } catch (e) { /* server unreachable: the menu shows the default */ }
}

export function applySettings() {
  $(ID.MENU_AUTO_STOP).classList.toggle(CLS.CHECKED, autoStop);
}

/** Settings ▸ Stop the server when the last page is closed: flips it, on the server and in the menu. */
export async function toggleAutoStop() {
  try {
    autoStop = (await saveSettings({ autoStop: !autoStop })).autoStop !== false;
  } catch (e) {
    toastServerError(e);
    return;
  }
  applySettings();
  toast(t(autoStop ? MSG.SETTINGS_AUTO_STOP_ON : MSG.SETTINGS_AUTO_STOP_OFF));
}
