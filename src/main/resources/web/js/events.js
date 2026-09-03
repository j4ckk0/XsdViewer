/** Wiring of the page's controls to the actions: menu, tabs, keyboard, drag and drop, clicks in the views. */
import { DATA_TRANSFER_FILES, DROP_EFFECT_COPY, KEY, MIDDLE_BUTTON, NODE_KIND, PATH_SEPARATOR, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE, TEXT, VIEW } from './constants.js';
import { $, CLS, DATA, ID, selector } from './dom.js';
import { closeAbout, showAbout } from './about.js';
import { clearSelection, initOptions, openPairTab, rememberOptions, renderCompare, setAllDetails, startCompare, toggleDetail, toggleSelection } from './compare.js';
import { closeAll, closeFile, openFiles, openSchemas, quit } from './file-actions.js';
import { closeActiveWorkspace, openAllListed, openBrowserFolder, openEntriesAsWorkspace, openFolder, openWorkspace, saveWorkspace, startWorkspace } from './workspace-actions.js';
import { initDetails, toggleDetails } from './details.js';
import { fileListClick, initFiles, isFilesCollapsed, renderFileList, setAllUnfolded, setFilesCollapsed, toggleFiles } from './file-list.js';
import { ensureTab } from './file-tabs.js';
import { renderGraph } from './graph.js';
import { isShowAllKindsClick, kindOfClick, showAllKinds, toggleKind } from './kind-filter.js';
import { categoryOfClick, isShowAllClick, showAllLinks, toggleCategory } from './link-filter.js';
import { filesOfEntries } from './folder-library.js';
import { followExternal, goBack, jumpTo, select } from './navigation.js';
import { renderPage, showView } from './page.js';
import { exportPng, exportSvg } from './png-export.js';
import { initSchemaInfo, renderNodeList, setAllGroupsExpanded, toggleGroup, toggleSchemaInfo } from './sidebar.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { session } from './state.js';
import { clearFind, findStep, focusFind, refreshFind } from './text-find.js';
import { toggleTheme } from './theme.js';
import * as validation from './validate.js';
import { toast } from './toast.js';
import { activateTab, closeTab, closeWorkspace, newTab, renderNavigation, tabToShow } from './tabs.js';
import { toggleAutoStop } from './settings.js';


export function wireEvents() {
  wireMenus();
  wireFileMenu();
  wireSettingsMenu();
  wireHelpMenu();
  wireDocumentTabs();
  wireKeyboard();
  wireDragAndDrop();
  wireViews();
  wireSearch();
  wireSelectionSources();
}

/** The drop-down menus of the top bar: one open at a time, closed by a click elsewhere or Escape. */
const MENUS = [[ID.FILE_MENU_BUTTON, ID.FILE_MENU], [ID.SETTINGS_MENU_BUTTON, ID.SETTINGS_MENU], [ID.HELP_MENU_BUTTON, ID.HELP_MENU],
  [ID.LINK_MENU_BUTTON, ID.LINK_MENU], [ID.TYPE_MENU_BUTTON, ID.TYPE_MENU]];
const closeMenus = () => MENUS.forEach(([, menu]) => $(menu).classList.add(CLS.HIDDEN));

function wireMenus() {
  for (const [button, menu] of MENUS) {
    $(button).addEventListener('click', (e) => {
      e.stopPropagation();
      const open = !$(menu).classList.contains(CLS.HIDDEN);
      closeMenus();
      if (!open) $(menu).classList.remove(CLS.HIDDEN);
    });
  }
  document.addEventListener('click', closeMenus);
  document.addEventListener('keydown', (e) => { if (e.key === KEY.ESCAPE) closeMenus(); });
}

function wireSettingsMenu() {
  $(ID.MENU_AUTO_STOP).addEventListener('click', () => { closeMenus(); toggleAutoStop(); });
  $(ID.MENU_THEME).addEventListener('click', () => { closeMenus(); toggleTheme(); });
}

