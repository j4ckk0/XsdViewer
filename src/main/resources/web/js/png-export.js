/** ⤓ PNG: the graph (rendered from its SVG) or the text view (drawn line by line) as a PNG file. */
import { MIME, SVG_NS, VIEW } from './constants.js';
import { $, CLS, ID, selector } from './dom.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { toast } from './toast.js';

const EXPORT_SCALE = 2;        // device pixels per CSS pixel
const EXPORT_MAX_DIM = 16000;  // keep canvases within what browsers can allocate
const GRAPH_MARGIN = 24;
const FALLBACK_BACKGROUND = '#ffffff';
/** The page's background (the theme's), so that the image looks like the page. */
const background = () => getComputedStyle(document.documentElement).getPropertyValue('--bg').trim() || FALLBACK_BACKGROUND;
const FILE_EXTENSION = '.png';
const TEXT_SUFFIX = '-text';
const DEFAULT_BASENAME = 'schema';
const UNSAFE_FILE_CHARS = /[^\w.-]+/g;
const EXTENSION = /\.[^.]+$/;
const REVOKE_DELAY_MS = 10000;
/** Text export: width of the line-number gutter, its right padding, padding after the longest line. */
const LN_W = 60, LN_PAD = 12, CODE_PAD = 20;
const DEFAULT_LINE_HEIGHT = 19;
const SVG_STYLE_TAG = 'style', SVG_RECT_TAG = 'rect';

export function exportPng() {
  const st = session.active;
  if (!st.model) return;
  const base = (st.fileName || DEFAULT_BASENAME).replace(EXTENSION, '');
  if (st.view === VIEW.GRAPH) {
    if (!st.selected) { toast(t(MSG.EXPORT_SELECT_FIRST)); return; }
    const name = st.nodes.get(st.selected).name.replace(UNSAFE_FILE_CHARS, '_');
    exportGraphPng(base + '-' + name + FILE_EXTENSION);
  } else {
    exportTextPng(base + TEXT_SUFFIX + FILE_EXTENSION);
  }
}

/** All CSS of the page, embedded into the SVG so that it renders alone (classes, variables). */
function pageCss() {
  let css = '';
  for (const sheet of document.styleSheets) {
    try { for (const r of sheet.cssRules) css += r.cssText + '\n'; } catch (e) { /* cross-origin sheet: none expected */ }
  }
  return css;
}

function exportGraphPng(fileName) {
  const src = $(ID.GRAPH_CANVAS).querySelector('svg');
  if (!src) return;
  // Crop to what is drawn (the SVG itself fills the whole panel) plus a margin.
  const M = GRAPH_MARGIN, bb = src.getBBox();
  const x = Math.floor(bb.x - M), y = Math.floor(bb.y - M);
  const w = Math.ceil(bb.width + 2 * M), h = Math.ceil(bb.height + 2 * M);
  const svg = src.cloneNode(true);
  svg.setAttribute('width', w); svg.setAttribute('height', h);
  svg.setAttribute('viewBox', x + ' ' + y + ' ' + w + ' ' + h);
  const font = getComputedStyle(document.body).font;
  const style = document.createElementNS(SVG_NS, SVG_STYLE_TAG);
  style.textContent = pageCss() + '\nsvg { font: ' + font + '; }';
  svg.insertBefore(style, svg.firstChild);
  const bg = document.createElementNS(SVG_NS, SVG_RECT_TAG);
  bg.setAttribute('x', x); bg.setAttribute('y', y); bg.setAttribute('width', w); bg.setAttribute('height', h); bg.setAttribute('fill', background());
  svg.insertBefore(bg, style.nextSibling);

  const scale = Math.min(EXPORT_SCALE, EXPORT_MAX_DIM / Math.max(w, h));
  const blob = new Blob([new XMLSerializer().serializeToString(svg)], { type: MIME.SVG });
  const url = URL.createObjectURL(blob);
  const img = new Image();
  img.onload = () => {
    URL.revokeObjectURL(url);
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(w * scale); canvas.height = Math.round(h * scale);
    const ctx = canvas.getContext('2d');
    ctx.scale(scale, scale);
    ctx.drawImage(img, 0, 0, w, h);
    saveCanvas(canvas, fileName);
  };
  img.onerror = () => { URL.revokeObjectURL(url); toast(t(MSG.EXPORT_RENDER_FAILED)); };
  img.src = url;
}

function exportTextPng(fileName) {
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

function saveCanvas(canvas, fileName) {
  canvas.toBlob((blob) => {
    if (!blob) { toast(t(MSG.EXPORT_PNG_FAILED)); return; }
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), REVOKE_DELAY_MS);
  }, MIME.PNG);
}
