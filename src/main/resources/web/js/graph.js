/**
 * The graph view: the selected object in the centre, what it links to on the right, what uses it on
 * the left, and optionally a second level on each side, drawn as an SVG with bezier edges.
 */
import { NODE_KIND, SVG_NS } from './constants.js';
import { findInTabs, kindsOf, usersInOtherTabs } from './declarations.js';
import { $, CLS, DATA, ID, SVG_ID, dataAttr, esc } from './dom.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

// ---- layout ----
export const NODE_W = 200, NODE_H = 36;
const ROW = 50, MARGIN = 24, MIN_GAP = 90;
const SELF_LOOP_HEIGHT = 60, SELF_LOOP_ROOM = 40, SELF_LOOP_SPREAD = 30, SELF_LOOP_CONTROL_SPREAD = 50, SELF_LABEL_LIFT = 45;
/** Position of a label along an edge (0 = start, 1 = end), per edge type. */
const LABEL_AT = { OUT: 0.72, OUT2: 0.55, IN: 0.28, IN2: 0.45 };
const LABEL_MAX_CHARS = 40, LABEL_CHAR_W = 6.2, LABEL_PAD = 8, LABEL_H = 14, LABEL_RADIUS = 3;
const NAME_MAX_CHARS_CENTER = 24, NAME_MAX_CHARS = 26, KIND_MAX_CHARS = 30;
const ELLIPSIS = '…';
const LIST_SEPARATOR = ', ';
const ARROW_COLOR = '#9aa3af';

const shorten = (s, max) => (s.length > max ? s.slice(0, max - 1) + ELLIPSIS : s);

