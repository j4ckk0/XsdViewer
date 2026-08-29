/** Workspaces and their tabs: creating, switching, closing, drawing the bars. Callers redraw the page (renderPage) after a switch. */
import { WORKSPACE_FILE_SUFFIX } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { renderFileList } from './file-list.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { newTabState, newWorkspaceState, session } from './state.js';

/** A workspace being closed leaves the comparison selection and ends a comparison it took part in. */
function forgetWorkspace(ws) {
  const i = session.compareSelection.indexOf(ws);
  if (i >= 0) session.compareSelection.splice(i, 1);
  if (session.compare && (session.compare.left === ws || session.compare.right === ws)) session.compare = null;
}

const PATH_SEPARATORS = /[\\/]/;

export const tabsOf = (ws) => session.tabs.filter(tab => tab.workspace === ws);
export const activeWorkspace = () => session.active.workspace;
/** An unsaved workspace knowing no file: it can take the next workspace opened. */
export const isEmptyWorkspace = (ws) => !ws.path && !ws.files.length && tabsOf(ws).every(tab => !tab.model);

/** The workspace file's name without its suffix, else the name it was given (an opened folder), else "Workspace n". */
export function workspaceName(ws) {
  if (!ws.path) return ws.label || t(MSG.WORKSPACE_UNTITLED, ws.number);
  const base = ws.path.split(PATH_SEPARATORS).pop();
  return base.endsWith(WORKSPACE_FILE_SUFFIX) ? base.slice(0, -WORKSPACE_FILE_SUFFIX.length) : base;
}

/** Adds an unsaved, empty workspace (no tab yet: see newTab). */
export function newWorkspace() {
  const ws = newWorkspaceState(++session.workspaceCounter);
  session.workspaces.push(ws);
  return ws;
}

/** Adds an empty tab (with the current view) at the end of {@code ws} (default: the active workspace); does not activate it. */
export function newTab(ws = session.active.workspace) {
  const tab = newTabState();
  tab.view = session.active.view;
  tab.workspace = ws;
  const own = tabsOf(ws);
  const at = own.length ? session.tabs.indexOf(own[own.length - 1]) + 1 : session.tabs.length;
  session.tabs.splice(at, 0, tab);
  renderNavigation();
  return tab;
}

/** Makes {@code tab} the active one, keeping the scroll positions of the previous one. Returns false when it already was. */
export function activateTab(tab) {
  tab.workspace.lastActive = tab;
  if (tab === session.active) return false;
  session.active.scroll = currentScroll();
  session.active = tab;
  return true;
}

/** The tab to show when switching to {@code ws}: the one last active there, else its first. */
export const tabToShow = (ws) => (tabsOf(ws).includes(ws.lastActive) ? ws.lastActive : tabsOf(ws)[0]);

/**
 * Removes {@code tab}; a workspace left without tabs goes too (the last workspace gets an empty
 * tab instead). Returns true when the active tab's content changed.
 */
export function closeTab(tab) {
  const ws = tab.workspace;
  if (session.tabs.length === 1) {
    resetTab(tab);
    return true;
  }
  const i = session.tabs.indexOf(tab);
  session.tabs.splice(i, 1);
  if (!tabsOf(ws).length) {
    if (session.workspaces.length > 1) { session.workspaces.splice(session.workspaces.indexOf(ws), 1); forgetWorkspace(ws); }
    else session.tabs.push(emptyTabOf(ws, tab.view));
  }
  if (tab !== session.active) return false;
  session.active = session.tabs[Math.min(i, session.tabs.length - 1)];
  return true;
}

/** Closes every tab of {@code ws} and forgets it (the last workspace is emptied instead). Returns true when the active tab changed. */
export function closeWorkspace(ws) {
  const view = session.active.view;
  const wasActive = session.active.workspace === ws;
  session.tabs = session.tabs.filter(tab => tab.workspace !== ws);
  forgetWorkspace(ws);
  if (session.workspaces.length > 1) {
    session.workspaces.splice(session.workspaces.indexOf(ws), 1);
  } else {
    ws.path = null;
    session.tabs.push(emptyTabOf(ws, view));
  }
  if (!wasActive) return false;
  session.active = session.tabs[0];
  return true;
}

/** Closes everything (File ▸ Close all tabs): one unsaved workspace with one empty tab remains, with the current view. */
export function closeAllTabs() {
  const view = session.active.view;
  session.workspaceCounter = 0;
  const ws = newWorkspaceState(++session.workspaceCounter);
  session.workspaces = [ws];
  session.compareSelection = [];
  session.compare = null;
  session.tabs = [emptyTabOf(ws, view)];
  session.active = session.tabs[0];
  session.pendingJump = null;
  return session.active;
}

/** Empties a tab (File ▸ Close), keeping its view and workspace. */
export function resetTab(tab) {
  Object.assign(tab, newTabState(), { view: tab.view, workspace: tab.workspace });
}

function emptyTabOf(ws, view) {
  const tab = newTabState();
  tab.view = view;
  tab.workspace = ws;
  return tab;
}

function currentScroll() {
  return { text: $(ID.TEXT).scrollTop, graphTop: $(ID.GRAPH_CANVAS).scrollTop, graphLeft: $(ID.GRAPH_CANVAS).scrollLeft };
}

/** Draws everything that lists what is open: the workspace bar, the tab bar (the active workspace's tabs) and the Files panel. */
export function renderNavigation() {
  let chips = '';
  session.workspaces.forEach((ws, w) => {
    const name = workspaceName(ws);
    chips += '<div class="' + CLS.WORKSPACE_GROUP + (ws === session.active.workspace ? ' ' + CLS.ACTIVE : '')
      + (session.compareSelection.includes(ws) ? ' ' + CLS.SELECTED : '') + '"' + dataAttr(DATA.WORKSPACE_INDEX, w) + '>'
      + '<span class="' + CLS.WORKSPACE_NAME + '" title="' + esc((ws.path || name) + '\n' + t(MSG.WORKSPACE_SELECT_HINT)) + '">' + esc(name)
      + '<button class="' + CLS.WORKSPACE_CLOSE + '" type="button" title="' + esc(t(MSG.WORKSPACE_CLOSE, name)) + '">×</button></span></div>';
  });
  $(ID.WORKSPACES).innerHTML = chips;
  let tabs = '';
  for (const tab of tabsOf(session.active.workspace)) {
    const tabName = tab.fileName || t(MSG.TAB_UNTITLED);
    tabs += '<div class="' + CLS.DOC_TAB + (tab === session.active ? ' ' + CLS.ACTIVE : '') + '"'
      + dataAttr(DATA.TAB_INDEX, session.tabs.indexOf(tab)) + ' title="' + esc(tab.path || tabName) + '">'
      + '<span class="' + CLS.DOC_TAB_NAME + '">' + esc(tabName) + '</span>'
      + '<button class="' + CLS.DOC_TAB_CLOSE + '" type="button" title="' + esc(t(MSG.TAB_CLOSE)) + '">×</button></div>';
  }
  $(ID.TABS).innerHTML = tabs;
  const selected = session.compareSelection.length;
  $(ID.COMPARE_BUTTON).disabled = selected !== 2;
  $(ID.COMPARE_HINT).classList.toggle(CLS.HIDDEN, selected === 2 || session.workspaces.length < 2);   // shown once there is something to compare
  renderFileList();
}
