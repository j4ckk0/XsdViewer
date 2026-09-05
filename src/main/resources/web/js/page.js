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
import { comparedPair } from './comparison.js';
import { renderObjectCompare } from './object-compare.js';
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
  showView(currentView());
  $(ID.TEXT).scrollTop = st.scroll.text;
  $(ID.GRAPH_CANVAS).scrollTop = st.scroll.graphTop;
  $(ID.GRAPH_CANVAS).scrollLeft = st.scroll.graphLeft;
  $(ID.MODEL_CANVAS).scrollTop = st.scroll.modelTop;
  $(ID.MODEL_CANVAS).scrollLeft = st.scroll.modelLeft;
}

/** The view being read: the comparison's own while it is the place shown, else the active tab's. */
const currentView = () => (session.comparison.shown ? session.comparison.view : session.active.view);

/**
 * What the main area is given to: a file's view, one of the comparison's two sections, a
 * validation, or nothing. Everything the page shows or hides follows from which one it is.
 */
const PLACE = { FILE: 'file', EMPTY: 'empty', VALIDATION: 'validation', COMPARISON_OBJECTS: 'objects', COMPARISON_FILES: 'files' };

function placeShown() {
  const st = session.active;
  if (session.comparison.shown) return session.comparison.section === COMPARE_SECTION.OBJECTS ? PLACE.COMPARISON_OBJECTS : PLACE.COMPARISON_FILES;
  if (st.validation) return PLACE.VALIDATION;
  return st.model ? PLACE.FILE : PLACE.EMPTY;
}

/**
 * What each place shows of the page. The sidebar and the tab bar belong to a workspace, so they go
 * wherever a workspace is being read, a file loaded or not; the details panel needs a file. The view
 * tabs and the exports go with whatever is drawn three ways — a file, or the two declarations of the
 * comparison's Objects section, which is why that section has them and the Files section, a list of
 * file pairs, has not.
 */
const SHOWS = {
  [PLACE.FILE]:               { sidebar: true,  details: true,  tabbar: true,  viewTabs: true,  drawn: true,  comparison: false, validation: false },
  [PLACE.EMPTY]:              { sidebar: true,  details: false, tabbar: true,  viewTabs: true,  drawn: false, comparison: false, validation: false },
  [PLACE.VALIDATION]:         { sidebar: false, details: false, tabbar: true,  viewTabs: false, drawn: false, comparison: false, validation: true },
  [PLACE.COMPARISON_OBJECTS]: { sidebar: false, details: false, tabbar: false, viewTabs: true,  drawn: true,  comparison: true,  validation: false },
  [PLACE.COMPARISON_FILES]:   { sidebar: false, details: false, tabbar: false, viewTabs: false, drawn: false, comparison: true,  validation: false },
};

/**
 * Shows one of the views (VIEW.MODEL / VIEW.TEXT / VIEW.GRAPH), of the active tab's file or of the
 * two declarations the comparison holds — the comparison keeps a view of its own, so switching there
 * leaves every tab where its reader left it — and shows or hides every part of the page for the
 * place being read.
 */
export function showView(view) {
  const st = session.active;
  const comparing = session.comparison.shown;
  if (comparing) session.comparison.view = view; else st.view = view;
  document.querySelectorAll(selector(CLS.VIEW_TAB)).forEach(b => b.classList.toggle(CLS.ACTIVE, b.dataset[DATA.VIEW] === view));
  const place = placeShown(), shows = SHOWS[place];
  // a drawing to zoom and export: the model and the graph are SVGs, the text is not — and the comparison needs two declarations to draw anything
  const drawing = shows.drawn && ZOOMABLE_VIEWS.has(view) && (place !== PLACE.COMPARISON_OBJECTS || !!comparedPair());
  const show = (id, shown) => $(id).classList.toggle(CLS.HIDDEN, !shown);
  show(ID.COMPARISON, shows.comparison);
  show(ID.COMPARE, place === PLACE.COMPARISON_FILES);
  show(ID.OBJECT_COMPARE, place === PLACE.COMPARISON_OBJECTS);
  show(ID.VALIDATION, shows.validation);
  show(ID.TABBAR, shows.tabbar);
  show(ID.VIEW_TABS, shows.viewTabs);
  show(ID.SIDEBAR, shows.sidebar);
  show(ID.DETAILS, shows.details);
  show(ID.EMPTY, place === PLACE.EMPTY);
  show(ID.GRAPH, place === PLACE.FILE && view === VIEW.GRAPH);
  show(ID.MODEL, place === PLACE.FILE && view === VIEW.MODEL);
  show(ID.TEXT, place === PLACE.FILE && view === VIEW.TEXT);
  show(ID.TEXT_FIND, place === PLACE.FILE && view === VIEW.TEXT);
  show(ID.ZOOM_CONTROLS, drawing);
  // a file's text exports as a picture painted by text-export.js, but has no SVG; the comparison's text draws nothing
  $(ID.EXPORT_BUTTON).disabled = !(drawing || (place === PLACE.FILE && view === VIEW.TEXT));
  $(ID.EXPORT_SVG_BUTTON).disabled = !drawing;
  updateSplitters();
  $(ID.MENU_VALIDATE).disabled = !canValidate();
  $(ID.MENU_OPEN_ALL).disabled = place !== PLACE.FILE && place !== PLACE.EMPTY || !listedOnly().length;
  renderMainView();   // drawn now that it is shown, so that it is laid out for the room it has
  if (place === PLACE.FILE && view === VIEW.TEXT) highlightTextLine(true);
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

/** The Objects section stands on the two sides, not on a file: it is drawn whenever they change. */
export function renderComparedObjects() {
  if (session.comparison.shown && session.comparison.section === COMPARE_SECTION.OBJECTS) renderObjectCompare();
}