export function renderGraph() {
  const st = session.active;
  const canvas = $(ID.GRAPH_CANVAS);
  if (!st.selected) { canvas.innerHTML = ''; return; }
  const center = st.nodes.get(st.selected);
  const showBuiltins = $(ID.SHOW_BUILTINS).checked;
  const depth = $(ID.TWO_LEVELS).checked ? 2 : 1;
  $(ID.GRAPH_TITLE).textContent = t(MSG.GRAPH_NODE_TITLE, kindLabel(center.kind), center.name);
  const visible = (n) => n && (showBuiltins || n.kind !== NODE_KIND.BUILTIN);
  const byName = (a, b) => a.n.kind.localeCompare(b.n.kind) || a.n.name.localeCompare(b.n.name);
  const fileKind = (n, tab) => ({ kindText: t(MSG.GRAPH_KIND_IN_FILE, kindLabel(n.kind), tab.fileName) });

  // Group the level-1 links by neighbour, merging the labels of parallel edges.
  const neigh = new Map(); // id -> { out: [labels], in: [labels] }
  const get = (id) => { if (!neigh.has(id)) neigh.set(id, { out: [], in: [] }); return neigh.get(id); };
  const selfLabels = [];
  for (const e of st.outEdges.get(center.id) || []) {
    if (e.to === center.id) { selfLabels.push(e.label); continue; }
    get(e.to).out.push(e.label);
  }
  for (const e of st.inEdges.get(center.id) || []) {
    if (e.from === center.id) continue;
    get(e.from).in.push(e.label);
  }
  const right = [], left = [];
  for (const [id, l] of neigh) {
    const n = st.nodes.get(id);
    if (!visible(n)) continue;
    (l.out.length ? right : left).push({ n, labels: l, tab: null, children: [], parents: [] });
  }
  // Users of the centre in the other open tabs, where it appears as an external placeholder.
  for (const u of usersInOtherTabs(center, st)) {
    if (visible(u.n)) left.push({ n: u.n, labels: { in: u.labels, out: [] }, tab: u.tab, children: [], parents: [] });
  }
  right.sort(byName); left.sort(byName);

  // Level 2, drawn as trees (a node may appear under several parents).
  if (depth === 2) {
    // right: what each level-1 target links to; an external target declared in another tab is expanded from there
    for (const r of right) {
      let src = st, id = r.n.id;
      if (r.n.kind === NODE_KIND.EXTERNAL) {
        const found = findInTabs(r.n.name, kindsOf(r.n), r.n.ns || '', st);
        if (!found) continue;
        src = found.tab; id = found.id;
        r.resolved = { n: src.nodes.get(id), tab: src };
      }
      const kids = new Map(); // id -> [labels]
      for (const e of src.outEdges.get(id) || []) {
        if (e.to === id) continue;
        if (!kids.has(e.to)) kids.set(e.to, []);
        kids.get(e.to).push(e.label);
      }
      for (const [kid, ls] of kids) {
        const n = src.nodes.get(kid);
        if (visible(n)) r.children.push({ n, labels: ls, tab: src === st ? null : src });
      }
      r.children.sort(byName);
    }
    // left: what uses each level-1 user, in its own file and in the other open tabs
    for (const l of left) {
      const src = l.tab || st;
      const par = new Map();
      for (const e of src.inEdges.get(l.n.id) || []) {
        if (e.from === l.n.id) continue;
        if (!par.has(e.from)) par.set(e.from, []);
        par.get(e.from).push(e.label);
      }
      for (const [pid, ls] of par) {
        const n = src.nodes.get(pid);
        if (visible(n)) l.parents.push({ n, labels: ls, tab: l.tab });
      }
      if (l.n.kind !== NODE_KIND.EXTERNAL) {
        for (const u of usersInOtherTabs(l.n, src)) if (visible(u.n)) l.parents.push({ n: u.n, labels: u.labels, tab: u.tab });
      }
      l.parents.sort(byName);
    }
  }
  const spanR = (r) => Math.max(1, r.children.length);
  const spanL = (l) => Math.max(1, l.parents.length);
  const rightRows = right.reduce((sum, r) => sum + spanR(r), 0);
  const leftRows = left.reduce((sum, l) => sum + spanL(l), 0);

  // Columns: (level 2 in) | incoming | centre | level 1 out | (level 2 out); spread over the canvas width when wider than needed.
  const cols = 2 * depth + 1, ci = depth;   // ci: index of the centre column
  const W = Math.max(canvas.clientWidth, cols * NODE_W + (cols - 1) * MIN_GAP + 2 * MARGIN);
  const gap = (W - 2 * MARGIN - cols * NODE_W) / (cols - 1);
  const colX = (c) => MARGIN + c * (NODE_W + gap);
  const rows = Math.max(rightRows, leftRows, 1);
  const H = Math.max(canvas.clientHeight, rows * ROW + 2 * MARGIN + (selfLabels.length ? SELF_LOOP_ROOM : 0));
  const cy = H / 2;
  const cx = colX(ci) + NODE_W / 2, xLeft = colX(ci - 1), xR1 = colX(ci + 1);
  const xL2 = depth === 2 ? colX(0) : 0, xR2 = depth === 2 ? colX(ci + 2) : 0;
  const yOf = (i, count) => cy - ((count - 1) * ROW) / 2 + i * ROW;
  const tabOpt = (tab, n) => tab ? Object.assign({ tab: session.tabs.indexOf(tab) }, fileKind(n, tab)) : null;

  let svg = '<svg xmlns="' + SVG_NS + '" width="' + W + '" height="' + H + '" viewBox="0 0 ' + W + ' ' + H + '">'
    + '<defs><marker id="' + SVG_ID.ARROW + '" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">'
    + '<path d="M0,0 L10,5 L0,10 z" fill="' + ARROW_COLOR + '"/></marker></defs>';

  const edges = [], labels = [], nodes = [];

  // outgoing: centre -> level 1 (-> level 2)
  let row = 0;
  for (const r of right) {
    const first = yOf(row, rightRows);
    const y = first + ((spanR(r) - 1) * ROW) / 2;   // a parent sits in the middle of its children
    const x1 = cx + NODE_W / 2, y1 = cy, x2 = xR1, y2 = y;
    edges.push(curve(x1, y1, x2, y2));
    labels.push(label(x1, y1, x2, y2, LABEL_AT.OUT, r.labels.out.join(LIST_SEPARATOR)));
    nodes.push(r.resolved
      ? nodeSvg(r.resolved.n, xR1, y - NODE_H / 2, false, Object.assign({ id: r.n.id }, fileKind(r.resolved.n, r.resolved.tab)))
      : nodeSvg(r.n, xR1, y - NODE_H / 2, false));
    r.children.forEach((c, k) => {
      const yc = first + k * ROW;
      edges.push(curve(xR1 + NODE_W, y, xR2, yc));
      labels.push(label(xR1 + NODE_W, y, xR2, yc, LABEL_AT.OUT2, c.labels.join(LIST_SEPARATOR)));
      nodes.push(nodeSvg(c.n, xR2, yc - NODE_H / 2, false, tabOpt(c.tab, c.n)));
    });
    row += spanR(r);
  }
  // incoming: (level 2 ->) level 1 -> centre
  row = 0;
  for (const l of left) {
    const first = yOf(row, leftRows);
    const y = first + ((spanL(l) - 1) * ROW) / 2;
    const x1 = xLeft + NODE_W, y1 = y, x2 = cx - NODE_W / 2, y2 = cy;
    edges.push(curve(x1, y1, x2, y2));
    labels.push(label(x1, y1, x2, y2, LABEL_AT.IN, l.labels.in.join(LIST_SEPARATOR)));
    nodes.push(nodeSvg(l.n, xLeft, y - NODE_H / 2, false, tabOpt(l.tab, l.n)));
    l.parents.forEach((p, k) => {
      const yp = first + k * ROW;
      edges.push(curve(xL2 + NODE_W, yp, xLeft, y));
      labels.push(label(xL2 + NODE_W, yp, xLeft, y, LABEL_AT.IN2, p.labels.join(LIST_SEPARATOR)));
      nodes.push(nodeSvg(p.n, xL2, yp - NODE_H / 2, false, tabOpt(p.tab, p.n)));
    });
    row += spanL(l);
  }
  // self reference (recursive type)
  if (selfLabels.length) {
    const top = cy - NODE_H / 2;
    edges.push('<path class="' + CLS.EDGE + '" marker-end="url(#' + SVG_ID.ARROW + ')" d="M' + (cx - SELF_LOOP_SPREAD) + ',' + top
      + ' C' + (cx - SELF_LOOP_CONTROL_SPREAD) + ',' + (top - SELF_LOOP_HEIGHT) + ' ' + (cx + SELF_LOOP_CONTROL_SPREAD) + ',' + (top - SELF_LOOP_HEIGHT)
      + ' ' + (cx + SELF_LOOP_SPREAD) + ',' + top + '"/>');
    labels.push(textWithBg(cx, top - SELF_LABEL_LIFT, selfLabels.join(LIST_SEPARATOR)));
  }
  nodes.push(nodeSvg(center, cx - NODE_W / 2, cy - NODE_H / 2, true));

  svg += edges.join('') + labels.join('') + nodes.join('') + '</svg>';
  canvas.innerHTML = svg;

  // keep the centre in view
  canvas.scrollTop = Math.max(0, cy - canvas.clientHeight / 2);
  canvas.scrollLeft = Math.max(0, cx - canvas.clientWidth / 2);
}

