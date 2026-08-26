'use strict';

// ---- state ----------------------------------------------------------------------------

const KINDS = ['element', 'complexType', 'simpleType', 'group', 'attributeGroup', 'attribute', 'builtin', 'external'];
const KIND_LABELS = {
  element: 'Elements', complexType: 'Complex types', simpleType: 'Simple types', group: 'Groups',
  attributeGroup: 'Attribute groups', attribute: 'Attributes', builtin: 'Built-in types', external: 'External / undeclared'
};

const state = {
  fileName: null,
  text: '',
  model: null,        // { targetNamespace, imports, nodes, edges }
  nodes: new Map(),   // id -> node
  outEdges: new Map(),// id -> [edge]
  inEdges: new Map(), // id -> [edge]
  lineToNode: new Map(),
  selected: null,
  history: [],
  view: 'graph',
  filter: '',
  collapsed: new Set(),
};

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// ---- loading --------------------------------------------------------------------------

async function loadFile(file) {
  if (!file) return;
  const text = await file.text();
  await loadText(file.name, text);
}

async function loadText(name, text) {
  let resp;
  try {
    resp = await fetch('/api/parse', { method: 'POST', headers: { 'Content-Type': 'text/plain; charset=utf-8' }, body: text });
  } catch (e) {
    toast('Cannot reach the XsdViewer server: ' + e.message);
    return;
  }
  const json = await resp.json();
  if (!resp.ok) {
    toast('Cannot parse ' + name + ': ' + (json.error || resp.status));
    return;
  }
  state.fileName = name;
  state.text = text;
  state.model = json;
  state.nodes = new Map(json.nodes.map(n => [n.id, n]));
  state.outEdges = new Map();
  state.inEdges = new Map();
  for (const e of json.edges) {
    if (!state.outEdges.has(e.from)) state.outEdges.set(e.from, []);
    state.outEdges.get(e.from).push(e);
    if (!state.inEdges.has(e.to)) state.inEdges.set(e.to, []);
    state.inEdges.get(e.to).push(e);
  }
  state.lineToNode = new Map();
  for (const n of json.nodes) if (n.line > 0) state.lineToNode.set(n.line, n.id);
  state.history = [];
  state.selected = null;
  state.filter = '';
  $('search').value = '';

  document.title = name + ' – XsdViewer';
  $('fileName').textContent = name;
  $('fileName').title = name;
  renderSchemaInfo();
  renderNodeList();
  renderText();

  // Start on the first global element that nothing references (a likely document root), else the first node.
  const roots = json.nodes.filter(n => n.kind === 'element' && !(state.inEdges.get(n.id) || []).some(e => e.from !== n.id));
  const first = roots[0] || json.nodes.find(n => n.kind === 'element') || json.nodes[0];
  if (first) select(first.id, false); else { renderGraph(); renderDetails(); }
  showView(state.view);
}

function closeFile() {
  state.fileName = null; state.text = ''; state.model = null;
  state.nodes = new Map(); state.outEdges = new Map(); state.inEdges = new Map();
  state.lineToNode = new Map(); state.selected = null; state.history = [];
  document.title = 'XsdViewer';
  $('fileName').textContent = 'No file loaded';
  $('schemaInfo').innerHTML = '';
  $('nodeList').innerHTML = '';
  $('text').innerHTML = '';
  $('graphCanvas').innerHTML = '';
  $('details').classList.add('hidden');
  showView(state.view);
}

// ---- selection ------------------------------------------------------------------------

function select(id, pushHistory = true) {
  if (!state.nodes.has(id)) return;
  if (pushHistory && state.selected && state.selected !== id) state.history.push(state.selected);
  state.selected = id;
  $('backBtn').disabled = state.history.length === 0;
  renderNodeListSelection();
  renderGraph();
  renderDetails();
  highlightTextLine(true);
}

function goBack() {
  const prev = state.history.pop();
  if (prev) select(prev, false);
}

// ---- views ----------------------------------------------------------------------------

function showView(view) {
  state.view = view;
  document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.view === view));
  const loaded = !!state.model;
  $('empty').classList.toggle('hidden', loaded);
  $('graph').classList.toggle('hidden', !loaded || view !== 'graph');
  $('text').classList.toggle('hidden', !loaded || view !== 'text');
  $('details').classList.toggle('hidden', !loaded || !state.selected);
  if (loaded && view === 'text') highlightTextLine(true);
}

