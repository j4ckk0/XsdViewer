/** The searchable list of objects by kind (left panel) and the schema header (namespace, imports, counts) at the top of the details panel (right). */
import { KINDS, NODE_KIND, STORAGE_KEY } from './constants.js';
import { $, dataAttr, esc, selector } from './dom.js';
import { CLS, DATA, ID } from './dom-names.js';
import { foldable } from './foldable.js';
import { t } from './i18n.js';
import { groupLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { matchedBy, matches } from './search.js';
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

/** The schema header (namespace, imports, counts) folds to its title line; folded until the user unfolds it once. */
export const schemaInfo = foldable({
  element: ID.SCHEMA_INFO, toggle: ID.SCHEMA_INFO_TOGGLE, storageKey: STORAGE_KEY.SCHEMA_INFO_COLLAPSED,
  titles: { fold: MSG.INFO_COLLAPSE, unfold: MSG.INFO_EXPAND }, defaultFolded: true,
});

export function renderNodeList() {
  const st = session.active;
  const f = st.filter.toLowerCase();
  const byKind = new Map(KINDS.map(k => [k, []]));
  for (const n of st.model.nodes) {
    if (!matches(n, f)) continue;
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
      const why = matchedBy(n, f);
      html += '<div class="' + CLS.ITEM + (n.id === st.selected ? ' ' + CLS.SELECTED : '') + '"' + dataAttr(DATA.ID, n.id) + ' title="' + esc(n.id) + '">'
        + '<span class="' + CLS.DOT + ' ' + k + '"></span><span>' + esc(n.name) + '</span>'
        + (why ? '<span class="' + CLS.WHY + '" title="' + esc(why) + '">' + esc(why) + '</span>' : '') + '</div>';
    }
    html += '</div>';
  }
  $(ID.NODE_LIST).innerHTML = html || '<div class="' + CLS.ITEM + ' ' + CLS.NO_MATCH + '">' + esc(t(MSG.LIST_NO_MATCH)) + '</div>';
}

/** Expands every group of the object list, or collapses them all. */
export function setAllGroupsExpanded(expanded) {
  session.active.collapsed = expanded ? new Set() : new Set(KINDS);
  if (session.active.model) renderNodeList();
}

/** Moves the highlight to the selected node without rebuilding the list. */
export function renderNodeListSelection() {
  const st = session.active;
  const selected = st.selected;
  const n = selected ? st.nodes.get(selected) : null;
  // the object selected in a view is shown in the list: its group opens when it was folded
  const group = n ? (KINDS.includes(n.kind) ? n.kind : NODE_KIND.EXTERNAL) : null;
  if (group && st.collapsed.delete(group)) renderNodeList();
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