function wireHelpMenu() {
  $(ID.MENU_ABOUT).addEventListener('click', () => { closeMenus(); showAbout(); });
  $(ID.MENU_VALIDATE).addEventListener('click', () => { closeMenus(); validation.validateFile(); });
  $(ID.MENU_OPEN_ALL).addEventListener('click', () => { closeMenus(); openAllListed(); });
  $(ID.ABOUT_CLOSE).addEventListener('click', closeAbout);
}

function wireFileMenu() {
  const closeMenu = closeMenus;
  $(ID.MENU_OPEN).addEventListener('click', () => { closeMenu(); openSchemas(); });
  $(ID.MENU_NEW_WORKSPACE).addEventListener('click', () => { closeMenu(); startWorkspace(); });
  $(ID.MENU_OPEN_WORKSPACE).addEventListener('click', () => { closeMenu(); openWorkspace(); });
  $(ID.MENU_CLOSE_WORKSPACE).addEventListener('click', () => { closeMenu(); closeActiveWorkspace(); });
  $(ID.MENU_SAVE_WORKSPACE).addEventListener('click', () => { closeMenu(); saveWorkspace(); });
  $(ID.MENU_NEW_TAB).addEventListener('click', () => { closeMenu(); activateTab(newTab()); renderPage(); });
  $(ID.MENU_CLOSE).addEventListener('click', () => { closeMenu(); closeFile(); });
  $(ID.MENU_CLOSE_ALL).addEventListener('click', () => { closeMenu(); closeAll(); });
  $(ID.MENU_QUIT).addEventListener('click', () => { closeMenu(); quit(); });
  $(ID.FILE_INPUT).addEventListener('change', (e) => { openFiles([...e.target.files]); e.target.value = ''; });
  $(ID.FILE_INPUT).addEventListener('cancel', () => { session.pendingJump = null; });
  $(ID.VALIDATE_INPUT).addEventListener('change', async (e) => {   // the browser's file input: a document for the shown schema, or another one for the shown validation
    const f = e.target.files[0]; e.target.value = '';
    if (!f) return;
    if (session.active.validation) validation.replaceDocument(f.name, await f.text());
    else validation.validateText(f.name, await f.text());
  });
  $(ID.MENU_OPEN_FOLDER).addEventListener('click', () => { closeMenu(); openFolder(); });
  $(ID.FOLDER_INPUT).addEventListener('change', (e) => {
    const files = [...e.target.files];
    const folder = files[0] ? (files[0].webkitRelativePath.split(PATH_SEPARATOR)[0] || '') : '';
    openBrowserFolder(files, f => f.webkitRelativePath || f.name, folder);
    e.target.value = '';
  });
}

