import { ServerUnreachableError } from './api.js';
import { $, CLS, ID } from './dom.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';

const TOAST_DURATION_MS = 6000;
let toastTimer = null;

/** Shows a message at the bottom of the window for a few seconds. */
export function toast(msg) {
  const el = $(ID.TOAST);
  el.textContent = msg;
  el.classList.remove(CLS.HIDDEN);
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add(CLS.HIDDEN), TOAST_DURATION_MS);
}

/** Shows what went wrong with a call to the server: unreachable, or the error it answered. */
export function toastServerError(e) {
  toast(e instanceof ServerUnreachableError ? t(MSG.SERVER_UNREACHABLE, e.message) : t(MSG.SERVER_ERROR, e.message));
}
