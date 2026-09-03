/** Drawing the whole page from the active tab, and switching between the graph and text views. */
import { VIEW } from './constants.js';
import { renderCompare } from './compare.js';
import { renderDetails } from './details.js';
import { canValidate, renderValidation } from './validate.js';
import { listedOnly } from './workspace-actions.js';
import { $, CLS, DATA, ID, selector } from './dom.js';
import { renderGraph } from './graph.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { updateSplitters } from './panels.js';
import { renderNodeList, renderSchemaInfo } from './sidebar.js';
import { session } from './state.js';
import { compareTitle, renderNavigation, validationTitle } from './tabs.js';
import { highlightTextLine, renderText } from './text-view.js';
import { renderModel } from './model-view.js';

/** Redraws everything from the active tab's state. */
export function renderPage() {
  const st = session.active;
  const loaded = !!st.model;
  const shown = st.compare ? compareTitle(st) : st.validation ? validationTitle(st) : loaded ? st.fileName : null;
  document.title = shown ? t(MSG.APP_TITLE_WITH_FILE, shown) : t(MSG.APP_TITLE);
  $(ID.FILE_NAME).textContent = shown || t(MSG.STATUS_NO_FILE);
  $(ID.FILE_NAME).title = loaded ? (st.path || st.fileName) : '';
  $(ID.SEARCH).value = st.filter;
  $(ID.BACK_BUTTON).disabled = st.history.length === 0;
  if (loaded) {
    renderSchemaInfo();
    renderNodeList();
    renderText();
    renderGraph();
    renderModel();
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
  if (st.compare) renderCompare();
  if (st.validation) renderValidation();
  showView(st.view);
  $(ID.TEXT).scrollTop = st.scroll.text;
  $(ID.GRAPH_CANVAS).scrollTop = st.scroll.graphTop;
  $(ID.GRAPH_CANVAS).scrollLeft = st.scroll.graphLeft;
  $(ID.MODEL_CANVAS).scrollTop = st.scroll.modelTop;
  $(ID.MODEL_CANVAS).scrollLeft = st.scroll.modelLeft;
}

/** Shows the graph, the model or the text view of the active tab (VIEW.GRAPH / VIEW.MODEL / VIEW.TEXT) — or the workspace comparison / the validation, which takes the whole page while it is on. */
export function showView(view) {
  const st = session.active;
  st.view = view;
  document.querySelectorAll(selector(CLS.VIEW_TAB)).forEach(b => b.classList.toggle(CLS.ACTIVE, b.dataset[DATA.VIEW] === view));
  const loaded = !!st.model, comparing = !!st.compare || !!st.validation;   // a whole-page tab
  $(ID.COMPARE).classList.toggle(CLS.HIDDEN, !st.compare);
  $(ID.VALIDATION).classList.toggle(CLS.HIDDEN, !st.validation);
  $(ID.SIDEBAR).classList.toggle(CLS.HIDDEN, comparing);
  $(ID.EMPTY).classList.toggle(CLS.HIDDEN, loaded || comparing);
  $(ID.GRAPH).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.GRAPH);
  $(ID.MODEL).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.MODEL);
  $(ID.TEXT).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.TEXT);
  $(ID.TEXT_FIND).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.TEXT);
  $(ID.DETAILS).classList.toggle(CLS.HIDDEN, !loaded || comparing);   // the schema header, then the selected object
  $(ID.EXPORT_BUTTON).disabled = !loaded || comparing;
  $(ID.EXPORT_SVG_BUTTON).disabled = !loaded || comparing || view === VIEW.TEXT;   // the graph and the model are SVGs
  updateSplitters();
  $(ID.MENU_VALIDATE).disabled = !canValidate();
  $(ID.MENU_OPEN_ALL).disabled = comparing || !listedOnly().length;
  if (loaded && !comparing && view === VIEW.TEXT) highlightTextLine(true);
}
