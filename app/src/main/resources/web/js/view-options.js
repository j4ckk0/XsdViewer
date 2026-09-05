/**
 * Client-side display choices the browser remembers, unlike the server settings in {@code settings.js}.
 * For now: whether the cross-view handles are drawn — the ◎ on a model box and the ▤ on a graph node
 * that jump between the Model and Graph views, and the ×N mark counting the objects a box is shared by.
 */
import { STORAGE_KEY } from './constants.js';
import { $ } from './dom.js';
import { CLS, ID } from './dom-names.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { toast } from './toast.js';

const STORAGE_FALSE = '0';
const read = () => {
  try { return localStorage.getItem(STORAGE_KEY.CROSS_VIEW_HANDLES) !== STORAGE_FALSE; } catch (e) { return true; }
};
let handles = read();

/** Whether the ◎ / ▤ handles and the ×N shared mark are drawn. On by default. */
export const crossViewHandles = () => handles;

/** Reflects the choice in the Settings menu; call once the page is wired. */
export function applyViewOptions() {
  $(ID.MENU_HANDLES).classList.toggle(CLS.CHECKED, handles);
}

/** Settings ▸ Cross-view handles: flips whether they are drawn, remembers it, updates the menu and says which way it went. */
export function toggleCrossViewHandles() {
  handles = !handles;
  try { localStorage.setItem(STORAGE_KEY.CROSS_VIEW_HANDLES, handles ? '1' : STORAGE_FALSE); } catch (e) { /* private mode: kept for this session only */ }
  applyViewOptions();
  toast(t(handles ? MSG.HANDLES_ON : MSG.HANDLES_OFF));
}
