/** The right panel: the selected object, its documentation, its links out and the objects using it. */
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { t } from './i18n.js';
import { kindLabel } from './kind-labels.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';

export function renderDetails() {
  const st = session.active;
  const panel = $(ID.DETAILS);
  if (!st.selected) { panel.classList.add(CLS.HIDDEN); return; }
  const n = st.nodes.get(st.selected);
  let html = '<h2>' + esc(n.name) + '</h2><span class="' + CLS.BADGE + ' ' + n.kind + '">' + esc(kindLabel(n.kind)) + '</span>';
  html += '<div class="' + CLS.META + '">'
    + (n.line > 0 ? '<a' + dataAttr(DATA.LINE, n.line) + '>' + esc(t(MSG.DETAILS_SHOW_IN_TEXT, n.line)) + '</a>' : esc(t(MSG.DETAILS_NO_DECLARATION)))
    + '</div>';
  if (n.doc) html += '<div class="' + CLS.DOC + '">' + esc(n.doc) + '</div>';

  const out = (st.outEdges.get(n.id) || []).filter(e => st.nodes.has(e.to));
  const inn = (st.inEdges.get(n.id) || []).filter(e => st.nodes.has(e.from));
  const none = '<div class="' + CLS.META + '">' + esc(t(MSG.DETAILS_NONE)) + '</div>';
  html += '<h3>' + esc(t(MSG.DETAILS_LINKS_OUT, out.length)) + '</h3>';
  html += out.length ? out.map(e => linkHtml(e.label, st.nodes.get(e.to))).join('') : none;
  html += '<h3>' + esc(t(MSG.DETAILS_USED_BY, inn.length)) + '</h3>';
  html += inn.length ? inn.map(e => linkHtml(e.label, st.nodes.get(e.from))).join('') : none;
  panel.innerHTML = html;
  panel.classList.remove(CLS.HIDDEN);
}

function linkHtml(label, target) {
  return '<div class="' + CLS.LINK + '"' + dataAttr(DATA.ID, target.id) + ' title="' + esc(t(MSG.GRAPH_NODE_TITLE, kindLabel(target.kind), target.name)) + '">'
    + '<span class="' + CLS.LINK_LABEL + '">' + esc(label) + '</span><span class="' + CLS.DOT + ' ' + target.kind + '"></span>'
    + '<span class="' + CLS.LINK_TARGET + '">' + esc(target.name) + '</span></div>';
}
