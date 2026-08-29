/** Wiring of the page's controls to the actions: menu, tabs, keyboard, drag and drop, clicks in the views. */
import { DATA_TRANSFER_FILES, DROP_EFFECT_COPY, KEY, MIDDLE_BUTTON, NODE_KIND, PATH_SEPARATOR, STORAGE_FALSE, STORAGE_KEY, STORAGE_TRUE, VIEW } from './constants.js';
import { $, CLS, DATA, ID, selector } from './dom.js';
import { addFolder, closeAll, closeFile, openFiles, openSchemas, openWorkspace, quit, saveWorkspace } from './file-actions.js';
import { initDetails, toggleDetails } from './details.js';
import { renderGraph } from './graph.js';
import { filesOfEntries } from './library.js';
import { followExternal, goBack, select } from './navigation.js';
import { renderPage, showView } from './page.js';
import { exportPng } from './png-export.js';
import { initSchemaInfo, renderNodeList, toggleGroup, toggleSchemaInfo } from './sidebar.js';
import { session } from './state.js';
import { activateTab, closeTab, newTab, renderTabBar } from './tabs.js';

const LIST_SEPARATOR = ', ';

export function wireEvents() {
  wireFileMenu();
  wireDocumentTabs();
  wireKeyboard();
  wireDragAndDrop();
  wireViews();
  wireSearch();
  wireSelectionSources();
}

function wireFileMenu() {
  const menu = $(ID.FILE_MENU);
  const closeMenu = () => menu.classList.add(CLS.HIDDEN);
  $(ID.FILE_MENU_BUTTON).addEventListener('click', (e) => { e.stopPropagation(); menu.classList.toggle(CLS.HIDDEN); });
  document.addEventListener('click', closeMenu);
  document.addEventListener('keydown', (e) => { if (e.key === KEY.ESCAPE) closeMenu(); });
  $(ID.MENU_OPEN).addEventListener('click', () => { closeMenu(); openSchemas(); });
  $(ID.MENU_OPEN_WORKSPACE).addEventListener('click', () => { closeMenu(); openWorkspace(); });
  $(ID.MENU_SAVE_WORKSPACE).addEventListener('click', () => { closeMenu(); saveWorkspace(); });
  $(ID.MENU_NEW_TAB).addEventListener('click', () => { closeMenu(); activateTab(newTab()); renderPage(); });
  $(ID.MENU_CLOSE).addEventListener('click', () => { closeMenu(); closeFile(); });
  $(ID.MENU_CLOSE_ALL).addEventListener('click', () => { closeMenu(); closeAll(); });
  $(ID.MENU_QUIT).addEventListener('click', () => { closeMenu(); quit(); });
  $(ID.FILE_INPUT).addEventListener('change', (e) => { openFiles([...e.target.files]); e.target.value = ''; });
  $(ID.FILE_INPUT).addEventListener('cancel', () => { session.pendingJump = null; });
  $(ID.MENU_OPEN_FOLDER).addEventListener('click', () => { closeMenu(); $(ID.FOLDER_INPUT).click(); });
  $(ID.FOLDER_INPUT).addEventListener('change', (e) => {
    const files = [...e.target.files];
    const folder = files[0] ? (files[0].webkitRelativePath.split(PATH_SEPARATOR)[0] || '') : '';
    addFolder(files, f => f.webkitRelativePath || f.name, folder);
    e.target.value = '';
  });
}

function wireDocumentTabs() {
  $(ID.NEW_TAB_BUTTON).addEventListener('click', () => { activateTab(newTab()); renderPage(); });
  const tabOf = (e) => { const el = e.target.closest(selector(CLS.DOC_TAB)); return el ? session.tabs[+el.dataset[DATA.TAB_INDEX]] : null; };
  const close = (tab) => { if (closeTab(tab)) renderPage(); else renderTabBar(); };
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
}

function wireKeyboard() {
  document.addEventListener('keydown', (e) => {
    const ctrl = e.ctrlKey || e.metaKey;
    if (ctrl && e.key.toLowerCase() === KEY.OPEN) { e.preventDefault(); openSchemas(); }
    if (ctrl && e.key.toLowerCase() === KEY.SAVE) { e.preventDefault(); saveWorkspace(); }
    if (ctrl && e.key.toLowerCase() === KEY.FIND) { e.preventDefault(); $(ID.SEARCH).focus(); $(ID.SEARCH).select(); }
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
        addFolder(list.map(x => x.file), f => list.find(x => x.file === f).rel, dirs.map(d => d.name).join(LIST_SEPARATOR));
      });
    }
    const files = [...e.dataTransfer.files].filter((f, i) => !entries[i] || entries[i].isFile);
    if (files.length) openFiles(files);
  });
}

function wireViews() {
  document.querySelectorAll(selector(CLS.VIEW_TAB)).forEach(b => b.addEventListener('click', () => showView(b.dataset[DATA.VIEW])));
  $(ID.SHOW_BUILTINS).addEventListener('change', renderGraph);
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
  $(ID.EXPORT_BUTTON).addEventListener('click', exportPng);
  window.addEventListener('resize', () => { const st = session.active; if (st.model && st.view === VIEW.GRAPH) renderGraph(); });
}

function wireSearch() {
  const search = $(ID.SEARCH);
  const apply = (value) => { session.active.filter = value.trim(); if (session.active.model) renderNodeList(); };
  search.addEventListener('input', (e) => apply(e.target.value));
  search.addEventListener('keydown', (e) => {
    if (e.key === KEY.ESCAPE) { e.target.value = ''; apply(''); e.target.blur(); }
  });
}

/** Everything that selects a node: the object list, the graph, the details links, the line numbers of the text. */
function wireSelectionSources() {
  $(ID.NODE_LIST).addEventListener('click', (e) => {
    const header = e.target.closest(selector(CLS.GROUP_HEADER));
    if (header) { toggleGroup(header); return; }
    const item = e.target.closest(selector(CLS.ITEM));
    if (item && item.dataset[DATA.ID]) select(item.dataset[DATA.ID]);
  });
  $(ID.GRAPH_CANVAS).addEventListener('click', (e) => {
    const g = e.target.closest(selector(CLS.NODE));
    if (!g) return;
    const st = session.active, id = g.dataset[DATA.ID];
    if (g.dataset[DATA.TAB] != null) {   // level-2 node of another file
      if (activateTab(session.tabs[+g.dataset[DATA.TAB]])) renderPage();
      select(id);
    } else if (id !== st.selected) {
      select(id);
    } else if (st.nodes.get(id).kind === NODE_KIND.EXTERNAL) {
      followExternal(st.nodes.get(id));   // retry (e.g. the file chooser was cancelled)
    }
  });
  $(ID.DETAILS).addEventListener('click', (e) => {
    if (e.target.closest('a[data-' + DATA.LINE + ']')) { showView(VIEW.TEXT); return; }
    const link = e.target.closest(selector(CLS.LINK));
    if (link) select(link.dataset[DATA.ID]);
  });
  $(ID.TEXT).addEventListener('click', (e) => {
    const ln = e.target.closest(selector(CLS.LINE_NUMBER));
    const line = ln && ln.closest(selector(CLS.LINE) + selector(CLS.LINE_DECLARATION));
    if (line) select(line.dataset[DATA.ID]);
  });
}
