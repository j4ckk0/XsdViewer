/**
 * The graph view: the selected object in the centre, what it links to on the right (one arrow per
 * link, its name and cardinality above the target, dashed when optional, a hollow arrowhead for
 * a derivation from a base type), what uses it on the
 * left, and optionally the targets' own links as a second level on the right (an object expanded once).
 */
import { ID_SEPARATOR, LINK_LABEL, NODE_KIND, STRUCTURAL_LINK_LABELS, SVG_NS, TEXT, WSDL_KINDS, isDerivation } from './constants.js';
import { findInWorkspace, kindsOf, placeAttributes, usersInWorkspace } from './declarations.js';
import { cardinalityText, isOptional } from './cardinality.js';
import { $, CLS, DATA, ID, SVG_ID, dataAttr, esc } from './dom.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

// ---- layout ----
export const NODE_W = 200, NODE_H = 36;
const ROW = 60, MARGIN = 24, MIN_GAP = 90;
const SELF_LOOP_HEIGHT = 60, SELF_LOOP_ROOM = 40, SELF_LOOP_SPREAD = 30, SELF_LOOP_CONTROL_SPREAD = 50, SELF_LABEL_LIFT = 45;
const LABEL_MAX_CHARS = 40, LABEL_CHAR_W = 6.2, LABEL_PAD = 8, LABEL_H = 14, LABEL_RADIUS = 3;
const NAME_MAX_CHARS_CENTER = 24, NAME_MAX_CHARS = 26, KIND_MAX_CHARS = 30, CAPTION_MAX_CHARS = 28;
/** Distance between the caption's baseline and the top of the node. */
const CAPTION_LIFT = 6;
const ELLIPSIS = '…';
/** Length in px of the hollow arrowhead (9 marker units at markerWidth 12 x stroke 1.5): a derivation's line stops at its base. */
const DERIVATION_ARROW_LENGTH = 16;

const shorten = (s, max) => (s.length > max ? s.slice(0, max - 1) + ELLIPSIS : s);