function wireDocumentTabs() {
  $(ID.NEW_TAB_BUTTON).addEventListener('click', () => { activateTab(newTab()); renderPage(); });
  const tabOf = (e) => { const el = e.target.closest(selector(CLS.DOC_TAB)); return el ? session.tabs[+el.dataset[DATA.TAB_INDEX]] : null; };
  const workspaceOf = (e) => { const el = e.target.closest(selector(CLS.WORKSPACE_GROUP)); return el ? session.workspaces[+el.dataset[DATA.WORKSPACE_INDEX]] : null; };
  const close = (tab) => { if (closeTab(tab)) renderPage(); else renderNavigation(); };
  const closeGroup = (ws) => { if (closeWorkspace(ws)) renderPage(); else renderNavigation(); };
  // the tab bar: the tabs of the active workspace
  $(ID.TABS).addEventListener('click', (e) => {
    const tab = tabOf(e);
    if (!tab) return;
    if (e.target.closest(selector(CLS.DOC_TAB_CLOSE))) close(tab);
    else if (activateTab(tab)) renderPage();
  });
  $(ID.TABS).addEventListener('auxclick', (e) => {   // middle click closes
    const tab = tabOf(e);
    if (tab && e.button === MIDDLE_BUTTON) { e.preventDefault(); close(tab); }
  });
  // the workspace bar: one chip per workspace
  $(ID.WORKSPACES).addEventListener('click', (e) => {
    const ws = workspaceOf(e);
    if (!ws) return;
    if (e.target.closest(selector(CLS.WORKSPACE_CLOSE))) closeGroup(ws);
    else if (e.ctrlKey || e.metaKey) { toggleSelection(ws); renderNavigation(); }   // select for comparison
    else if (activateTab(tabToShow(ws))) renderPage();
  });
  $(ID.WORKSPACES).addEventListener('auxclick', (e) => {
    const ws = workspaceOf(e);
    if (ws && e.button === MIDDLE_BUTTON) { e.preventDefault(); closeGroup(ws); }
  });
  $(ID.COMPARE_BUTTON).addEventListener('click', () => { if (startCompare()) renderPage(); else toast(t(MSG.COMPARE_NEED_TWO)); });
  $(ID.CLEAR_SELECTION_BUTTON).addEventListener('click', () => { clearSelection(); renderNavigation(); });
  $(ID.COMPARE_CLOSE).addEventListener('click', () => { if (session.active.compare) closeTab(session.active); renderPage(); });
  initOptions();
  for (const id of [ID.COMPARE_BUSINESS_ONLY, ID.COMPARE_DIFF_ONLY]) {
    $(id).addEventListener('change', () => { rememberOptions(); if (session.active.compare) renderCompare(); });
  }
  wireValidation();
  // a row shows / hides its differences; its ⧉ button, or a double-click, opens them in a tab of their own
  $(ID.COMPARE_TABLE).addEventListener('click', (e) => {
    const row = e.target.closest(selector(CLS.COMPARE_ROW));
    if (!row) return;
    if (e.target.closest(selector(CLS.COMPARE_OPEN))) { if (openPairTab(row)) renderPage(); }
    else toggleDetail(row);
  });
  $(ID.COMPARE_TABLE).addEventListener('dblclick', (e) => {
    const row = e.target.closest(selector(CLS.COMPARE_ROW));
    if (row && !e.target.closest(selector(CLS.COMPARE_OPEN)) && openPairTab(row)) { window.getSelection()?.removeAllRanges(); renderPage(); }
  });
}

/** The validation tab: its buttons, the phase, the problem rows (a click selects, arrows walk, a link selects the assertion in the Schematron), the document's lines. */
function wireValidation() {
  validation.initOptions();
  validation.onChange(renderPage);
  $(ID.VALIDATE_ERRORS_ONLY).addEventListener('change', () => { validation.rememberOptions(); if (session.active.validation) validation.renderValidation(); });
  $(ID.VALIDATE_PHASE).addEventListener('change', (e) => validation.setPhase(e.target.value));
  $(ID.VALIDATE_SCHEMAS).addEventListener('change', (e) => { if (e.target.dataset[DATA.SOURCE]) validation.setSchema(e.target.dataset[DATA.SOURCE], e.target.value); });
  $(ID.VALIDATE_RERUN).addEventListener('click', () => validation.rerun());
  $(ID.VALIDATE_ANOTHER).addEventListener('click', () => validation.chooseAnotherDocument());
  $(ID.VALIDATE_CLOSE).addEventListener('click', () => { if (session.active.validation) closeTab(session.active); renderPage(); });
  $(ID.VALIDATE_PROBLEMS).addEventListener('click', async (e) => {
    const link = e.target.closest(selector(CLS.VALIDATE_LINK));
    if (link) {
      const entry = validation.schematronEntryOf(session.active);
      const tab = entry && await ensureTab(entry);
      if (tab) jumpTo(tab, link.dataset[DATA.ID]);
      return;
    }
    const row = e.target.closest(selector(CLS.VALIDATE_PROBLEM));
    if (row) validation.selectProblem(+row.dataset[DATA.PROBLEM_INDEX]);
  });
  $(ID.VALIDATE_PROBLEMS).addEventListener('keydown', (e) => {
    if (e.key === KEY.ARROW_DOWN) { e.preventDefault(); validation.stepProblem(1); }
    else if (e.key === KEY.ARROW_UP) { e.preventDefault(); validation.stepProblem(-1); }
    else if (e.key === KEY.ENTER || e.key === KEY.SPACE) {
      const row = e.target.closest(selector(CLS.VALIDATE_PROBLEM));
      if (row) { e.preventDefault(); validation.selectProblem(+row.dataset[DATA.PROBLEM_INDEX]); }
    }
  });
  $(ID.VALIDATE_DOC).addEventListener('click', (e) => {
    const line = e.target.closest(selector(CLS.LINE));
    if (!line) return;
    const i = validation.problemAtLine(+line.dataset[DATA.LINE_NUMBER]);
    if (i >= 0) validation.selectProblem(i, false);
  });
}

