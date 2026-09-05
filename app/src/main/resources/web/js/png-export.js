/**
 * ⤓ PNG and ⤓ SVG of the views that are drawings: the graph, the model, and the two drawings of the
 * comparison side by side. Each is cropped to what it holds and carries the page's styles and
 * background, so the file renders on its own; the PNG is that SVG rasterised.
 * The Text view is not a drawing: {@link text-export.js} paints it.
 */
import { MIME, SVG_NS, VIEW, nameOfId } from './constants.js';
import { $, selector } from './dom.js';
import { CLS, ID } from './dom-names.js';
import { comparedPair } from './comparison-state.js';
import { saveBlob, saveCanvas } from './file-download.js';
import { exportTextPng } from './text-export.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { stampTheme } from './theme.js';
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
/** The compare view exports three pictures: each side alone, then both together. */
const LEFT_SUFFIX = '-left', RIGHT_SUFFIX = '-right';
/** The comparison as one picture: the gap between its two drawings, and the room the heading of each takes above it. */
const COMPARE_GAP = 48, COMPARE_HEAD_H = 26, COMPARE_HEAD_BASELINE = 9;
const FALLBACK_TEXT = '#000000';
/** The page's text colour, for the heading written above each model. */
const textColour = () => getComputedStyle(document.documentElement).getPropertyValue('--text').trim() || FALLBACK_TEXT;
const SVG_TEXT_TAG = 'text', SVG_GROUP_TAG = 'g', SVG_TAG = 'svg';
const DEFAULT_BASENAME = 'schema';
const UNSAFE_FILE_CHARS = /[^\w.-]+/g;
const EXTENSION = /\.[^.]+$/;
const SVG_STYLE_TAG = 'style', SVG_RECT_TAG = 'rect';

export function exportPng() {
  const st = session.active;
  if (session.comparison.shown) { for (const p of comparePictures(FILE_EXTENSION)) exportImage(p.picture, p.name); return; }
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
  if (session.comparison.shown) { for (const p of comparePictures(SVG_EXTENSION)) saveSvg(p.picture, p.name); return; }
  if (!st.model || st.view === VIEW.TEXT) return;
  if (!st.selected) { toast(t(MSG.EXPORT_SELECT_FIRST)); return; }
  const base = (st.fileName || DEFAULT_BASENAME).replace(EXTENSION, '');
  const name = st.nodes.get(st.selected).name.replace(UNSAFE_FILE_CHARS, '_');
  saveSvg(graphSvg(), base + '-' + name + (st.view === VIEW.MODEL ? MODEL_SUFFIX : '') + SVG_EXTENSION);
}

/** A marked declaration's name, made safe for a file name. */
const sideName = (mark) => nameOfId(mark.id).replace(UNSAFE_FILE_CHARS, '_');

/** The file the comparison exports to: the two declarations it draws. */
function comparedName() {
  const pair = comparedPair();
  return pair ? pair.map(sideName).join('-') : DEFAULT_BASENAME;
}

/** The panes of the comparison: the left, the right, each a canvas and the heading naming it. */
const LEFT_PANE = [ID.OBJECT_COMPARE_LEFT, ID.OBJECT_COMPARE_LEFT_NAME];
const RIGHT_PANE = [ID.OBJECT_COMPARE_RIGHT, ID.OBJECT_COMPARE_RIGHT_NAME];

/**
 * The three pictures the compare view exports, each with its file name (extension {@code ext}): the
 * left declaration alone, the right alone, then the two side by side as before. A picture is left
 * out when its pane draws nothing (the text view, or a side not yet marked).
 */
function comparePictures(ext) {
  const pair = comparedPair();
  const left = pair ? sideName(pair[0]) : DEFAULT_BASENAME, right = pair ? sideName(pair[1]) : DEFAULT_BASENAME;
  return [
    { picture: compareSvg([LEFT_PANE]), name: left + COMPARE_SUFFIX + LEFT_SUFFIX + ext },
    { picture: compareSvg([RIGHT_PANE]), name: right + COMPARE_SUFFIX + RIGHT_SUFFIX + ext },
    { picture: compareSvg([LEFT_PANE, RIGHT_PANE]), name: comparedName() + COMPARE_SUFFIX + ext },
  ].filter(p => p.picture);
}

/**
 * The given panes of the comparison as one picture — the content models, or the neighbourhoods: each
 * cropped to what it draws, side by side when there are two, under the heading its pane carries, so
 * the image says which side is which. Null when any asked pane draws nothing (the text view, or a
 * side not yet marked).
 */
function compareSvg(panes) {
  const sides = panes
    .map(([canvas, head]) => ({ src: $(canvas).querySelector(SVG_TAG), head: $(head).textContent }));
  if (sides.some(side => !side.src)) return null;
  for (const side of sides) {
    const bb = side.src.getBBox();
    side.x = Math.floor(bb.x - GRAPH_MARGIN); side.y = Math.floor(bb.y - GRAPH_MARGIN);
    side.w = Math.ceil(bb.width + 2 * GRAPH_MARGIN); side.h = Math.ceil(bb.height + 2 * GRAPH_MARGIN);
  }
  const w = sides.reduce((sum, side) => sum + side.w, 0) + COMPARE_GAP * (sides.length - 1);
  const h = Math.max(...sides.map(side => side.h)) + COMPARE_HEAD_H;
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
  // the handles to the other view are the page's, not the picture's
  svg.querySelectorAll(selector(CLS.NODE_TO_MODEL) + ', ' + selector(CLS.MODEL_TO_GRAPH)).forEach(el => el.remove());
  svg.setAttribute('width', w); svg.setAttribute('height', h);
  svg.setAttribute('viewBox', x + ' ' + y + ' ' + w + ' ' + h);
  return { svg: standalone(svg, x, y, w, h), w, h };
}

/**
 * The page's styles and its background put under {@code svg}, so that the file renders on its own — and
 * the page's theme on its root, which is what the dark palette of those styles is keyed on.
 */
function standalone(svg, x, y, w, h) {
  stampTheme(svg);
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

