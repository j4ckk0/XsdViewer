/** Drawing the whole page from the active tab, and switching between its views. */
import { COMPARE_SECTION, VIEW, ZOOMABLE_VIEWS } from './constants.js';
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
import { renderNavigation, validationTitle } from './tabs.js';
import { highlightTextLine, renderText } from './text-view.js';
import { renderModel } from './model-view.js';
import { comparedPair, renderObjectCompare } from './object-compare.js';
import { applyZoom } from './zoom.js';

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
  showView(st.view);
  $(ID.TEXT).scrollTop = st.scroll.text;
  $(ID.GRAPH_CANVAS).scrollTop = st.scroll.graphTop;
  $(ID.GRAPH_CANVAS).scrollLeft = st.scroll.graphLeft;
  $(ID.MODEL_CANVAS).scrollTop = st.scroll.modelTop;
  $(ID.MODEL_CANVAS).scrollLeft = st.scroll.modelLeft;
}

/**
 * Shows one of the views of the active tab's file (VIEW.GRAPH / VIEW.MODEL / VIEW.TEXT), unless the
 * comparison or a validation is the place being read: those take the whole page, having no file.
 */
export function showView(view) {
  const st = session.active;
  st.view = view;
  document.querySelectorAll(selector(CLS.VIEW_TAB)).forEach(b => b.classList.toggle(CLS.ACTIVE, b.dataset[DATA.VIEW] === view));
  const comparing = session.comparison.shown, elsewhere = comparing || !!st.validation;   // no file is being read
  const loaded = !!st.model;
  $(ID.COMPARISON).classList.toggle(CLS.HIDDEN, !comparing);
  $(ID.COMPARE).classList.toggle(CLS.HIDDEN, !comparing || session.comparison.section !== COMPARE_SECTION.FILES);
  $(ID.OBJECT_COMPARE).classList.toggle(CLS.HIDDEN, !comparing || session.comparison.section !== COMPARE_SECTION.OBJECTS);
  $(ID.VALIDATION).classList.toggle(CLS.HIDDEN, !st.validation || comparing);
  $(ID.TABBAR).classList.toggle(CLS.HIDDEN, comparing);   // the comparison holds no file, so it has no tabs
  $(ID.VIEW_TABS).classList.toggle(CLS.HIDDEN, elsewhere);
  $(ID.SIDEBAR).classList.toggle(CLS.HIDDEN, elsewhere);
  $(ID.EMPTY).classList.toggle(CLS.HIDDEN, loaded || elsewhere);
  $(ID.GRAPH).classList.toggle(CLS.HIDDEN, !loaded || elsewhere || view !== VIEW.GRAPH);
  $(ID.MODEL).classList.toggle(CLS.HIDDEN, !loaded || elsewhere || view !== VIEW.MODEL);
  $(ID.TEXT).classList.toggle(CLS.HIDDEN, !loaded || elsewhere || view !== VIEW.TEXT);
  $(ID.TEXT_FIND).classList.toggle(CLS.HIDDEN, !loaded || elsewhere || view !== VIEW.TEXT);
  $(ID.DETAILS).classList.toggle(CLS.HIDDEN, !loaded || elsewhere);
  // the comparison exports its two models as one picture, so it needs two to draw rather than a file
  const objects = comparing && session.comparison.section === COMPARE_SECTION.OBJECTS;
  const nothingToExport = st.validation || (comparing ? !objects || !comparedPair() : !loaded);
  $(ID.EXPORT_BUTTON).disabled = nothingToExport;
  $(ID.EXPORT_SVG_BUTTON).disabled = nothingToExport || (!comparing && view === VIEW.TEXT);
  $(ID.ZOOM_CONTROLS).classList.toggle(CLS.HIDDEN, objects ? !comparedPair() : elsewhere || !ZOOMABLE_VIEWS.has(view) || !loaded);
  updateSplitters();
  $(ID.MENU_VALIDATE).disabled = !canValidate();
  $(ID.MENU_OPEN_ALL).disabled = elsewhere || !listedOnly().length;
  renderMainView();   // drawn now that it is shown, so that it is laid out for the room it has
  if (loaded && !elsewhere && view === VIEW.TEXT) highlightTextLine(true);
}

/** Draws the comparison: the section being read, and the chip's state on the bar. */
export function renderComparison() {
  if (!session.comparison.shown) return;
  if (session.comparison.section === COMPARE_SECTION.OBJECTS) renderObjectCompare();
  else renderCompare();
}

/**
 * Draws the main view of the active tab: the graph, or the model. They are laid out for the room
 * they have, so they are drawn when they are shown and again whenever that room, or what they draw,
 * changes — the selection, a panel resized, the window resized. Nothing to draw for the text view
 * (it is written once with the file), for a comparison or a validation (they take the whole page).
 */
export function renderMainView() {
  const st = session.active;
  if (session.comparison.shown) { renderComparison(); return; }
  if (st.validation || !st.model) return;
  if (st.view === VIEW.GRAPH) renderGraph();
  else if (st.view === VIEW.MODEL) renderModel();
  applyZoom();   // drawing writes a new SVG, which takes the tab's level
}

/** The Declarations section stands on the two sides, not on a file: it is drawn whenever they change. */
export function renderComparedObjects() {
  if (session.comparison.shown && session.comparison.section === COMPARE_SECTION.OBJECTS) renderObjectCompare();
}
