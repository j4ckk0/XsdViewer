/**
 * A part of the page that folds to its title line and unfolds back — the details panel, the schema
 * header, the Files panel, the Compare group — with a button whose glyph and tooltip say which way
 * it goes next, and the state remembered in the browser across sessions.
 */
import { STORAGE_FALSE, STORAGE_TRUE } from './constants.js';
import { $ } from './dom.js';
import { CLS } from './dom-names.js';
import { t } from './i18n.js';

/** The glyphs of a group folding to its title line, and of a panel folding to a strip at the side. */
export const GLYPHS = { GROUP: { fold: '▾', unfold: '▸' }, PANEL: { fold: '»', unfold: '«' } };

/**
 * @param element         id of what folds (it wears {@code CLS.COLLAPSED} while folded)
 * @param toggle          id of its button
 * @param storageKey      where the state is remembered
 * @param titles          {fold, unfold}: the message keys of the button's tooltip, for each way it goes next
 * @param glyphs          one of {@link GLYPHS}
 * @param defaultFolded   the state before the user ever touched it
 * @param onChange        called after the state is applied, for what else has to follow (a splitter, say)
 */
export function foldable({ element, toggle, storageKey, titles, glyphs = GLYPHS.GROUP, defaultFolded = false, onChange = null }) {
  const isFolded = () => $(element).classList.contains(CLS.COLLAPSED);
  const set = (folded) => {
    $(element).classList.toggle(CLS.COLLAPSED, folded);
    const button = $(toggle);
    button.textContent = folded ? glyphs.unfold : glyphs.fold;
    button.title = t(folded ? titles.unfold : titles.fold);
    if (onChange) onChange();
    try { localStorage.setItem(storageKey, folded ? STORAGE_TRUE : STORAGE_FALSE); } catch (e) { /* storage unavailable */ }
  };
  return {
    set,
    isFolded,
    toggle: () => set(!isFolded()),
    /** The button's tooltip again, in the language now chosen. */
    refresh: () => set(isFolded()),
    /** Restores the state remembered in the browser, else the default. */
    init() {
      let folded = defaultFolded;
      try {
        const stored = localStorage.getItem(storageKey);
        if (stored !== null) folded = stored === STORAGE_TRUE;
      } catch (e) { /* storage unavailable */ }
      set(folded);
    },
  };
}