// ---- sidebar --------------------------------------------------------------------------

function renderSchemaInfo() {
  const m = state.model;
  let html = '<div><b>targetNamespace</b><br>' + (m.targetNamespace ? esc(m.targetNamespace) : '<i>none</i>') + '</div>';
  for (const i of m.imports) {
    html += '<div><b>' + esc(i.tag) + '</b> ' + esc(i.schemaLocation || i.namespace || '?')
      + (i.schemaLocation && i.namespace ? '<br><span>' + esc(i.namespace) + '</span>' : '') + '</div>';
  }
  html += '<div>' + m.nodes.length + ' objects, ' + m.edges.length + ' links</div>';
  $('schemaInfo').innerHTML = html;
}

function renderNodeList() {
  const f = state.filter.toLowerCase();
  const byKind = new Map(KINDS.map(k => [k, []]));
  for (const n of state.model.nodes) {
    if (f && !n.name.toLowerCase().includes(f)) continue;
    (byKind.get(n.kind) || byKind.get('external')).push(n);
  }
  let html = '';
  for (const k of KINDS) {
    const items = byKind.get(k);
    if (!items.length) continue;
    items.sort((a, b) => a.name.localeCompare(b.name));
    html += '<div class="group-h' + (state.collapsed.has(k) ? ' collapsed' : '') + '" data-kind="' + k + '">'
      + '<span>' + KIND_LABELS[k] + '</span><span class="count">' + items.length + '</span></div><div class="group-items">';
    for (const n of items) {
      html += '<div class="item' + (n.id === state.selected ? ' selected' : '') + '" data-id="' + esc(n.id) + '" title="' + esc(n.id) + '">'
        + '<span class="dot ' + k + '"></span><span>' + esc(n.name) + '</span></div>';
    }
    html += '</div>';
  }
  $('nodeList').innerHTML = html || '<div class="item" style="color:var(--muted)">no match</div>';
}

function renderNodeListSelection() {
  document.querySelectorAll('#nodeList .item').forEach(el => {
    const on = el.dataset.id === state.selected;
    el.classList.toggle('selected', on);
    if (on) el.scrollIntoView({ block: 'nearest' });
  });
}

// ---- graph ----------------------------------------------------------------------------

const NODE_W = 200, NODE_H = 36, ROW = 50, MARGIN = 24, MIN_GAP = 90;

