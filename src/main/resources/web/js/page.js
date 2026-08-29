/** Drawing the whole page from the active tab, and switching between the graph and text views. */
import { VIEW } from './constants.js';
import { renderDetails } from './details.js';
import { $, CLS, DATA, ID, selector } from './dom.js';
import { renderGraph } from './graph.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { renderNodeList, renderSchemaInfo } from './sidebar.js';
import { session } from './state.js';
import { renderTabBar } from './tabs.js';
import { highlightTextLine, renderText } from './text-view.js';

/** Redraws everything from the active tab's state. */
export function renderPage() {
  const st = session.active;
  const loaded = !!st.model;
  document.title = loaded ? t(MSG.APP_TITLE_WITH_FILE, st.fileName) : t(MSG.APP_TITLE);
  $(ID.FILE_NAME).textContent = loaded ? st.fileName : t(MSG.STATUS_NO_FILE);
  $(ID.FILE_NAME).title = loaded ? (st.path || st.fileName) : '';
  $(ID.SEARCH).value = st.filter;
  $(ID.BACK_BUTTON).disabled = st.history.length === 0;
  if (loaded) {
    renderSchemaInfo();
    renderNodeList();
    renderText();
    renderGraph();
    renderDetails();
    highlightTextLine(false);
  } else {
    $(ID.SCHEMA_INFO).innerHTML = '';
    $(ID.NODE_LIST).innerHTML = '';
    $(ID.TEXT).innerHTML = '';
    $(ID.GRAPH_CANVAS).innerHTML = '';
    $(ID.DETAILS).classList.add(CLS.HIDDEN);
  }
  renderTabBar();
  showView(st.view);
  $(ID.TEXT).scrollTop = st.scroll.text;
  $(ID.GRAPH_CANVAS).scrollTop = st.scroll.graphTop;
  $(ID.GRAPH_CANVAS).scrollLeft = st.scroll.graphLeft;
}

/** Shows the graph or the text view of the active tab (VIEW.GRAPH / VIEW.TEXT). */
export function showView(view) {
  const st = session.active;
  st.view = view;
  document.querySelectorAll(selector(CLS.VIEW_TAB)).forEach(b => b.classList.toggle(CLS.ACTIVE, b.dataset[DATA.VIEW] === view));
  const loaded = !!st.model;
  $(ID.EMPTY).classList.toggle(CLS.HIDDEN, loaded);
  $(ID.GRAPH).classList.toggle(CLS.HIDDEN, !loaded || view !== VIEW.GRAPH);
  $(ID.TEXT).classList.toggle(CLS.HIDDEN, !loaded || view !== VIEW.TEXT);
  $(ID.DETAILS).classList.toggle(CLS.HIDDEN, !loaded || !st.selected);
  $(ID.EXPORT_BUTTON).disabled = !loaded;
  if (loaded && view === VIEW.TEXT) highlightTextLine(true);
}
