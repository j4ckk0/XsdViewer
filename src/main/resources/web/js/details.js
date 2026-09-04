/** The right panel, under the schema header (sidebar.js): the selected object, its expression (a Schematron rule's context, an assertion's test), its documentation, the values it enumerates, its links out and the objects using it. Collapsible to a strip. */
import { cardinalityText, isOptional } from './cardinality.js';
import { NODE_KIND, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE } from './constants.js';
import { isMarked } from './object-compare.js';
import { placeAttributes, usersInWorkspace } from './declarations.js';
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { updateSplitters } from './panels.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

export function renderDetails() {
  const st = session.active;
  const panel = $(ID.DETAILS);
  if (!st.selected) { $(ID.DETAILS_CONTENT).innerHTML = ''; panel.classList.remove(CLS.HIDDEN); return; }
  const n = st.nodes.get(st.selected);
  let html = '<h2>' + esc(n.name) + '</h2><span class="' + CLS.BADGE + ' ' + n.kind + '">' + esc(kindLabel(n.kind)) + '</span>';
  html += '<div class="' + CLS.META + '">'
    + (n.line > 0 ? '<a' + dataAttr(DATA.LINE, n.line) + '>' + esc(t(MSG.DETAILS_SHOW_IN_TEXT, n.line)) + '</a>' : esc(t(MSG.DETAILS_NO_DECLARATION)))
    + '</div>';
  // marked for the Compare view, which draws two marked declarations wherever each of them lives
  if (n.kind !== NODE_KIND.EXTERNAL) {
    const marked = isMarked(st, n.id);
    html += '<button class="' + CLS.MARK_BUTTON + (marked ? ' ' + CLS.MARKED : '') + '" type="button" title="'
      + esc(t(marked ? MSG.OBJECT_MARK_OFF_TITLE : MSG.OBJECT_MARK_TITLE)) + '">'
      + esc(t(marked ? MSG.OBJECT_MARKED : MSG.OBJECT_MARK)) + '</button>';
  }
  if (n.xpath) html += '<div class="' + CLS.XPATH + '" title="' + esc(t(MSG.DETAILS_XPATH)) + '"><code>' + esc(n.xpath) + '</code></div>';
  if (n.doc) html += '<div class="' + CLS.DOC + '">' + esc(n.doc) + '</div>';
  if (n.values && n.values.length) {
    html += '<h3>' + esc(t(MSG.DETAILS_VALUES, n.values.length)) + '</h3>'
      + n.values.map(v => '<div class="' + CLS.VALUE + '"><code>' + esc(v.value) + '</code>'
        + (v.doc ? '<span class="' + CLS.VALUE_DOC + '" title="' + esc(v.doc) + '">' + esc(v.doc) + '</span>' : '') + '</div>').join('');
  }

  const out = (st.outEdges.get(n.id) || []).filter(e => st.nodes.has(e.to));
  const inn = (st.inEdges.get(n.id) || []).filter(e => st.nodes.has(e.from));
  const none = '<div class="' + CLS.META + '">' + esc(t(MSG.DETAILS_NONE)) + '</div>';
  html += '<h3>' + esc(t(MSG.DETAILS_LINKS_OUT, out.length)) + '</h3>';
  html += out.length ? out.map(e => linkHtml(e, st.nodes.get(e.to))).join('') : none;
  // the users in the other files of the workspace (open or only listed), where the object is an external placeholder
  const elsewhere = [];
  for (const u of usersInWorkspace(n, st)) for (const e of u.edges) elsewhere.push({ e, n: u.n, place: u.place });
  html += '<h3>' + esc(t(MSG.DETAILS_USED_BY, inn.length + elsewhere.length)) + '</h3>';
  html += inn.length || elsewhere.length
    ? inn.map(e => linkHtml(e, st.nodes.get(e.from))).join('') + elsewhere.map(u => linkHtml(u.e, u.n, u.place)).join('')
    : none;
  $(ID.DETAILS_CONTENT).innerHTML = html;
  panel.classList.remove(CLS.HIDDEN);
}

const COLLAPSE_GLYPH = '»', EXPAND_GLYPH = '«';

/** Collapses the panel to a strip (or expands it back); remembered across sessions. */
export function setDetailsCollapsed(collapsed) {
  $(ID.DETAILS).classList.toggle(CLS.COLLAPSED, collapsed);
  const toggle = $(ID.DETAILS_TOGGLE);
  toggle.textContent = collapsed ? EXPAND_GLYPH : COLLAPSE_GLYPH;
  toggle.title = t(collapsed ? MSG.DETAILS_EXPAND : MSG.DETAILS_COLLAPSE);
  updateSplitters();
  try { localStorage.setItem(STORAGE_KEY.DETAILS_COLLAPSED, collapsed ? STORAGE_TRUE : STORAGE_FALSE); } catch (e) { /* storage unavailable */ }
}

export const isDetailsCollapsed = () => $(ID.DETAILS).classList.contains(CLS.COLLAPSED);

export function toggleDetails() {
  setDetailsCollapsed(!isDetailsCollapsed());
}

/** Restores the collapsed state remembered in the browser. */
export function initDetails() {
  let collapsed = false;
  try { collapsed = localStorage.getItem(STORAGE_KEY.DETAILS_COLLAPSED) === STORAGE_TRUE; } catch (e) { /* storage unavailable */ }
  setDetailsCollapsed(collapsed);
}

/** One link row: its label, its cardinality when it has one, and the node at the other end (with its file when it is another file's); optional links are marked. */
function linkHtml(edge, target, place = null) {
  const card = cardinalityText(edge);
  const title = place ? t(MSG.GRAPH_KIND_IN_FILE, kindLabel(target.kind), place.fileName) + ' ' + target.name : t(MSG.GRAPH_NODE_TITLE, kindLabel(target.kind), target.name);
  return '<div class="' + CLS.LINK + (isOptional(edge) ? ' ' + CLS.OPTIONAL : '') + '"' + dataAttr(DATA.ID, target.id)
    + placeAttributes(place, session.active.workspace, dataAttr, DATA) + ' title="' + esc(title) + '">'
    + '<span class="' + CLS.LINK_LABEL + '">' + esc(edge.label) + '</span>'
    + (card ? '<span class="' + CLS.CARDINALITY + '">' + esc(card) + '</span>' : '')
    // the xs:sequence / xs:choice / xs:all the element sits in, when it sits in one
    + (edge.compositor ? '<span class="' + CLS.COMPOSITOR + '">' + esc(edge.compositor) + '</span>' : '')
    + '<span class="' + CLS.DOT + ' ' + target.kind + '"></span>'
    + '<span class="' + CLS.LINK_TARGET + '">' + esc(target.name) + (place ? ' <span class="' + CLS.META + '">' + esc(place.fileName) + '</span>' : '') + '</span></div>';
}
