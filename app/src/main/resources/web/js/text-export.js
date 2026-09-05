/**
 * The Text view as a PNG file: the source is HTML, not a drawing, so it is painted line by line onto
 * a canvas — the line numbers, the highlighted line's background, and each token in the colour the
 * page gives it. A source too tall for one image is cut to what the reader has in front of them.
 */
import { $, selector } from './dom.js';
import { CLS, ID } from './dom-names.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { saveCanvas } from './file-download.js';
import { toast } from './toast.js';

/** Device pixels per CSS pixel, and the largest canvas a browser will allocate. */
const EXPORT_SCALE = 2, EXPORT_MAX_DIM = 16000;
const FALLBACK_BACKGROUND = '#ffffff';
/** The page's background (the theme's), so that the image looks like the page. */
const background = () => getComputedStyle(document.documentElement).getPropertyValue('--bg').trim() || FALLBACK_BACKGROUND;
/** Width of the line-number gutter, its right padding, the padding after the longest line. */
const LN_W = 60, LN_PAD = 12, CODE_PAD = 20;
const DEFAULT_LINE_HEIGHT = 19;

export function exportTextPng(fileName) {
  const container = $(ID.TEXT);
  const lines = [...container.querySelectorAll(selector(CLS.LINE))];
  if (!lines.length) return;
  const cs = getComputedStyle(container);
  const font = cs.font;
  const lineH = lines[0].getBoundingClientRect().height || DEFAULT_LINE_HEIGHT;
  const padTop = parseFloat(cs.paddingTop) || 0;
  const colorOf = (el) => getComputedStyle(el).color;
  const bgOf = (el) => getComputedStyle(el).backgroundColor;

  // Text lines: measure first.
  const meas = document.createElement('canvas').getContext('2d');
  meas.font = font;
  let maxCode = 0;
  for (const l of lines) maxCode = Math.max(maxCode, meas.measureText(l.querySelector(selector(CLS.CODE)).textContent).width);
  const w = Math.ceil(LN_W + maxCode + CODE_PAD);

  // Height cap: fall back to the lines from the current scroll position.
  const maxLines = Math.floor((EXPORT_MAX_DIM / EXPORT_SCALE - 2 * padTop) / lineH);
  let first = 0, count = lines.length;
  if (count > maxLines) {
    first = Math.min(Math.floor(container.scrollTop / lineH), count - maxLines);
    count = maxLines;
    toast(t(MSG.EXPORT_TEXT_TRUNCATED, first + 1, first + count));
  }
  const h = Math.ceil(count * lineH + 2 * padTop);
  const scale = Math.min(EXPORT_SCALE, EXPORT_MAX_DIM / Math.max(w, h));

  const canvas = document.createElement('canvas');
  canvas.width = Math.round(w * scale); canvas.height = Math.round(h * scale);
  const ctx = canvas.getContext('2d');
  ctx.scale(scale, scale);
  ctx.fillStyle = background();
  ctx.fillRect(0, 0, w, h);
  ctx.font = font;
  ctx.textBaseline = 'middle';

  const colorCache = new Map();
  const spanColor = (el) => {
    const k = el.className || '';
    if (!colorCache.has(k)) colorCache.set(k, colorOf(el));
    return colorCache.get(k);
  };
  for (let i = 0; i < count; i++) {
    const l = lines[first + i];
    const y = padTop + i * lineH, ym = y + lineH / 2;
    if (l.classList.contains(CLS.LINE_HIGHLIGHT)) { ctx.fillStyle = bgOf(l); ctx.fillRect(0, y, w, lineH); }
    const ln = l.querySelector(selector(CLS.LINE_NUMBER));
    ctx.textAlign = 'right';
    ctx.fillStyle = colorOf(ln);
    ctx.fillText(ln.textContent, LN_W - LN_PAD, ym);
    ctx.textAlign = 'left';
    let x = LN_W;
    const code = l.querySelector(selector(CLS.CODE));
    const codeColor = colorOf(code);
    for (const node of code.childNodes) {
      const s = node.textContent;
      if (!s) continue;
      ctx.fillStyle = node.nodeType === Node.ELEMENT_NODE ? spanColor(node) : codeColor;
      ctx.fillText(s, x, ym);
      x += ctx.measureText(s).width;
    }
  }
  saveCanvas(canvas, fileName);
}
