/**
 * Zooming the drawn views without zooming the browser: the graph, the model and the two models of
 * the comparison are SVGs, so the zoom scales the picture and leaves its {@code viewBox} alone —
 * the panel then scrolls a larger drawing, and the text stays as crisp as the vectors.
 *
 * The level belongs to the tab, so two files can be read at different sizes; it is not remembered
 * across sessions, being a way of looking at what is on screen rather than a setting.
 */
import { ZOOM } from './constants.js';
import { $ } from './dom.js';
import { ID } from './dom-names.js';
import { session } from './state.js';

const PERCENT = 100;
const SVG_TAG = 'svg';
const VIEWBOX = 'viewBox';

const levelOf = (st) => st.zoom || ZOOM.DEFAULT;

/** The next level in {@code direction} (+1 in, -1 out), stopping at the ends. */
function step(direction) {
  const st = session.active;
  const i = ZOOM.STEPS.indexOf(levelOf(st));
  const next = Math.min(ZOOM.STEPS.length - 1, Math.max(0, (i < 0 ? ZOOM.STEPS.indexOf(ZOOM.DEFAULT) : i) + direction));
  st.zoom = ZOOM.STEPS[next];
  applyZoom();
}

export const zoomIn = () => step(1);
export const zoomOut = () => step(-1);
export const zoomReset = () => { session.active.zoom = ZOOM.DEFAULT; applyZoom(); };

/**
 * Draws the shown view at the tab's level: every SVG of the main area is given the size of its
 * {@code viewBox} times the level. Called whenever a view is drawn, since drawing writes a new SVG.
 */
export function applyZoom() {
  const level = levelOf(session.active);
  for (const svg of $(ID.MAIN).querySelectorAll(SVG_TAG)) {
    const box = svg.getAttribute(VIEWBOX);
    if (!box) continue;
    const [, , w, h] = box.split(' ').map(Number);
    svg.setAttribute('width', Math.round(w * level));
    svg.setAttribute('height', Math.round(h * level));
  }
  $(ID.ZOOM_LEVEL).textContent = Math.round(level * PERCENT) + '%';
  $(ID.ZOOM_IN).disabled = level === ZOOM.STEPS[ZOOM.STEPS.length - 1];
  $(ID.ZOOM_OUT).disabled = level === ZOOM.STEPS[0];
}
