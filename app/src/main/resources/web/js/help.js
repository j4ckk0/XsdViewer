/**
 * Help ▸ User guide and Help ▸ Keyboard shortcuts: two modal dialogs whose content is built here from
 * the translated texts, so the guide reads in the page's language. The keystrokes themselves stay
 * literal (Ctrl, Alt, the arrows); only what each does is translated.
 */
import { $ } from './dom.js';
import { ID } from './dom-names.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';

/** One section of the guide: its heading, then its paragraph. */
const GUIDE_SECTIONS = [
  [MSG.GUIDE_VIEWS_TITLE, MSG.GUIDE_VIEWS_BODY],
  [MSG.GUIDE_WORKSPACES_TITLE, MSG.GUIDE_WORKSPACES_BODY],
  [MSG.GUIDE_COMPARE_TITLE, MSG.GUIDE_COMPARE_BODY],
  [MSG.GUIDE_VALIDATION_TITLE, MSG.GUIDE_VALIDATION_BODY],
  [MSG.GUIDE_TIPS_TITLE, MSG.GUIDE_TIPS_BODY],
];

/** One row of the shortcuts table: the keystroke (left as written) and what it does. */
const SHORTCUTS = [
  ['Ctrl + O', MSG.SHORTCUT_OPEN],
  ['Ctrl + S', MSG.SHORTCUT_SAVE],
  ['Ctrl + F', MSG.SHORTCUT_FIND],
  ['Alt + ←', MSG.SHORTCUT_BACK],
  ['Esc', MSG.SHORTCUT_ESCAPE],
  ['Tab, ← ↑ → ↓', MSG.SHORTCUT_GRAPH_MOVE],
  ['Home', MSG.SHORTCUT_GRAPH_CENTRE],
  ['Enter / Space', MSG.SHORTCUT_GRAPH_ACTIVATE],
  ['↑ ↓', MSG.SHORTCUT_PROBLEMS],
  // the keystroke of this one is built at render time: the word 'click' is translated, and i18n is ready only then
  [() => 'Ctrl + ' + t(MSG.SHORTCUT_CLICK), MSG.SHORTCUT_SELECT_WORKSPACES],
];

function fill(container, build) {
  container.textContent = '';
  build(container);
}

/** Adds a paragraph of {@code text} to {@code parent}, muted when asked. */
function paragraph(parent, text, muted) {
  const p = document.createElement('p');
  if (muted) p.className = 'muted';
  p.textContent = text;
  parent.appendChild(p);
}

export function showGuide() {
  fill($(ID.GUIDE_BODY), (body) => {
    paragraph(body, t(MSG.GUIDE_INTRO));
    for (const [title, text] of GUIDE_SECTIONS) {
      const h = document.createElement('h3');
      h.textContent = t(title);
      body.appendChild(h);
      paragraph(body, t(text));
    }
  });
  $(ID.GUIDE_DIALOG).showModal();
}

export function showShortcuts() {
  fill($(ID.SHORTCUTS_BODY), (body) => {
    const table = document.createElement('table');
    table.className = 'shortcuts';
    for (const [keys, meaning] of SHORTCUTS) {
      const row = table.insertRow();
      const key = row.insertCell();
      key.className = 'keys';
      key.textContent = typeof keys === 'function' ? keys() : keys;
      row.insertCell().textContent = t(meaning);
    }
    body.appendChild(table);
  });
  $(ID.SHORTCUTS_DIALOG).showModal();
}

export const closeGuide = () => $(ID.GUIDE_DIALOG).close();
export const closeShortcuts = () => $(ID.SHORTCUTS_DIALOG).close();
