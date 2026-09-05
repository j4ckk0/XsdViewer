/**
 * The find bar of the Text view: the lines holding a text are marked, Enter / Shift+Enter walk them
 * (the current one scrolled into view), a count says where. Ctrl+F in the Text view focuses it.
 */
import { $, CLS, ID, selector } from './dom.js';

let found = [];
let current = -1;

/** Marks the lines holding the bar's text again (the text or the file changed). */
export function refreshFind() {
  const typed = $(ID.TEXT_FIND_INPUT).value;
  const query = typed.trim() ? typed.toLowerCase() : '';   // spaces count (a blank query does not)
  const lines = [...$(ID.TEXT).querySelectorAll(selector(CLS.LINE))];
  for (const l of lines) l.classList.remove(CLS.FOUND, CLS.CURRENT);
  found = query ? lines.filter(l => l.querySelector(selector(CLS.CODE)).textContent.toLowerCase().includes(query)) : [];
  for (const l of found) l.classList.add(CLS.FOUND);
  current = found.length ? 0 : -1;
  show(true);
}

function show(scroll) {
  const typed = !!$(ID.TEXT_FIND_INPUT).value.trim();
  $(ID.TEXT_FIND_COUNT).textContent = found.length ? (current + 1) + '/' + found.length : typed ? '0' : '';
  found.forEach((l, i) => l.classList.toggle(CLS.CURRENT, i === current));
  if (scroll && current >= 0) found[current].scrollIntoView({ block: 'center' });
}

/** The next ({@code step} 1) or previous (-1) line found, round and round. */
export function findStep(step) {
  if (!found.length) return;
  current = (current + step + found.length) % found.length;
  show(true);
}

export function clearFind() {
  $(ID.TEXT_FIND_INPUT).value = '';
  refreshFind();
}

export function focusFind() {
  const input = $(ID.TEXT_FIND_INPUT);
  input.focus();
  input.select();
}