function wireKeyboard() {
  document.addEventListener('keydown', (e) => {
    const ctrl = e.ctrlKey || e.metaKey;
    if (ctrl && e.key.toLowerCase() === KEY.OPEN) { e.preventDefault(); openSchemas(); }
    if (ctrl && e.key.toLowerCase() === KEY.SAVE) { e.preventDefault(); saveWorkspace(); }
    if (ctrl && e.key.toLowerCase() === KEY.FIND) {
      e.preventDefault();
      // in the Text view, the find bar; elsewhere the object search
      if (session.active.model && !session.active.compare && !session.active.validation && session.active.view === VIEW.TEXT) focusFind();
      else { $(ID.SEARCH).focus(); $(ID.SEARCH).select(); }
    }
    if (e.altKey && e.key === KEY.ARROW_LEFT) goBack();
  });
}

/** Drag and drop anywhere in the window: dropped folders feed the library, dropped files open in tabs. */
function wireDragAndDrop() {
  const overlay = $(ID.DROP_OVERLAY);
  const carriesFiles = (e) => e.dataTransfer && [...e.dataTransfer.types].includes(DATA_TRANSFER_FILES);
  let dragDepth = 0;
  window.addEventListener('dragenter', (e) => {
    if (!carriesFiles(e)) return;
    e.preventDefault(); dragDepth++; overlay.classList.remove(CLS.HIDDEN);
  });
  window.addEventListener('dragover', (e) => { if (carriesFiles(e)) { e.preventDefault(); e.dataTransfer.dropEffect = DROP_EFFECT_COPY; } });
  window.addEventListener('dragleave', () => { if (--dragDepth <= 0) { dragDepth = 0; overlay.classList.add(CLS.HIDDEN); } });
  window.addEventListener('drop', (e) => {
    e.preventDefault(); dragDepth = 0; overlay.classList.add(CLS.HIDDEN);
    if (!e.dataTransfer) return;
    const entries = [...e.dataTransfer.items].map(i => i.webkitGetAsEntry && i.webkitGetAsEntry()).filter(Boolean);
    const dirs = entries.filter(en => en.isDirectory);
    if (dirs.length) {
      filesOfEntries(dirs).then(list => {
        openBrowserFolder(list.map(x => x.file), f => list.find(x => x.file === f).rel, dirs.map(d => d.name).join(TEXT.LIST_SEPARATOR));
      });
    }
    const files = [...e.dataTransfer.files].filter((f, i) => !entries[i] || entries[i].isFile);
    if (files.length) openFiles(files);
  });
}

