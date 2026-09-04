/**
 * The widths of the side panels: a splitter between the sidebar and the main area, another between
 * the main area and the details panel. Dragging one (or the arrow keys when it has the focus) sets
 * the panel's width, remembered across sessions; a double-click restores the default. The views are
 * laid out for the room they have, so {@code initPanels} is given what redraws the shown one.
 */
import { KEY, PANEL, STORAGE_KEY } from './constants.js';
import { $, CLS, ID } from './dom.js';

/** The two splitters: the panel each one sizes, on which side, and where its width is kept. */
const SPLITTERS = [
  { id: ID.SIDEBAR_SPLITTER, panel: ID.SIDEBAR, key: STORAGE_KEY.SIDEBAR_WIDTH, fromLeft: true },
  { id: ID.DETAILS_SPLITTER, panel: ID.DETAILS, key: STORAGE_KEY.DETAILS_WIDTH, fromLeft: false },
];

/** A width within what the window can show: never narrower than PANEL.MIN_WIDTH, never more than a share of the window. */
const clamp = (width) => Math.max(PANEL.MIN_WIDTH, Math.min(width, Math.round(window.innerWidth * PANEL.MAX_SHARE)));

function setWidth(splitter, width, remember = true) {
  const panel = $(splitter.panel);
  panel.style.width = clamp(width) + 'px';
  if (!remember) return;
  try { localStorage.setItem(splitter.key, String(clamp(width))); } catch (e) { /* storage unavailable */ }
}

/** Back to the width of the stylesheet (a double-click on the splitter). */
function resetWidth(splitter) {
  $(splitter.panel).style.width = '';
  try { localStorage.removeItem(splitter.key); } catch (e) { /* storage unavailable */ }
}

/** Applies the remembered widths and wires the splitters (drag, double-click, arrow keys); {@code redraw} is called once a width has changed. */
export function initPanels(redraw) {
  for (const splitter of SPLITTERS) {
    let stored = null;
    try { stored = localStorage.getItem(splitter.key); } catch (e) { /* storage unavailable */ }
    if (stored) setWidth(splitter, +stored, false);
    const el = $(splitter.id);
    el.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      el.setPointerCapture(e.pointerId);
      el.classList.add(CLS.DRAGGING);
      const startX = e.clientX, startWidth = $(splitter.panel).getBoundingClientRect().width;
      const move = (ev) => setWidth(splitter, startWidth + (splitter.fromLeft ? ev.clientX - startX : startX - ev.clientX));
      const stop = () => {
        el.removeEventListener('pointermove', move);
        el.classList.remove(CLS.DRAGGING);
        redraw();
      };
      el.addEventListener('pointermove', move);
      el.addEventListener('pointerup', stop, { once: true });
      el.addEventListener('pointercancel', stop, { once: true });
    });
    el.addEventListener('dblclick', () => { resetWidth(splitter); redraw(); });
    el.addEventListener('keydown', (e) => {
      const step = e.key === KEY.ARROW_LEFT ? -PANEL.KEY_STEP : e.key === KEY.ARROW_RIGHT ? PANEL.KEY_STEP : 0;
      if (!step) return;
      e.preventDefault();
      setWidth(splitter, $(splitter.panel).getBoundingClientRect().width + (splitter.fromLeft ? step : -step));
      redraw();
    });
  }
}

/** Hides a splitter whose panel is not shown (a comparison or a validation takes the whole page; the details panel can be collapsed). */
export function updateSplitters() {
  for (const splitter of SPLITTERS) {
    const panel = $(splitter.panel);
    const shown = !panel.classList.contains(CLS.HIDDEN) && !panel.classList.contains(CLS.COLLAPSED);
    $(splitter.id).classList.toggle(CLS.HIDDEN, !shown);
  }
}
