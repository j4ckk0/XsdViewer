/**
 * Redrawing the whole page from the active tab: the window's title and the file name, the panels, the
 * navigation, then the place being read and its drawing ({@code view-router.js}). What every action
 * ends with.
 */
import { $ } from './dom.js';
import { CLS, ID } from './dom-names.js';
import { renderDetails } from './details.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { renderNodeList, renderSchemaInfo } from './sidebar.js';
import { session } from './state.js';
import { renderNavigation, validationTitle } from './tabs.js';
import { highlightTextLine, renderText } from './text-view.js';
import { renderValidation } from './validate.js';
import { currentView, renderComparison, showView } from './view-router.js';

/** Redraws everything from the active tab's state. */
export function renderPage() {
  const st = session.active;
  const loaded = !!st.model;
  const shown = session.comparison.shown ? t(MSG.COMPARISON_CHIP) : st.validation ? validationTitle(st) : loaded ? st.fileName : null;
  document.title = shown ? t(MSG.APP_TITLE_WITH_FILE, shown) : t(MSG.APP_TITLE);
  $(ID.FILE_NAME).textContent = shown || t(MSG.STATUS_NO_FILE);
  $(ID.FILE_NAME).title = loaded ? (st.path || st.fileName) : '';
  $(ID.SEARCH).value = st.filter;
  $(ID.BACK_BUTTON).disabled = st.history.length === 0;
  if (loaded) {
    renderSchemaInfo();
    renderNodeList();
    renderText();
    renderDetails();
    highlightTextLine(false);
  } else {
    $(ID.SCHEMA_INFO_CONTENT).innerHTML = '';
    $(ID.NODE_LIST).innerHTML = '';
    $(ID.TEXT).innerHTML = '';
    $(ID.GRAPH_CANVAS).innerHTML = '';
    $(ID.MODEL_CANVAS).innerHTML = '';
    $(ID.DETAILS).classList.add(CLS.HIDDEN);
  }
  renderNavigation();
  if (st.validation) renderValidation();
  if (session.comparison.shown) renderComparison();
  showView(currentView());
  $(ID.TEXT).scrollTop = st.scroll.text;
  $(ID.GRAPH_CANVAS).scrollTop = st.scroll.graphTop;
  $(ID.GRAPH_CANVAS).scrollLeft = st.scroll.graphLeft;
  $(ID.MODEL_CANVAS).scrollTop = st.scroll.modelTop;
  $(ID.MODEL_CANVAS).scrollLeft = st.scroll.modelLeft;
}