function wireViews() {
  document.querySelectorAll(selector(CLS.VIEW_TAB)).forEach(b => b.addEventListener('click', () => showView(b.dataset[DATA.VIEW])));
  // the Links and Types menus: an entry switches its category of link (its kind of object), the last one draws them all
  // again; the menu stays open meanwhile (several switches in a row), a click elsewhere or Escape closes it
  $(ID.LINK_MENU).addEventListener('click', (e) => {
    const category = categoryOfClick(e.target);
    if (category) toggleCategory(category);
    else if (isShowAllClick(e.target)) showAllLinks();
    else return;
    e.stopPropagation();
    renderGraph();
  });
  $(ID.TYPE_MENU).addEventListener('click', (e) => {
    const kind = kindOfClick(e.target);
    if (kind) toggleKind(kind);
    else if (isShowAllKindsClick(e.target)) showAllKinds();
    else return;
    e.stopPropagation();
    renderGraph();
  });
  try { $(ID.TWO_LEVELS).checked = localStorage.getItem(STORAGE_KEY.TWO_LEVELS) === STORAGE_TRUE; } catch (e) { /* storage unavailable */ }
  $(ID.TWO_LEVELS).addEventListener('change', (e) => {
    try { localStorage.setItem(STORAGE_KEY.TWO_LEVELS, e.target.checked ? STORAGE_TRUE : STORAGE_FALSE); } catch (e2) { /* ignore */ }
    renderGraph();
  });
  $(ID.BACK_BUTTON).addEventListener('click', goBack);
  initDetails();
  $(ID.DETAILS_TOGGLE).addEventListener('click', toggleDetails);
  initSchemaInfo();
  $(ID.SCHEMA_INFO_TOGGLE).addEventListener('click', toggleSchemaInfo);
  initFiles();
  $(ID.FILES_TOGGLE).addEventListener('click', toggleFiles);
  // expand all / collapse all, on each tree
  $(ID.FILES_EXPAND_ALL).addEventListener('click', () => setAllUnfolded(true));
  $(ID.FILES_COLLAPSE_ALL).addEventListener('click', () => setAllUnfolded(false));
  $(ID.OBJECTS_EXPAND_ALL).addEventListener('click', () => setAllGroupsExpanded(true));
  $(ID.OBJECTS_COLLAPSE_ALL).addEventListener('click', () => setAllGroupsExpanded(false));
  $(ID.COMPARE_EXPAND_ALL).addEventListener('click', () => setAllDetails(true));
  $(ID.COMPARE_COLLAPSE_ALL).addEventListener('click', () => setAllDetails(false));
  $(ID.FILES_CONTENT).addEventListener('click', async (e) => {   // the Files panel: a file or an object shows its tab, opened when needed
    const hit = fileListClick(e.target);
    if (!hit) return;
    if (hit.entries) { openEntriesAsWorkspace(hit.folder, hit.entries); return; }
    const tab = hit.tab || await ensureTab(hit.entry);
    if (!tab) return;
    if (activateTab(tab)) renderPage();
    if (hit.id) select(hit.id);
  });
  $(ID.EXPORT_BUTTON).addEventListener('click', exportPng);
  $(ID.EXPORT_SVG_BUTTON).addEventListener('click', exportSvg);
  window.addEventListener('resize', () => { const st = session.active; if (st.model && st.view === VIEW.GRAPH) renderGraph(); });
}

function wireSearch() {
  const search = $(ID.SEARCH);
  const apply = (value) => {
    const had = !!session.active.filter;
    session.active.filter = value.trim();
    // a search reaches every file of the workspace: the Files panel opens for it, since that is where the other files answer
    if (!had && session.active.filter && isFilesCollapsed()) setFilesCollapsed(false);
    if (session.active.model) renderNodeList();
    renderFileList();
  };
  search.addEventListener('input', (e) => apply(e.target.value));
  search.addEventListener('keydown', (e) => {
    if (e.key === KEY.ESCAPE) { e.target.value = ''; apply(''); e.target.blur(); }
  });
  $(ID.SEARCH_CLEAR).addEventListener('click', () => { search.value = ''; apply(''); search.focus(); });
  // the find bar of the Text view
  const find = $(ID.TEXT_FIND_INPUT);
  find.addEventListener('input', refreshFind);
  find.addEventListener('keydown', (e) => {
    if (e.key === KEY.ENTER) { e.preventDefault(); findStep(e.shiftKey ? -1 : 1); }
    if (e.key === KEY.ESCAPE) { clearFind(); find.blur(); }
  });
  $(ID.TEXT_FIND_PREV).addEventListener('click', () => findStep(-1));
  $(ID.TEXT_FIND_NEXT).addEventListener('click', () => findStep(1));
  $(ID.TEXT_FIND_CLOSE).addEventListener('click', () => { clearFind(); find.blur(); });
}