export function renderGraph() {
  const st = session.active;
  const canvas = $(ID.GRAPH_CANVAS);
  if (!st.selected) { canvas.innerHTML = ''; return; }
  const center = st.nodes.get(st.selected);
  const showBuiltins = $(ID.SHOW_BUILTINS).checked;
  const depth = $(ID.TWO_LEVELS).checked ? 2 : 1;
  $(ID.GRAPH_TITLE).textContent = t(MSG.GRAPH_NODE_TITLE, kindLabel(center.kind), center.name);
  $(ID.GRAPH_LEGEND).classList.toggle(CLS.WSDL, st.model.nodes.some(n => WSDL_KINDS.has(n.kind)));
  const visible = (n) => n && (showBuiltins || n.kind !== NODE_KIND.BUILTIN);
  const byName = (a, b) => a.n.kind.localeCompare(b.n.kind) || a.n.name.localeCompare(b.n.name) || a.edge.label.localeCompare(b.edge.label);
  const fileKind = (n, place) => ({ kindText: t(MSG.GRAPH_KIND_IN_FILE, kindLabel(n.kind), place.fileName) });
  /** One row per link: {n, edge, place} (place: the other file the node belongs to — a tab or a listed file —, or null); children: its level-2 targets. */
  const link = (n, edge, place) => ({ n, edge, place, children: [] });

  // Level 1: one row per outgoing edge on the right, per incoming edge on the left.
  const selfLabels = [];
  const right = [], left = [];
  for (const e of st.outEdges.get(center.id) || []) {
    if (e.to === center.id) { selfLabels.push(linkTitle(e)); continue; }
    const n = st.nodes.get(e.to);
    if (visible(n)) right.push(link(n, e, null));
  }
  for (const e of st.inEdges.get(center.id) || []) {
    if (e.from === center.id) continue;
    const n = st.nodes.get(e.from);
    if (visible(n)) left.push(link(n, e, null));
  }
  // Users of the centre in the other files of the workspace (open or only listed), where it appears as an external placeholder.
  for (const u of usersInWorkspace(center, st)) {
    if (visible(u.n)) for (const e of u.edges) left.push(link(u.n, e, u.place));
  }
  right.sort(byName); left.sort(byName);
  // An external target declared elsewhere in the workspace shows as what it is there (its kind, its file).
  for (const r of right) {
    if (r.n.kind !== NODE_KIND.EXTERNAL) continue;
    const found = findInWorkspace(r.n.name, kindsOf(r.n), r.n.ns || '', st);
    if (found) r.resolved = { n: found.place.nodes.get(found.id), place: found.place };
  }

  // Level 2, on the right only, drawn as trees: what each level-1 target links to. An object
  // reached by several links is expanded once, under its first copy; the other copies stay leaves.
  if (depth === 2) {
    const placeKey = (place) => (place ? (place.tab ? 't' + session.tabs.indexOf(place.tab) : 'f' + st.workspace.files.indexOf(place.entry)) : '');
    const expandedKey = (n, place) => placeKey(place) + ID_SEPARATOR + n.id;
    const expanded = new Set();
    for (const r of right) {
      // an external target declared elsewhere is expanded from there
      const src = r.resolved ? r.resolved.place : r.n.kind === NODE_KIND.EXTERNAL ? null : st;
      if (!src) continue;
      const id = r.resolved ? r.resolved.n.id : r.n.id;
      if (expanded.has(expandedKey(r.n, r.place))) continue;
      expanded.add(expandedKey(r.n, r.place));
      for (const e of src.outEdges.get(id) || []) {
        if (e.to === id) continue;
        let n = src.nodes.get(e.to), place = src === st ? null : src;
        if (n && n.kind === NODE_KIND.EXTERNAL) {   // a level-2 target declared elsewhere shows as what it is there too
          const found = findInWorkspace(n.name, kindsOf(n), n.ns || '', st);
          if (found) { n = found.place.nodes.get(found.id); place = found.place; }
        }
        if (visible(n)) r.children.push(link(n, e, place));
      }
      r.children.sort(byName);
    }
  }
  const spanR = (r) => Math.max(1, r.children.length);
  const rightRows = right.reduce((sum, r) => sum + spanR(r), 0);
  const leftRows = left.length;

  // Columns: incoming | centre | level 1 out | (level 2 out); spread over the canvas width when wider than needed.
  const cols = 2 + depth, ci = 1;   // ci: index of the centre column
  const W = Math.max(canvas.clientWidth, cols * NODE_W + (cols - 1) * MIN_GAP + 2 * MARGIN);
  const gap = (W - 2 * MARGIN - cols * NODE_W) / (cols - 1);
  const colX = (c) => MARGIN + c * (NODE_W + gap);
  const rows = Math.max(rightRows, leftRows, 1);
  const H = Math.max(canvas.clientHeight, rows * ROW + 2 * MARGIN + (selfLabels.length ? SELF_LOOP_ROOM : 0));
  const cy = H / 2;
  const cx = colX(ci) + NODE_W / 2, xLeft = colX(ci - 1), xR1 = colX(ci + 1);
  const xR2 = depth === 2 ? colX(ci + 2) : 0;
  const yOf = (i, count) => cy - ((count - 1) * ROW) / 2 + i * ROW;
  /** Drawing options of a row's node: its caption (the link) and, for a node of another file, that file. */
  const rowOpt = (row) => Object.assign({ link: row.edge }, row.place ? { place: row.place } : {}, row.place ? fileKind(row.n, row.place) : {});

  let svg = '<svg xmlns="' + SVG_NS + '" width="' + W + '" height="' + H + '" viewBox="0 0 ' + W + ' ' + H + '">'
    + '<defs><marker id="' + SVG_ID.ARROW + '" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">'
    + '<path class="' + CLS.ARROW_HEAD + '" d="M0,0 L10,5 L0,10 z"/></marker>'
    // a derivation: a hollow triangle, larger, as a UML generalisation
    + '<marker id="' + SVG_ID.DERIVATION_ARROW + '" viewBox="0 0 10 10" refX="0.5" refY="5" markerWidth="12" markerHeight="12" orient="auto">'
    + '<path class="' + CLS.ARROW_HEAD + ' ' + CLS.DERIVATION + '" d="M0.5,0.5 L9.5,5 L0.5,9.5 z"/></marker></defs>';

  const edges = [], labels = [], nodes = [];

  // outgoing: centre -> level 1 (-> level 2)
  let row = 0;
  for (const r of right) {
    const first = yOf(row, rightRows);
    const y = first + ((spanR(r) - 1) * ROW) / 2;   // a parent sits in the middle of its children
    edges.push(curve(cx + NODE_W / 2, cy, xR1, y, r.edge));
    nodes.push(r.resolved
      ? nodeSvg(r.resolved.n, xR1, y - NODE_H / 2, false, Object.assign({ id: r.n.id, link: r.edge }, fileKind(r.resolved.n, r.resolved.place)))
      : nodeSvg(r.n, xR1, y - NODE_H / 2, false, { link: r.edge }));
    r.children.forEach((c, k) => {
      const yc = first + k * ROW;
      edges.push(curve(xR1 + NODE_W, y, xR2, yc, c.edge));
      nodes.push(nodeSvg(c.n, xR2, yc - NODE_H / 2, false, rowOpt(c)));
    });
    row += spanR(r);
  }
  // incoming: level 1 -> centre, one step only
  left.forEach((l, i) => {
    const y = yOf(i, leftRows);
    edges.push(curve(xLeft + NODE_W, y, cx - NODE_W / 2, cy, l.edge));
    nodes.push(nodeSvg(l.n, xLeft, y - NODE_H / 2, false, rowOpt(l)));
  });
  // self reference (recursive type)
  if (selfLabels.length) {
    const top = cy - NODE_H / 2;
    edges.push('<path class="' + CLS.EDGE + '" marker-end="url(#' + SVG_ID.ARROW + ')" d="M' + (cx - SELF_LOOP_SPREAD) + ',' + top
      + ' C' + (cx - SELF_LOOP_CONTROL_SPREAD) + ',' + (top - SELF_LOOP_HEIGHT) + ' ' + (cx + SELF_LOOP_CONTROL_SPREAD) + ',' + (top - SELF_LOOP_HEIGHT)
      + ' ' + (cx + SELF_LOOP_SPREAD) + ',' + top + '"/>');
    labels.push(textWithBg(cx, top - SELF_LABEL_LIFT, selfLabels.join(TEXT.LIST_SEPARATOR)));
  }
  nodes.push(nodeSvg(center, cx - NODE_W / 2, cy - NODE_H / 2, true));

  svg += edges.join('') + labels.join('') + nodes.join('') + '</svg>';
  canvas.innerHTML = svg;

  // keep the centre in view
  canvas.scrollTop = Math.max(0, cy - canvas.clientHeight / 2);
  canvas.scrollLeft = Math.max(0, cx - canvas.clientWidth / 2);
}

