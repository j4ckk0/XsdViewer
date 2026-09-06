/**
 * The comparison's Text view as pieces of picture: its two sides are HTML tables, not drawings, so
 * each is rebuilt in SVG line by line — the line numbers, the text of each line, the background of a
 * deleted, inserted or moved line, the rows folding identical lines — in the colours and the font the
 * page gives them, so that {@code png-export.js} exports it as it does a model or a graph.
 */
import { SVG_NS } from './constants.js';
import { $, selector } from './dom.js';
import { CLS, ID } from './dom-names.js';

const SVG_GROUP_TAG = 'g', SVG_RECT_TAG = 'rect', SVG_TEXT_TAG = 'text';
const XML_NS = 'http://www.w3.org/XML/1998/namespace';
/** The padding of a cell, the rule between the numbers and the lines, the inset of a fold's text: the table's own. */
const CELL_PAD = 6, RULE_W = 1, FOLD_INSET = 24;
const DEFAULT_LINE_HEIGHT = 17;
/** A tab has no width of its own in a picture. */
const TAB = '\t', TAB_SPACES = '    ';
const TRANSPARENT = /^rgba\(.*,\s*0\)$|^transparent$/;
/** Where a baseline sits in a line of the font's size, from the line's middle. */
const BASELINE_RATIO = 0.35;
/** A coordinate written with two decimals: the row heights the page computes are not round. */
const at = (v) => Math.round(v * 100) / 100;

/**
 * The lines of side {@code index} of the comparison's text — 0 the left, 1 the right — as {@code {nodes,
 * extent}}: an SVG group and the box it covers ({@code {x, y, width, height}}), or null when that side
 * shows no lines (a declaration without source, a text too large to compare).
 */
export function textDiffPicture(index) {
  const table = $(ID.OBJECT_COMPARE_TEXT).querySelectorAll(selector(CLS.DIFF_SIDE) + ' table')[index];
  const rows = table ? [...table.rows] : [];
  if (!rows.length) return null;
  const font = getComputedStyle(table).font;
  const fontSize = parseFloat(getComputedStyle(table).fontSize) || 0;
  const lineH = rows[0].getBoundingClientRect().height || DEFAULT_LINE_HEIGHT;
  const measure = document.createElement('canvas').getContext('2d');
  measure.font = font;
  const textOf = (cell) => cell.textContent.replaceAll(TAB, TAB_SPACES);
  const widthOf = (cell) => (cell ? measure.measureText(textOf(cell)).width : 0);
  const isFold = (row) => row.classList.contains(CLS.FOLD);
  const numberCell = (row) => (isFold(row) ? null : row.cells[0]), codeCell = (row) => (isFold(row) ? null : row.cells[1]);
  const gutter = Math.ceil(Math.max(...rows.map(row => widthOf(numberCell(row))))) + 2 * CELL_PAD;
  const width = gutter + RULE_W + Math.ceil(Math.max(...rows.map(row => widthOf(codeCell(row))))) + 2 * CELL_PAD;
  const height = rows.length * lineH;

  const g = document.createElementNS(SVG_NS, SVG_GROUP_TAG);
  g.setAttribute('style', 'font: ' + font + '; white-space: pre');
  const rect = (x, y, w, h, fill) => {
    const r = document.createElementNS(SVG_NS, SVG_RECT_TAG);
    r.setAttribute('x', at(x)); r.setAttribute('y', at(y)); r.setAttribute('width', at(w)); r.setAttribute('height', at(h)); r.setAttribute('fill', fill);
    g.append(r);
  };
  const text = (x, y, content, style, anchor) => {
    const el = document.createElementNS(SVG_NS, SVG_TEXT_TAG);
    el.setAttribute('x', at(x)); el.setAttribute('y', at(y));
    el.setAttribute('fill', style.color);
    if (style.fontStyle !== 'normal') el.setAttribute('font-style', style.fontStyle);
    if (anchor) el.setAttribute('text-anchor', anchor);
    el.setAttributeNS(XML_NS, 'xml:space', 'preserve');   // the indentation of the source is its shape
    el.textContent = content;
    g.append(el);
  };
  const background = (cell, x, y, w) => {
    const fill = getComputedStyle(cell).backgroundColor;
    if (fill && !TRANSPARENT.test(fill)) rect(x, y, w, lineH, fill);
  };
  const numbered = rows.find(row => !isFold(row)) || rows[0];
  const rule = getComputedStyle(numbered.cells[0]).borderRightColor;
  rows.forEach((row, i) => {
    const y = i * lineH, baseline = y + lineH / 2 + fontSize * BASELINE_RATIO;
    if (isFold(row)) {
      const cell = row.cells[0];
      background(cell, 0, y, width);
      text(FOLD_INSET, baseline, textOf(cell), getComputedStyle(cell));
      return;
    }
    const number = numberCell(row), code = codeCell(row);
    background(number, 0, y, gutter);
    background(code, gutter + RULE_W, y, width - gutter - RULE_W);
    text(gutter - CELL_PAD, baseline, textOf(number), getComputedStyle(number), 'end');
    text(gutter + RULE_W + CELL_PAD, baseline, textOf(code), getComputedStyle(code));
  });
  rect(gutter, 0, RULE_W, height, rule);
  return { nodes: [g], extent: { x: 0, y: 0, width, height } };
}
