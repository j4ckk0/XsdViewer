/**
 * The theme of the page: the system's (its light or dark setting, followed as it changes), light,
 * or dark — chosen in the Settings menu, remembered by the browser. The choice becomes
 * data-theme="light" / "dark" on <html>, which the stylesheet's palette reads; js/theme-boot.js
 * stamps it before anything is drawn.
 */
import { STORAGE_KEY, THEME } from './constants.js';
import { $, CLS, ID } from './dom.js';

const DATA_THEME = 'theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';
const MENU_ENTRIES = { [THEME.SYSTEM]: ID.MENU_THEME_SYSTEM, [THEME.LIGHT]: ID.MENU_THEME_LIGHT, [THEME.DARK]: ID.MENU_THEME_DARK };

let choice = THEME.SYSTEM;

/** Restores the remembered choice and follows the system's setting while that is the choice. */
export function initTheme() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY.THEME);
    if (Object.values(THEME).includes(stored)) choice = stored;
  } catch (e) { /* storage unavailable */ }
  apply();
  matchMedia(DARK_QUERY).addEventListener('change', () => { if (choice === THEME.SYSTEM) apply(); });
}

/** Settings ▸ Theme: one of THEME. */
export function setTheme(theme) {
  choice = theme;
  try { localStorage.setItem(STORAGE_KEY.THEME, theme); } catch (e) { /* storage unavailable */ }
  apply();
}

function apply() {
  const dark = choice === THEME.DARK || (choice === THEME.SYSTEM && matchMedia(DARK_QUERY).matches);
  document.documentElement.dataset[DATA_THEME] = dark ? THEME.DARK : THEME.LIGHT;
  for (const [theme, id] of Object.entries(MENU_ENTRIES)) $(id).classList.toggle(CLS.CHECKED, theme === choice);
}