/** Shows node {@code id} of another file of the workspace: an open tab (data-tab), or a listed file (data-file) opened in a tab first. */
async function jumpToPlace(dataset, id) {
  if (dataset[DATA.TAB] != null) { jumpTo(session.tabs[+dataset[DATA.TAB]], id); return; }
  const tab = await ensureTab(session.active.workspace.files[+dataset[DATA.FILE]]);
  if (tab) jumpTo(tab, id);
}

/** Everything that selects a node: the object list, the graph, the details links, the line numbers of the text. */
function wireSelectionSources() {
  $(ID.NODE_LIST).addEventListener('click', (e) => {
    const header = e.target.closest(selector(CLS.GROUP_HEADER));
    if (header) { toggleGroup(header); return; }
    const item = e.target.closest(selector(CLS.ITEM));
    if (item && item.dataset[DATA.ID]) select(item.dataset[DATA.ID]);
  });
  $(ID.GRAPH_CANVAS).addEventListener('click', (e) => activateNode(e.target.closest(selector(CLS.NODE))));
  // the keyboard in the graph: arrows walk the nodes (drawn in reading order), Home is the centre, Enter / Space act as a click
  $(ID.GRAPH_CANVAS).addEventListener('keydown', (e) => {
    const g = e.target.closest(selector(CLS.NODE));
    if (!g || e.altKey || e.ctrlKey || e.metaKey) return;
    const nodes = [...$(ID.GRAPH_CANVAS).querySelectorAll(selector(CLS.NODE))];
    const i = nodes.indexOf(g);
    if (e.key === KEY.ENTER || e.key === KEY.SPACE) { e.preventDefault(); activateNode(g); }
    else if (e.key === KEY.ARROW_RIGHT || e.key === KEY.ARROW_DOWN) { e.preventDefault(); nodes[(i + 1) % nodes.length].focus(); }
    else if (e.key === KEY.ARROW_LEFT || e.key === KEY.ARROW_UP) { e.preventDefault(); nodes[(i - 1 + nodes.length) % nodes.length].focus(); }
    else if (e.key === KEY.HOME) { e.preventDefault(); $(ID.GRAPH_CANVAS).querySelector(selector(CLS.NODE) + selector(CLS.CENTER)).focus(); }
  });
  /** What a click (or Enter) on a drawn node does: selects it, jumps to its file, or follows it. */
  function activateNode(g) {
    if (!g) return;
    const st = session.active, id = g.dataset[DATA.ID];
    if (g.dataset[DATA.TAB] != null || g.dataset[DATA.FILE] != null) {   // a node of another file
      jumpToPlace(g.dataset, id);
    } else if (id !== st.selected) {
      select(id);
    } else if (st.nodes.get(id).kind === NODE_KIND.EXTERNAL) {
      followExternal(st.nodes.get(id));   // retry (e.g. the file chooser was cancelled)
    }
  }
  $(ID.DETAILS).addEventListener('click', (e) => {
    if (e.target.closest('a[data-' + DATA.LINE + ']')) { showView(VIEW.TEXT); return; }
    const link = e.target.closest(selector(CLS.LINK));
    if (!link) return;
    if (link.dataset[DATA.TAB] != null || link.dataset[DATA.FILE] != null) jumpToPlace(link.dataset, link.dataset[DATA.ID]);
    else select(link.dataset[DATA.ID]);
  });
  $(ID.TEXT).addEventListener('click', (e) => {
    const ln = e.target.closest(selector(CLS.LINE_NUMBER));
    const line = ln && ln.closest(selector(CLS.LINE) + selector(CLS.LINE_DECLARATION));
    if (line) select(line.dataset[DATA.ID]);
  });
}
