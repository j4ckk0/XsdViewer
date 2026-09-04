/** Drawing the whole page from the active tab, and switching between its views. */
import { VIEW, ZOOMABLE_VIEWS } from './constants.js';
import { renderCompare } from './compare.js';
import { renderDetails } from './details.js';
import { canValidate, renderValidation } from './validate.js';
import { listedOnly } from './workspace-files.js';
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
import { comparedPair, renderObjectCompare } from './object-compare.js';
import { applyZoom } from './zoom.js';

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
  $(ID.EMPTY).classList.toggle(CLS.HIDDEN, loaded || comparing || view === VIEW.COMPARE);
  $(ID.GRAPH).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.GRAPH);
  $(ID.MODEL).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.MODEL);
  $(ID.OBJECT_COMPARE).classList.toggle(CLS.HIDDEN, comparing || view !== VIEW.COMPARE);   // it draws marked declarations, so it stands without a file
  $(ID.TEXT).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.TEXT);
  $(ID.TEXT_FIND).classList.toggle(CLS.HIDDEN, !loaded || comparing || view !== VIEW.TEXT);
  // the schema header, then the selected object; the comparison of two declarations wants the width instead
  $(ID.DETAILS).classList.toggle(CLS.HIDDEN, !loaded || comparing || view === VIEW.COMPARE);
  // the Compare view exports the two models as one picture, so it needs two to draw rather than a file
  const nothingToExport = comparing || (view === VIEW.COMPARE ? !comparedPair() : !loaded);
  $(ID.EXPORT_BUTTON).disabled = nothingToExport;
  $(ID.EXPORT_SVG_BUTTON).disabled = nothingToExport || view === VIEW.TEXT;   // the graph, the model and the comparison are SVGs
  $(ID.ZOOM_CONTROLS).classList.toggle(CLS.HIDDEN, comparing || !ZOOMABLE_VIEWS.has(view) || (!loaded && view !== VIEW.COMPARE));
  updateSplitters();
  $(ID.MENU_VALIDATE).disabled = !canValidate();
  $(ID.MENU_OPEN_ALL).disabled = comparing || !listedOnly().length;
  renderMainView();   // drawn now that it is shown, so that it is laid out for the room it has
  if (loaded && !comparing && view === VIEW.TEXT) highlightTextLine(true);
}

/**
 * Draws the main view of the active tab: the graph, or the model. They are laid out for the room
 * they have, so they are drawn when they are shown and again whenever that room, or what they draw,
 * changes — the selection, a panel resized, the window resized. Nothing to draw for the text view
 * (it is written once with the file), for a comparison or a validation (they take the whole page).
 */
export function renderMainView() {
  const st = session.active;
  if (st.compare || st.validation) return;
  if (st.view === VIEW.COMPARE) { renderObjectCompare(); return; }
  if (!st.model) return;
  if (st.view === VIEW.GRAPH) renderGraph();
  else if (st.view === VIEW.MODEL) renderModel();
  applyZoom();   // drawing writes a new SVG, which takes the tab's level
}

/** The Compare view stands on the marked declarations, not on the active tab's file: it is drawn whatever the tab holds. */
export function renderComparedObjects() {
  if (session.active.view === VIEW.COMPARE) renderObjectCompare();
}
