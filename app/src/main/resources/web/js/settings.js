/** The Settings menu's server settings (GET / POST /api/settings), kept by the server from one run to the next: the automatic stop, and the port a dialog sets for the next start. */
import { fetchSettings, saveSettings } from './api.js';
import { $ } from './dom.js';
import { CLS, ID } from './dom-names.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { toast, toastServerError } from './toast.js';

/** 0 means no chosen port: the default stands. */
const PORT_UNSET = 0;

let autoStop = true;
let port = PORT_UNSET;   // the port chosen from the menu for the next start (0 = the default)

/** Asks the server; nothing is applied yet (the texts are not loaded when this runs). */
export async function loadSettings() {
  try {
    const s = await fetchSettings();
    autoStop = s.autoStop !== false;
    port = Number(s.port) || PORT_UNSET;
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

/** Settings ▸ Server port…: a small dialog to set the port used at the next start; empty clears it back to the default. */
export function showPortDialog() {
  const input = $(ID.PORT_INPUT);
  input.value = port === PORT_UNSET ? '' : String(port);
  input.placeholder = location.port || '';   // the port in use now, as a hint
  $(ID.PORT_DIALOG).showModal();
  input.focus();
}

export const closePortDialog = () => $(ID.PORT_DIALOG).close();

/** Saves the port typed in the dialog (empty = back to the default); the server keeps it for the next start. */
export async function savePort() {
  const text = $(ID.PORT_INPUT).value.trim();
  let chosen = PORT_UNSET;
  if (text) {
    chosen = Number(text);
    if (!Number.isInteger(chosen) || chosen < 1 || chosen > 65535) { toast(t(MSG.PORT_INVALID)); return; }
  }
  try {
    port = Number((await saveSettings({ port: chosen })).port) || PORT_UNSET;
  } catch (e) {
    toastServerError(e);
    return;
  }
  closePortDialog();
  toast(port === PORT_UNSET ? t(MSG.SETTINGS_PORT_CLEARED) : t(MSG.SETTINGS_PORT_SET, port));
}
