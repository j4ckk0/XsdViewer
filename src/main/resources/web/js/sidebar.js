/** The left panel: the schema header (namespace, imports, counts) and the searchable list of objects by kind. */
import { KINDS, NODE_KIND, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc, selector } from './dom.js';
import { t } from './i18n.js';
import { groupLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

export function renderSchemaInfo() {
  const m = session.active.model;
  let html = '<div><b>' + esc(t(MSG.INFO_TARGET_NAMESPACE)) + '</b><br>'
    + (m.targetNamespace ? esc(m.targetNamespace) : '<i>' + esc(t(MSG.INFO_NONE)) + '</i>') + '</div>';
  for (const i of m.imports) {
    html += '<div><b>' + esc(i.tag) + '</b> ' + esc(i.schemaLocation || i.namespace || t(MSG.INFO_UNKNOWN_LOCATION))
      + (i.schemaLocation && i.namespace ? '<br><span>' + esc(i.namespace) + '</span>' : '') + '</div>';
  }
  html += '<div>' + esc(t(MSG.INFO_COUNTS, m.nodes.length, m.edges.length)) + '</div>';
  $(ID.SCHEMA_INFO_CONTENT).innerHTML = html;
}

const COLLAPSE_GLYPH = '▾', EXPAND_GLYPH = '▸';

/** Folds the schema header (namespace, imports, counts) to its title line, or unfolds it; remembered across sessions. */
export function setSchemaInfoCollapsed(collapsed) {
  $(ID.SCHEMA_INFO).classList.toggle(CLS.COLLAPSED, collapsed);
  const toggle = $(ID.SCHEMA_INFO_TOGGLE);
  toggle.textContent = collapsed ? EXPAND_GLYPH : COLLAPSE_GLYPH;
  toggle.title = t(collapsed ? MSG.INFO_EXPAND : MSG.INFO_COLLAPSE);
  try { localStorage.setItem(STORAGE_KEY.SCHEMA_INFO_COLLAPSED, collapsed ? STORAGE_TRUE : STORAGE_FALSE); } catch (e) { /* storage unavailable */ }
}

export function toggleSchemaInfo() {
  setSchemaInfoCollapsed(!$(ID.SCHEMA_INFO).classList.contains(CLS.COLLAPSED));
}

/** Restores the folded state remembered in the browser. */
export function initSchemaInfo() {
  let collapsed = false;
  try { collapsed = localStorage.getItem(STORAGE_KEY.SCHEMA_INFO_COLLAPSED) === STORAGE_TRUE; } catch (e) { /* storage unavailable */ }
  setSchemaInfoCollapsed(collapsed);
}

export function renderNodeList() {
  const st = session.active;
  const f = st.filter.toLowerCase();
  const byKind = new Map(KINDS.map(k => [k, []]));
  for (const n of st.model.nodes) {
    if (f && !n.name.toLowerCase().includes(f)) continue;
    (byKind.get(n.kind) || byKind.get(NODE_KIND.EXTERNAL)).push(n);
  }
  let html = '';
  for (const k of KINDS) {
    const items = byKind.get(k);
    if (!items.length) continue;
    items.sort((a, b) => a.name.localeCompare(b.name));
    html += '<div class="' + CLS.GROUP_HEADER + (st.collapsed.has(k) ? ' ' + CLS.COLLAPSED : '') + '"' + dataAttr(DATA.KIND, k) + '>'
      + '<span>' + esc(groupLabel(k)) + '</span><span class="' + CLS.COUNT + '">' + items.length + '</span></div>'
      + '<div class="' + CLS.GROUP_ITEMS + '">';
    for (const n of items) {
      html += '<div class="' + CLS.ITEM + (n.id === st.selected ? ' ' + CLS.SELECTED : '') + '"' + dataAttr(DATA.ID, n.id) + ' title="' + esc(n.id) + '">'
        + '<span class="' + CLS.DOT + ' ' + k + '"></span><span>' + esc(n.name) + '</span></div>';
    }
    html += '</div>';
  }
  $(ID.NODE_LIST).innerHTML = html || '<div class="' + CLS.ITEM + ' ' + CLS.NO_MATCH + '">' + esc(t(MSG.LIST_NO_MATCH)) + '</div>';
}

/** Moves the highlight to the selected node without rebuilding the list. */
export function renderNodeListSelection() {
  const selected = session.active.selected;
  $(ID.NODE_LIST).querySelectorAll(selector(CLS.ITEM)).forEach(el => {
    const on = el.dataset[DATA.ID] === selected;
    el.classList.toggle(CLS.SELECTED, on);
    if (on) el.scrollIntoView({ block: 'nearest' });
  });
}

/** Expands / collapses the group of kind {@code kind} (click on its header). */
export function toggleGroup(header) {
  const k = header.dataset[DATA.KIND];
  const collapsed = session.active.collapsed;
  if (collapsed.has(k)) collapsed.delete(k); else collapsed.add(k);
  header.classList.toggle(CLS.COLLAPSED);
}
