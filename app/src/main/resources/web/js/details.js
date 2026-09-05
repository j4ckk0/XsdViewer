/** The right panel, under the schema header (sidebar.js): the selected object, its expression (a Schematron rule's context, an assertion's test), its documentation, the values it enumerates, its links out and the objects using it. Collapsible to a strip. */
import { cardinalityText, isOptional } from './cardinality.js';
import { NODE_KIND, STORAGE_KEY, TEXT, kindOfId, nameOfId } from './constants.js';
import { SIDE, SIDES, heldBy, sideOf } from './comparison-state.js';
import { placeAttributes, usersInWorkspace } from './declaration-lookup.js';
import { $, dataAttr, esc } from './dom.js';
import { CLS, DATA, ID } from './dom-names.js';
import { GLYPHS, foldable } from './foldable.js';
import { updateSplitters } from './panels.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

/**
 * Draws what the panel holds. Whether the panel is shown belongs to {@link showView}: it is a panel
 * of a workspace, so the comparison and a validation, which take the whole page, do without it.
 */
export function renderDetails() {
  const st = session.active;
  if (!st.selected) { $(ID.DETAILS_CONTENT).innerHTML = ''; renderCompareGroup(null); return; }
  const n = st.nodes.get(st.selected);
  let html = '<h2>' + esc(n.name) + '</h2><span class="' + CLS.BADGE + ' ' + n.kind + '">' + esc(kindLabel(n.kind)) + '</span>';
  html += '<div class="' + CLS.META + '">'
    + (n.line > 0 ? '<a' + dataAttr(DATA.LINE, n.line) + '>' + esc(t(MSG.DETAILS_SHOW_IN_TEXT, n.line)) + '</a>' : esc(t(MSG.DETAILS_NO_DECLARATION)))
    + '</div>';
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
  renderCompareGroup(n);
}

/**
 * The Compare group: which side of the comparison holds this declaration, and the comparison itself.
 * An external placeholder is declared in another file, so it has nothing to put on a side.
 */
function renderCompareGroup(n) {
  const st = session.active;
  const shown = !!n && n.kind !== NODE_KIND.EXTERNAL;
  $(ID.COMPARE_GROUP).classList.toggle(CLS.HIDDEN, !shown);
  if (!shown) { $(ID.COMPARE_SIDES).innerHTML = ''; return; }
  const on = sideOf(st, n.id);
  const label = { [SIDE.LEFT]: MSG.OBJECT_MARK_LEFT, [SIDE.RIGHT]: MSG.OBJECT_MARK_RIGHT };
  const sideName = { [SIDE.LEFT]: MSG.OBJECT_SIDE_LEFT, [SIDE.RIGHT]: MSG.OBJECT_SIDE_RIGHT };
  $(ID.COMPARE_SIDES).innerHTML = '<div class="' + CLS.META + '">' + esc(t(MSG.DETAILS_COMPARE_HINT)) + '</div>'
    + '<div class="' + CLS.MARK_BUTTONS + '">' + SIDES.map(side => sideButton(side, on === side, t(sideName[side]), t(label[side]))).join('') + '</div>';
}

/** What a side holds, named as the comparison names it: the object, then the file it was read from. */
const heldName = (mark) => t(MSG.MODEL_TITLE, kindLabel(kindOfId(mark.id)), nameOfId(mark.id)) + TEXT.TOAST_SEPARATOR + mark.fileName;

/**
 * One side's button, in one of its three states: it holds the object being read (coloured, a click
 * takes it off), it holds another one (marked as taken, and its tooltip says which — a click puts
 * this object there instead), or it is free.
 */
function sideButton(side, isThisOne, sideName, label) {
  const held = heldBy(side);
  const taken = !isThisOne && !!held;
  const title = isThisOne ? t(MSG.OBJECT_MARK_OFF_TITLE, sideName)
    : taken ? t(MSG.OBJECT_MARK_TAKEN_TITLE, sideName, heldName(held))
      : t(MSG.OBJECT_MARK_TITLE, sideName);
  return '<button class="' + CLS.MARK_BUTTON + ' ' + side + (isThisOne ? ' ' + CLS.MARKED : taken ? ' ' + CLS.TAKEN : '') + '" type="button"'
    + dataAttr(DATA.SIDE, side) + ' title="' + esc(title) + '">' + esc(label) + '</button>';
}

/** The Compare group folds to its title line; unfolded until the user folds it once. */
export const compareGroup = foldable({
  element: ID.COMPARE_GROUP, toggle: ID.COMPARE_GROUP_TOGGLE, storageKey: STORAGE_KEY.COMPARE_GROUP_COLLAPSED,
  titles: { fold: MSG.COMPARE_GROUP_COLLAPSE, unfold: MSG.COMPARE_GROUP_EXPAND },
});

/** The panel folds to a strip at the side; the splitter beside it follows. */
export const detailsPanel = foldable({
  element: ID.DETAILS, toggle: ID.DETAILS_TOGGLE, storageKey: STORAGE_KEY.DETAILS_COLLAPSED,
  titles: { fold: MSG.DETAILS_COLLAPSE, unfold: MSG.DETAILS_EXPAND }, glyphs: GLYPHS.PANEL, onChange: updateSplitters,
});

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
