/** Reaching into the page and writing HTML for it: the element of an id, escaping, a data attribute, a selector, a legend. The names themselves are in {@code dom-names.js}. */
import { CLS } from './dom-names.js';

export const $ = (id) => document.getElementById(id);

/** HTML-escapes a value for insertion in markup. */
export const esc = (s) => String(s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export const dataAttr = (name, value) => ' data-' + name + '="' + esc(value) + '"';
/** A legend: one chip per [class, text] pair, the class colouring it. */
export const legendHtml = (entries) =>
  entries.map(([cls, text]) => '<span class="' + CLS.LEGEND_ENTRY + ' ' + cls + '">' + esc(text) + '</span>').join('');
export const selector = (cls) => '.' + cls;