function renderGraph() {
  const canvas = $('graphCanvas');
  if (!state.selected) { canvas.innerHTML = ''; return; }
  const center = state.nodes.get(state.selected);
  const showBuiltins = $('showBuiltins').checked;
  $('graphTitle').textContent = center.kind + ' ' + center.name;

  // Group the level-1 links by neighbour, merging the labels of parallel edges.
  const neigh = new Map(); // id -> { out: [labels], in: [labels] }
  const get = (id) => { if (!neigh.has(id)) neigh.set(id, { out: [], in: [] }); return neigh.get(id); };
  const selfLabels = [];
  for (const e of state.outEdges.get(center.id) || []) {
    if (e.to === center.id) { selfLabels.push(e.label); continue; }
    get(e.to).out.push(e.label);
  }
  for (const e of state.inEdges.get(center.id) || []) {
    if (e.from === center.id) continue;
    get(e.from).in.push(e.label);
  }
  const right = [], left = [];
  for (const [id, l] of neigh) {
    const n = state.nodes.get(id);
    if (!n || (!showBuiltins && n.kind === 'builtin')) continue;
    (l.out.length ? right : left).push({ n, labels: l });
  }
  const byName = (a, b) => a.n.kind.localeCompare(b.n.kind) || a.n.name.localeCompare(b.n.name);
  right.sort(byName); left.sort(byName);

  const rows = Math.max(right.length, left.length, 1);
  const W = Math.max(canvas.clientWidth, 3 * NODE_W + 2 * MIN_GAP + 2 * MARGIN);
  const H = Math.max(canvas.clientHeight, rows * ROW + 2 * MARGIN + (selfLabels.length ? 40 : 0));
  const cx = W / 2, cy = H / 2;
  const xLeft = MARGIN, xRight = W - MARGIN - NODE_W;
  const yOf = (i, count) => cy - ((count - 1) * ROW) / 2 + i * ROW;

  let svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + W + '" height="' + H + '" viewBox="0 0 ' + W + ' ' + H + '">'
    + '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">'
    + '<path d="M0,0 L10,5 L0,10 z" fill="#9aa3af"/></marker></defs>';

  const edges = [], labels = [], nodes = [];

  // outgoing: centre -> right column
  right.forEach((r, i) => {
    const y = yOf(i, right.length);
    const x1 = cx + NODE_W / 2, y1 = cy, x2 = xRight, y2 = y;
    edges.push(curve(x1, y1, x2, y2));
    labels.push(label(x1, y1, x2, y2, 0.72, r.labels.out.join(', ')));
    nodes.push(nodeSvg(r.n, xRight, y - NODE_H / 2, false));
  });
  // incoming: left column -> centre
  left.forEach((l, i) => {
    const y = yOf(i, left.length);
    const x1 = xLeft + NODE_W, y1 = y, x2 = cx - NODE_W / 2, y2 = cy;
    edges.push(curve(x1, y1, x2, y2));
    labels.push(label(x1, y1, x2, y2, 0.28, l.labels.in.join(', ')));
    nodes.push(nodeSvg(l.n, xLeft, y - NODE_H / 2, false));
  });
  // self reference (recursive type)
  if (selfLabels.length) {
    const top = cy - NODE_H / 2;
    edges.push('<path class="edge" marker-end="url(#arrow)" d="M' + (cx - 30) + ',' + top
      + ' C' + (cx - 50) + ',' + (top - 60) + ' ' + (cx + 50) + ',' + (top - 60) + ' ' + (cx + 30) + ',' + top + '"/>');
    labels.push(textWithBg(cx, top - 45, selfLabels.join(', ')));
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
  return '<path class="edge" marker-end="url(#arrow)" d="M' + x1 + ',' + y1 + ' C' + (x1 + dx) + ',' + y1 + ' ' + (x2 - dx) + ',' + y2 + ' ' + x2 + ',' + y2 + '"/>';
}

function bezierPoint(x1, y1, x2, y2, t) {
  const dx = (x2 - x1) / 2;
  const p = (a, b, c, d) => (1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * d;
  return [p(x1, x1 + dx, x2 - dx, x2), p(y1, y1, y2, y2)];
}

function label(x1, y1, x2, y2, t, text) {
  const [x, y] = bezierPoint(x1, y1, x2, y2, t);
  return textWithBg(x, y - 6, text);
}

function textWithBg(x, y, text) {
  const t = text.length > 40 ? text.slice(0, 38) + '…' : text;
  const w = t.length * 6.2 + 8;
  return '<rect class="edge-label-bg" x="' + (x - w / 2) + '" y="' + (y - 9) + '" width="' + w + '" height="14" rx="3"/>'
    + '<text class="edge-label" x="' + x + '" y="' + (y + 2) + '" text-anchor="middle"><title>' + esc(text) + '</title>' + esc(t) + '</text>';
}

function nodeSvg(n, x, y, isCenter) {
  const maxChars = isCenter ? 24 : 26;
  const name = n.name.length > maxChars ? n.name.slice(0, maxChars - 1) + '…' : n.name;
  return '<g class="node ' + n.kind + (isCenter ? ' center' : '') + '" data-id="' + esc(n.id) + '" transform="translate(' + x + ',' + y + ')">'
    + '<title>' + esc(n.kind + ' ' + n.name) + (n.doc ? '\n' + esc(n.doc.slice(0, 200)) : '') + '</title>'
    + '<rect width="' + NODE_W + '" height="' + NODE_H + '"/>'
    + '<text class="name" x="10" y="' + (NODE_H / 2 + 1) + '">' + esc(name) + '</text>'
    + '<text class="kind" x="' + (NODE_W - 8) + '" y="' + (NODE_H - 6) + '" text-anchor="end">' + esc(n.kind) + '</text>'
    + '</g>';
}

// ---- details --------------------------------------------------------------------------

function renderDetails() {
  const panel = $('details');
  if (!state.selected) { panel.classList.add('hidden'); return; }
  const n = state.nodes.get(state.selected);
  let html = '<h2>' + esc(n.name) + '</h2><span class="badge ' + n.kind + '">' + esc(n.kind) + '</span>';
  html += '<div class="meta">' + (n.line > 0 ? '<a data-line="' + n.line + '">line ' + n.line + ' → show in text</a>' : 'no declaration in this file') + '</div>';
  if (n.doc) html += '<div class="doc">' + esc(n.doc) + '</div>';

  const out = (state.outEdges.get(n.id) || []).filter(e => state.nodes.has(e.to));
  const inn = (state.inEdges.get(n.id) || []).filter(e => state.nodes.has(e.from));
  html += '<h3>Links out (' + out.length + ')</h3>';
  html += out.length ? out.map(e => linkHtml(e.label, state.nodes.get(e.to))).join('') : '<div class="meta">none</div>';
  html += '<h3>Used by (' + inn.length + ')</h3>';
  html += inn.length ? inn.map(e => linkHtml(e.label, state.nodes.get(e.from))).join('') : '<div class="meta">none</div>';
  panel.innerHTML = html;
  panel.classList.remove('hidden');
}

function linkHtml(lbl, target) {
  return '<div class="link" data-id="' + esc(target.id) + '" title="' + esc(target.kind + ' ' + target.name) + '">'
    + '<span class="lbl">' + esc(lbl) + '</span><span class="dot ' + target.kind + '"></span><span class="tgt">' + esc(target.name) + '</span></div>';
}

// ---- text view ------------------------------------------------------------------------

function renderText() {
  const lines = highlightXml(state.text);
  let html = '';
  for (let i = 0; i < lines.length; i++) {
    const ln = i + 1;
    const id = state.lineToNode.get(ln);
    html += '<div class="line' + (id ? ' decl' : '') + '" data-n="' + ln + '"' + (id ? ' data-id="' + esc(id) + '"' : '') + '>'
      + '<span class="ln"' + (id ? ' title="Select ' + esc(id) + '"' : '') + '>' + ln + '</span><span class="code">' + (lines[i] || ' ') + '</span></div>';
  }
  $('text').innerHTML = html;
}

function highlightTextLine(scroll) {
  const container = $('text');
  container.querySelectorAll('.line.hl').forEach(el => el.classList.remove('hl'));
  if (!state.selected) return;
  const n = state.nodes.get(state.selected);
  if (!n || n.line <= 0) return;
  const el = container.querySelector('.line[data-n="' + n.line + '"]');
  if (!el) return;
  el.classList.add('hl');
  if (scroll && state.view === 'text') el.scrollIntoView({ block: 'center' });
}

/** Tokenises XML into highlighted HTML, one entry per source line (spans never cross a line). */
function highlightXml(text) {
  const out = [];
  const push = (cls, s) => {
    const parts = s.split('\n');
    for (let k = 0; k < parts.length; k++) {
      if (k) out.push('\n');
      if (parts[k]) out.push(cls ? '<span class="' + cls + '">' + esc(parts[k]) + '</span>' : esc(parts[k]));
    }
  };
  const n = text.length;
  const reName = /<\/?[^\s>\/]*/y, reWs = /\s+/y, reAttr = /[^\s=>\/]+/y;
  let i = 0;
  while (i < n) {
    if (text.startsWith('<!--', i)) {
      let j = text.indexOf('-->', i + 4); j = j < 0 ? n : j + 3;
      push('c', text.slice(i, j)); i = j; continue;
    }
    if (text.startsWith('<?', i)) {
      let j = text.indexOf('?>', i + 2); j = j < 0 ? n : j + 2;
      push('pi', text.slice(i, j)); i = j; continue;
    }
    if (text.startsWith('<![CDATA[', i)) {
      let j = text.indexOf(']]>', i); j = j < 0 ? n : j + 3;
      push('v', text.slice(i, j)); i = j; continue;
    }
    if (text[i] === '<') {
      reName.lastIndex = i;
      const m = reName.exec(text);
      push('t', m[0]); i += m[0].length;
      while (i < n && text[i] !== '>') {
        if (text[i] === '/') { push('t', '/'); i++; continue; }
        reWs.lastIndex = i;
        const ws = reWs.exec(text);
        if (ws) { push(null, ws[0]); i += ws[0].length; continue; }
        reAttr.lastIndex = i;
        const a = reAttr.exec(text);
        if (a) { push('a', a[0]); i += a[0].length; continue; }
        if (text[i] === '=') {
          push(null, '='); i++;
          if (text[i] === '"' || text[i] === "'") {
            let j = text.indexOf(text[i], i + 1); j = j < 0 ? n : j + 1;
            push('v', text.slice(i, j)); i = j;
          }
          continue;
        }
        push(null, text[i]); i++;
      }
      if (i < n) { push('t', '>'); i++; }
      continue;
    }
    let j = text.indexOf('<', i); if (j < 0) j = n;
    push(null, text.slice(i, j)); i = j;
  }
  return out.join('').split('\n');
}

// ---- misc UI --------------------------------------------------------------------------

let toastTimer = null;
function toast(msg) {
  const t = $('toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add('hidden'), 6000);
}

function wireEvents() {
  // File menu
  const menu = $('fileMenu');
  $('fileMenuBtn').addEventListener('click', (e) => { e.stopPropagation(); menu.classList.toggle('hidden'); });
  document.addEventListener('click', () => menu.classList.add('hidden'));
  $('menuOpen').addEventListener('click', () => { menu.classList.add('hidden'); $('fileInput').click(); });
  $('menuClose').addEventListener('click', () => { menu.classList.add('hidden'); closeFile(); });
  $('fileInput').addEventListener('change', (e) => { loadFile(e.target.files[0]); e.target.value = ''; });
  document.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'o') { e.preventDefault(); $('fileInput').click(); }
    if (e.key === 'Escape') menu.classList.add('hidden');
    if (e.altKey && e.key === 'ArrowLeft') goBack();
  });

  // Drag and drop anywhere in the window
  let dragDepth = 0;
  window.addEventListener('dragenter', (e) => {
    if (!e.dataTransfer || ![...e.dataTransfer.types].includes('Files')) return;
    e.preventDefault(); dragDepth++; $('dropOverlay').classList.remove('hidden');
  });
  window.addEventListener('dragover', (e) => { if (e.dataTransfer && [...e.dataTransfer.types].includes('Files')) { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; } });
  window.addEventListener('dragleave', () => { if (--dragDepth <= 0) { dragDepth = 0; $('dropOverlay').classList.add('hidden'); } });
  window.addEventListener('drop', (e) => {
    e.preventDefault(); dragDepth = 0; $('dropOverlay').classList.add('hidden');
    const file = e.dataTransfer && e.dataTransfer.files[0];
    if (file) loadFile(file);
  });

  // Tabs
  document.querySelectorAll('.tab').forEach(t => t.addEventListener('click', () => showView(t.dataset.view)));
  $('showBuiltins').addEventListener('change', renderGraph);
  $('backBtn').addEventListener('click', goBack);

  // Search
  $('search').addEventListener('input', (e) => { state.filter = e.target.value.trim(); if (state.model) renderNodeList(); });

  // Node list: select / collapse groups
  $('nodeList').addEventListener('click', (e) => {
    const h = e.target.closest('.group-h');
    if (h) { const k = h.dataset.kind; state.collapsed.has(k) ? state.collapsed.delete(k) : state.collapsed.add(k); h.classList.toggle('collapsed'); return; }
    const it = e.target.closest('.item');
    if (it && it.dataset.id) select(it.dataset.id);
  });

  // Graph nodes
  $('graphCanvas').addEventListener('click', (e) => {
    const g = e.target.closest('.node');
    if (g && g.dataset.id !== state.selected) select(g.dataset.id);
  });
  window.addEventListener('resize', () => { if (state.model && state.view === 'graph') renderGraph(); });

  // Details links
  $('details').addEventListener('click', (e) => {
    const a = e.target.closest('a[data-line]');
    if (a) { showView('text'); return; }
    const l = e.target.closest('.link');
    if (l) select(l.dataset.id);
  });

  // Text view: click a declaration's line number to select it
  $('text').addEventListener('click', (e) => {
    const ln = e.target.closest('.ln');
    const line = ln && ln.closest('.line.decl');
    if (line) select(line.dataset.id);
  });
}

async function loadInitialFile() {
  try {
    const resp = await fetch('/api/initial');
    if (!resp.ok) return;
    const { name, text } = await resp.json();
    await loadText(name, text);
  } catch (e) { /* no initial file */ }
}

wireEvents();
showView('graph');
loadInitialFile();