function curve(x1, y1, x2, y2) {
  const dx = (x2 - x1) / 2;
  return '<path class="' + CLS.EDGE + '" marker-end="url(#' + SVG_ID.ARROW + ')" d="M' + x1 + ',' + y1
    + ' C' + (x1 + dx) + ',' + y1 + ' ' + (x2 - dx) + ',' + y2 + ' ' + x2 + ',' + y2 + '"/>';
}

function bezierPoint(x1, y1, x2, y2, t) {
  const dx = (x2 - x1) / 2;
  const p = (a, b, c, d) => (1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * d;
  return [p(x1, x1 + dx, x2 - dx, x2), p(y1, y1, y2, y2)];
}

function label(x1, y1, x2, y2, at, text) {
  const [x, y] = bezierPoint(x1, y1, x2, y2, at);
  return textWithBg(x, y - 6, text);
}

function textWithBg(x, y, text) {
  const shown = shorten(text, LABEL_MAX_CHARS);
  const w = shown.length * LABEL_CHAR_W + LABEL_PAD;
  return '<rect class="' + CLS.EDGE_LABEL_BG + '" x="' + (x - w / 2) + '" y="' + (y - 9) + '" width="' + w + '" height="' + LABEL_H + '" rx="' + LABEL_RADIUS + '"/>'
    + '<text class="' + CLS.EDGE_LABEL + '" x="' + x + '" y="' + (y + 2) + '" text-anchor="middle"><title>' + esc(text) + '</title>' + esc(shown) + '</text>';
}

/**
 * @param opts optional: {id} to select on click (default n.id), {tab} index of the tab the node
 *             belongs to (another file), {kindText} text shown instead of the kind
 */
function nodeSvg(n, x, y, isCenter, opts) {
  const o = opts || {};
  const name = shorten(n.name, isCenter ? NAME_MAX_CHARS_CENTER : NAME_MAX_CHARS);
  const kindText = o.kindText || kindLabel(n.kind);
  return '<g class="' + CLS.NODE + ' ' + n.kind + (isCenter ? ' ' + CLS.CENTER : '') + '"' + dataAttr(DATA.ID, o.id || n.id)
    + (o.tab != null ? dataAttr(DATA.TAB, o.tab) : '') + ' transform="translate(' + x + ',' + y + ')">'
    + '<title>' + esc(t(MSG.GRAPH_NODE_TITLE, kindText, n.name)) + (n.doc ? '\n' + esc(n.doc.slice(0, 200)) : '') + '</title>'
    + '<rect width="' + NODE_W + '" height="' + NODE_H + '"/>'
    + '<text class="' + CLS.NODE_NAME + '" x="10" y="' + (NODE_H / 2 + 1) + '">' + esc(name) + '</text>'
    + '<text class="' + CLS.NODE_KIND + '" x="' + (NODE_W - 8) + '" y="' + (NODE_H - 6) + '" text-anchor="end">' + esc(shorten(kindText, KIND_MAX_CHARS)) + '</text>'
    + '</g>';
}