/** A bezier arrow for {@code edge}; dashed when the link is optional, a hollow arrowhead when it is a derivation. */
function curve(x1, y1, x2, y2, edge) {
  const dx = (x2 - x1) / 2;
  const derivation = isDerivation(edge);
  if (derivation) x2 -= DERIVATION_ARROW_LENGTH;   // the hollow head, anchored at its base, fills the gap up to the node
  const cls = CLS.EDGE + (isOptional(edge) ? ' ' + CLS.OPTIONAL : '') + (derivation ? ' ' + CLS.DERIVATION : '');
  return '<path class="' + cls + '" marker-end="url(#' + (derivation ? SVG_ID.DERIVATION_ARROW : SVG_ID.ARROW) + ')" d="M' + x1 + ',' + y1
    + ' C' + (x1 + dx) + ',' + y1 + ' ' + (x2 - dx) + ',' + y2 + ' ' + x2 + ',' + y2 + '"/>';
}

/** Text in a white box, for the label of the self-reference loop. */
function textWithBg(x, y, text) {
  const shown = shorten(text, LABEL_MAX_CHARS);
  const w = shown.length * LABEL_CHAR_W + LABEL_PAD;
  return '<rect class="' + CLS.EDGE_LABEL_BG + '" x="' + (x - w / 2) + '" y="' + (y - 9) + '" width="' + w + '" height="' + LABEL_H + '" rx="' + LABEL_RADIUS + '"/>'
    + '<text class="' + CLS.EDGE_LABEL + '" x="' + x + '" y="' + (y + 2) + '" text-anchor="middle"><title>' + esc(text) + '</title>' + esc(shown) + '</text>';
}

