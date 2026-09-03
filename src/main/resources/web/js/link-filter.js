/**
 * Which categories of link the graph draws: the *Links* menu of its toolbar. A category off is
 * remembered across sessions, and the menu shows only the categories the file at hand can have
 * (its own chain for a WSDL or a Schematron, the schema's own links for the others).
 */
import { LINK_CATEGORIES, STORAGE_KEY, TEXT, linkCategory } from './constants.js';
import { $, CLS, DATA, ID, selector } from './dom.js';

/** The categories not drawn. */
const hidden = new Set();

/** True when the link between {@code fromKind} and {@code toKind} belongs to a category being drawn. */
export const isLinkShown = (edge, fromKind, toKind) => !hidden.has(linkCategory(edge, fromKind, toKind));

export const hiddenCategories = () => [...hidden];

/** Reads the categories left off in a previous session. */
export function initLinkFilter() {
  let stored = null;
  try { stored = localStorage.getItem(STORAGE_KEY.HIDDEN_LINKS); } catch (e) { /* storage unavailable */ }
  for (const c of (stored || '').split(TEXT.STORED_SEPARATOR)) if (LINK_CATEGORIES.includes(c)) hidden.add(c);
}

/** Draws or hides a category (a click on its entry of the menu); the caller redraws the graph. */
export function toggleCategory(category) {
  if (!hidden.delete(category)) hidden.add(category);
  try { localStorage.setItem(STORAGE_KEY.HIDDEN_LINKS, [...hidden].join(TEXT.STORED_SEPARATOR)); } catch (e) { /* storage unavailable */ }
}

/** Every category back on. */
export function showAllLinks() {
  hidden.clear();
  try { localStorage.removeItem(STORAGE_KEY.HIDDEN_LINKS); } catch (e) { /* storage unavailable */ }
}

/** The check marks of the menu and the mark on its button (a filter is on); the entries shown are the file's own (see the legend's classes). */
export function renderLinkMenu() {
  for (const entry of $(ID.LINK_MENU).querySelectorAll('[data-' + DATA.CATEGORY + ']')) {
    entry.classList.toggle(CLS.CHECKED, !hidden.has(entry.dataset[DATA.CATEGORY]));
  }
  $(ID.LINK_MENU_BUTTON).classList.toggle(CLS.FILTERED, hidden.size > 0);
  $(ID.LINK_MENU).classList.remove(CLS.WSDL, CLS.SCHEMATRON);
  const legend = $(ID.GRAPH_LEGEND);   // the legend already says which family the shown file is
  for (const family of [CLS.WSDL, CLS.SCHEMATRON]) if (legend.classList.contains(family)) $(ID.LINK_MENU).classList.add(family);
}

/** Closes the menu (a click elsewhere, Escape, a redraw). */
export function closeLinkMenu() {
  $(ID.LINK_MENU).classList.add(CLS.HIDDEN);
}

/** The category of the entry a click landed on, or null. */
export function categoryOfClick(target) {
  const entry = target.closest('[data-' + DATA.CATEGORY + ']');
  return entry ? entry.dataset[DATA.CATEGORY] : null;
}

export const isShowAllClick = (target) => !!target.closest(selector(CLS.LINKS_ALL));
