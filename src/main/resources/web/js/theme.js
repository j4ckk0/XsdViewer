/**
 * The theme of the page: light or dark. The system's setting (followed as it changes) until the
 * user flips it with the single Settings entry — "Dark theme" while light, "Light theme" while
 * dark —, then the choice is remembered by the browser. It becomes data-theme="light" / "dark" on
 * <html>, which the stylesheet's palette reads; js/theme-boot.js stamps it before anything is drawn.
 */
import { STORAGE_KEY, THEME } from './constants.js';
import { $, ID } from './dom.js';
import { translate } from './i18n.js';
import { MSG } from './message-keys.js';

const DATA_THEME = 'theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';
const I18N_ATTRIBUTE = 'data-i18n';

/** The user's choice, or null while the system's setting is followed. */
let choice = null;

/** Restores the remembered choice; follows the system's setting while there is none. */
export function initTheme() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY.THEME);
    if (Object.values(THEME).includes(stored)) choice = stored;
  } catch (e) { /* storage unavailable */ }
  apply();
  matchMedia(DARK_QUERY).addEventListener('change', () => { if (!choice) apply(); });
}

/** True while the page is dark. */
const isDark = () => document.documentElement.dataset[DATA_THEME] === THEME.DARK;

/** Settings ▸ Dark theme / Light theme: the other one. */
export function toggleTheme() {
  choice = isDark() ? THEME.LIGHT : THEME.DARK;
  try { localStorage.setItem(STORAGE_KEY.THEME, choice); } catch (e) { /* storage unavailable */ }
  apply();
}

function apply() {
  const dark = choice ? choice === THEME.DARK : matchMedia(DARK_QUERY).matches;
  document.documentElement.dataset[DATA_THEME] = dark ? THEME.DARK : THEME.LIGHT;
  // the entry names the theme it switches to; bound to a text key so that a language change keeps it right
  const entry = $(ID.MENU_THEME);
  entry.querySelector('[' + I18N_ATTRIBUTE + ']').setAttribute(I18N_ATTRIBUTE, dark ? MSG.MENU_THEME_LIGHT : MSG.MENU_THEME_DARK);
  translate(entry);
}