/** The caption above a node: the link's name (an XSD word small and muted, the word "attribute" dropped) and its cardinality. */
function captionSvg(edge) {
  const label = edge.label;
  const card = cardinalityText(edge);
  const cardSvg = card ? ' <tspan class="' + CLS.CARDINALITY + '">' + esc(card) + '</tspan>' : '';
  const optional = isOptional(edge) ? ' ' + CLS.OPTIONAL : '';
  const structural = (word) => '<text class="' + CLS.LINK_NAME + ' ' + CLS.STRUCTURAL + optional + '" x="2" y="-' + CAPTION_LIFT + '">' + esc(word) + cardSvg + '</text>';
  if (label === LINK_LABEL.ATTRIBUTE_REF) return structural(LINK_LABEL.REF);
  if (STRUCTURAL_LINK_LABELS.has(label)) return structural(label);
  const name = label.startsWith(LINK_LABEL.ATTRIBUTE_PREFIX) ? label.slice(LINK_LABEL.ATTRIBUTE_PREFIX.length) : label;
  return '<text class="' + CLS.LINK_NAME + optional + '" x="2" y="-' + CAPTION_LIFT + '">' + esc(shorten(name, CAPTION_MAX_CHARS)) + cardSvg + '</text>';
}

/** The link as written in a tooltip: "shipTo 1 → complexType USAddress". */
const linkTitle = (edge) => edge.label + (cardinalityText(edge) ? ' ' + cardinalityText(edge) : '');

/** @param opts {id} selected on click, {place} where the node lives when it is another file's (a tab or a listed file), {kindText} replaces the kind, {link} the edge captioned above the node */
function nodeSvg(n, x, y, isCenter, opts) {
  const o = opts || {};
  const name = shorten(n.name, isCenter ? NAME_MAX_CHARS_CENTER : NAME_MAX_CHARS);
  const kindText = o.kindText || kindLabel(n.kind);
  const caption = o.link ? captionSvg(o.link) : '';
  return '<g class="' + CLS.NODE + ' ' + n.kind + (isCenter ? ' ' + CLS.CENTER : '') + '"' + dataAttr(DATA.ID, o.id || n.id)
    + placeAttributes(o.place, session.active.workspace, dataAttr, DATA) + ' transform="translate(' + x + ',' + y + ')">'
    + '<title>' + (o.link ? esc(linkTitle(o.link)) + ' → ' : '') + esc(t(MSG.GRAPH_NODE_TITLE, kindText, n.name)) + (n.doc ? '\n' + esc(n.doc.slice(0, 200)) : '') + '</title>'
    + caption
    + '<rect width="' + NODE_W + '" height="' + NODE_H + '"/>'
    + '<text class="' + CLS.NODE_NAME + '" x="10" y="' + (NODE_H / 2 + 1) + '">' + esc(name) + '</text>'
    + '<text class="' + CLS.NODE_KIND + '" x="' + (NODE_W - 8) + '" y="' + (NODE_H - 6) + '" text-anchor="end">' + esc(shorten(kindText, KIND_MAX_CHARS)) + '</text>'
    + '</g>';
}
