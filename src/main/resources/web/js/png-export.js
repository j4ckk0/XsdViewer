/**
 * ⤓ PNG: the graph, the model, or the two models of the Compare view (rendered from their SVG), or
 * the text view (drawn line by line), as a PNG file; ⤓ SVG: the same, as vectors.
 */
import { MIME, SVG_NS, VIEW, nameOfId } from './constants.js';
import { $, CLS, ID, selector } from './dom.js';
import { comparedPair } from './object-compare.js';
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
const SVG_EXTENSION = '.svg';
const XML_DECLARATION = '<?xml version="1.0" encoding="UTF-8"?>\n';
const TEXT_SUFFIX = '-text', MODEL_SUFFIX = '-model', COMPARE_SUFFIX = '-compared';
/** The Compare view as one picture: the gap between the two models, and the room the heading of each takes above it. */
const COMPARE_GAP = 48, COMPARE_HEAD_H = 26, COMPARE_HEAD_BASELINE = 9;
const FALLBACK_TEXT = '#000000';
/** The page's text colour, for the heading written above each model. */
const textColour = () => getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || FALLBACK_TEXT;
const SVG_TEXT_TAG = 'text', SVG_GROUP_TAG = 'g', SVG_TAG = 'svg';
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
  if (st.view === VIEW.COMPARE) { exportImage(compareSvg(), comparedName() + COMPARE_SUFFIX + FILE_EXTENSION); return; }
  if (!st.model) return;
  const base = (st.fileName || DEFAULT_BASENAME).replace(EXTENSION, '');
  if (st.view === VIEW.GRAPH || st.view === VIEW.MODEL) {
    if (!st.selected) { toast(t(MSG.EXPORT_SELECT_FIRST)); return; }
    const name = st.nodes.get(st.selected).name.replace(UNSAFE_FILE_CHARS, '_');
    exportImage(graphSvg(), base + '-' + name + (st.view === VIEW.MODEL ? MODEL_SUFFIX : '') + FILE_EXTENSION);
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

/** ⤓ SVG: the graph as a vector image (its SVG, cropped, with the page's styles embedded). */
export function exportSvg() {
  const st = session.active;
  if (st.view === VIEW.COMPARE) { saveSvg(compareSvg(), comparedName() + COMPARE_SUFFIX + SVG_EXTENSION); return; }
  if (!st.model || st.view === VIEW.TEXT) return;
  if (!st.selected) { toast(t(MSG.EXPORT_SELECT_FIRST)); return; }
  const base = (st.fileName || DEFAULT_BASENAME).replace(EXTENSION, '');
  const name = st.nodes.get(st.selected).name.replace(UNSAFE_FILE_CHARS, '_');
  saveSvg(graphSvg(), base + '-' + name + (st.view === VIEW.MODEL ? MODEL_SUFFIX : '') + SVG_EXTENSION);
}

/** The file the Compare view exports to: the two declarations it draws. */
function comparedName() {
  const pair = comparedPair();
  return pair ? pair.map(m => nameOfId(m.id).replace(UNSAFE_FILE_CHARS, '_')).join('-') : DEFAULT_BASENAME;
}

/**
 * The two models of the Compare view as one picture: each cropped to what it draws, side by side,
 * under the heading its pane carries, so the image says which side is which. Null when either is missing.
 */
function compareSvg() {
  const sides = [[ID.OBJECT_COMPARE_LEFT, ID.OBJECT_COMPARE_LEFT_NAME], [ID.OBJECT_COMPARE_RIGHT, ID.OBJECT_COMPARE_RIGHT_NAME]]
    .map(([canvas, head]) => ({ src: $(canvas).querySelector(SVG_TAG), head: $(head).textContent }));
  if (sides.some(side => !side.src)) return null;
  for (const side of sides) {
    const bb = side.src.getBBox();
    side.x = Math.floor(bb.x - GRAPH_MARGIN); side.y = Math.floor(bb.y - GRAPH_MARGIN);
    side.w = Math.ceil(bb.width + 2 * GRAPH_MARGIN); side.h = Math.ceil(bb.height + 2 * GRAPH_MARGIN);
  }
  const w = sides[0].w + COMPARE_GAP + sides[1].w, h = Math.max(sides[0].h, sides[1].h) + COMPARE_HEAD_H;
  const svg = document.createElementNS(SVG_NS, SVG_TAG);
  svg.setAttribute('xmlns', SVG_NS);
  svg.setAttribute('width', w); svg.setAttribute('height', h);
  svg.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
  standalone(svg, 0, 0, w, h);
  let dx = 0;
  for (const side of sides) {
    const g = document.createElementNS(SVG_NS, SVG_GROUP_TAG);
    g.setAttribute('transform', 'translate(' + (dx - side.x) + ',' + (COMPARE_HEAD_H - side.y) + ')');
    for (const child of side.src.childNodes) g.append(child.cloneNode(true));
    const label = document.createElementNS(SVG_NS, SVG_TEXT_TAG);
    label.setAttribute('x', dx + GRAPH_MARGIN); label.setAttribute('y', COMPARE_HEAD_H - COMPARE_HEAD_BASELINE);
    label.setAttribute('fill', textColour()); label.setAttribute('font-weight', '600');
    label.textContent = side.head;
    svg.append(g, label);
    dx += side.w + COMPARE_GAP;
  }
  return { svg, w, h };
}

/** Writes an SVG picture to a file. */
function saveSvg(picture, fileName) {
  if (!picture) return;
  saveBlob(new Blob([XML_DECLARATION + new XMLSerializer().serializeToString(picture.svg)], { type: MIME.SVG }), fileName);
}

/** The shown view's SVG (the graph's, or the model's) cropped to what is drawn plus a margin, the page's CSS and background embedded so that it renders alone: {svg, w, h}, or null. */
function graphSvg() {
  const src = $(session.active.view === VIEW.MODEL ? ID.MODEL_CANVAS : ID.GRAPH_CANVAS).querySelector('svg');
  if (!src) return null;
  const M = GRAPH_MARGIN, bb = src.getBBox();
  const x = Math.floor(bb.x - M), y = Math.floor(bb.y - M);
  const w = Math.ceil(bb.width + 2 * M), h = Math.ceil(bb.height + 2 * M);
  const svg = src.cloneNode(true);
  svg.setAttribute('width', w); svg.setAttribute('height', h);
  svg.setAttribute('viewBox', x + ' ' + y + ' ' + w + ' ' + h);
  return { svg: standalone(svg, x, y, w, h), w, h };
}

/** The page's styles and its background put under {@code svg}, so that the file renders on its own. */
function standalone(svg, x, y, w, h) {
  const bg = document.createElementNS(SVG_NS, SVG_RECT_TAG);
  bg.setAttribute('x', x); bg.setAttribute('y', y); bg.setAttribute('width', w); bg.setAttribute('height', h);
  bg.setAttribute('fill', background());
  svg.insertBefore(bg, svg.firstChild);
  const style = document.createElementNS(SVG_NS, SVG_STYLE_TAG);
  style.textContent = pageCss() + '\nsvg { font: ' + getComputedStyle(document.body).font + '; }';
  svg.insertBefore(style, svg.firstChild);
  return svg;
}

/** Renders an SVG picture into a PNG file. */
function exportImage(picture, fileName) {
  if (!picture) return;
  const { svg, w, h } = picture;
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
    saveBlob(blob, fileName);
  }, MIME.PNG);
}

/** Hands a file to the browser to save. */
function saveBlob(blob, fileName) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), REVOKE_DELAY_MS);
}
