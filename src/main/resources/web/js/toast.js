import { $, CLS, ID } from './dom.js';

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
