/** Workspaces and their tabs: creating, switching, closing, drawing the bars. Callers redraw the page (renderPage) after a switch. A tab shows a file, a comparison (compare.js) or a validation (validate.js). */
import { WORKSPACE_FILE_SUFFIX } from './constants.js';
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { renderFileList } from './file-list.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { dropMarksOutside } from './object-compare.js';
import { newTabState, newWorkspaceState, session } from './state.js';

const SCHEMA_SEPARATOR = ' + ';

/** The name of a validation tab: "order.xml ⇢ purchaseOrder.xsd + purchaseOrder.sch". */
export function validationTitle(tab) {
  const v = tab.validation;
  return t(MSG.VALIDATE_TAB, v.name, [v.xsd, v.sch].filter(Boolean).map(s => s.name).join(SCHEMA_SEPARATOR));
}

/** The state class of a validation tab: pending, valid, invalid. */
export function validationStatus(tab) {
  const v = tab.validation;
  if (!v.result) return v.error ? CLS.INVALID : CLS.PENDING;
  return v.result.valid ? CLS.VALID : CLS.INVALID;
}

/** The name of a comparison tab: "v1 ⇄ v2", or "x.xsd (v1 ⇄ v2)" for the differences of one file. */
export function compareTitle(tab) {
  const { left, right, file } = tab.compare;
  const one = file;
  return t(one ? MSG.COMPARE_FILE_TAB : MSG.COMPARE_TAB, ...(one ? [one] : []), workspaceName(left), workspaceName(right));
}

/**
 * After tabs went: comparisons of a gone workspace go too, a workspace left without tabs goes
 * (the last one gets an empty tab), a gone active tab is replaced by the one at {@code at}.
 * Returns true when the active tab changed.
 */
function settle(at) {
  const view = session.active.view;
  for (;;) {
    const alive = new Set(session.workspaces);
    const before = session.tabs.length + session.workspaces.length;
    session.tabs = session.tabs.filter(tab => !(tab.compare && (!alive.has(tab.compare.left) || !alive.has(tab.compare.right))));
    for (const ws of [...session.workspaces]) {
      if (tabsOf(ws).length) continue;
      if (session.workspaces.length > 1) session.workspaces.splice(session.workspaces.indexOf(ws), 1);
      else session.tabs.push(emptyTabOf(ws, view));
    }
    if (before === session.tabs.length + session.workspaces.length) break;
  }
  session.compareSelection = session.compareSelection.filter(ws => session.workspaces.includes(ws));
  dropMarksOutside(session.workspaces);
  if (session.tabs.includes(session.active)) return false;
  session.active = session.tabs[Math.min(at, session.tabs.length - 1)];
  return true;
}

const PATH_SEPARATORS = /[\\/]/;

export const tabsOf = (ws) => session.tabs.filter(tab => tab.workspace === ws);
export const activeWorkspace = () => session.active.workspace;
/** An unsaved workspace knowing no file: it can take the next workspace opened. */
export const isEmptyWorkspace = (ws) => !ws.path && !ws.files.length && tabsOf(ws).every(tab => !tab.model && !tab.compare && !tab.validation);

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
  if (session.tabs.length === 1) {
    resetTab(tab);
    return true;
  }
  const i = session.tabs.indexOf(tab);
  session.tabs.splice(i, 1);
  return settle(i);
}

/** Closes every tab of {@code ws} and forgets it; closing the last workspace leaves a fresh empty one. Returns true when the active tab changed. */
export function closeWorkspace(ws) {
  if (session.workspaces.length === 1) {
    closeAllTabs();
    return true;
  }
  session.tabs = session.tabs.filter(tab => tab.workspace !== ws);
  session.workspaces.splice(session.workspaces.indexOf(ws), 1);
  return settle(0);
}

/** Closes everything (File ▸ Close all tabs): one unsaved workspace with one empty tab remains, with the current view. */
export function closeAllTabs() {
  const view = session.active.view;
  session.workspaceCounter = 0;
  const ws = newWorkspaceState(++session.workspaceCounter);
  session.workspaces = [ws];
  session.compareSelection = [];
  session.tabs = [emptyTabOf(ws, view)];
  session.active = session.tabs[0];
  session.pendingJump = null;
  return session.active;
}

/** Empties a tab (File ▸ Close), keeping its view and workspace; a comparison tab becomes an empty tab. */
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
  return { text: $(ID.TEXT).scrollTop, graphTop: $(ID.GRAPH_CANVAS).scrollTop, graphLeft: $(ID.GRAPH_CANVAS).scrollLeft,
    modelTop: $(ID.MODEL_CANVAS).scrollTop, modelLeft: $(ID.MODEL_CANVAS).scrollLeft };
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
    const tabName = tab.compare ? compareTitle(tab) : tab.validation ? validationTitle(tab) : tab.fileName || t(MSG.TAB_UNTITLED);
    tabs += '<div class="' + CLS.DOC_TAB + (tab === session.active ? ' ' + CLS.ACTIVE : '') + (tab.compare ? ' ' + CLS.COMPARE_TAB : '')
      + (tab.validation ? ' ' + CLS.VALIDATION_TAB + ' ' + validationStatus(tab) : '') + '"'
      + dataAttr(DATA.TAB_INDEX, session.tabs.indexOf(tab)) + ' title="' + esc(tab.path || tabName) + '">'
      + '<span class="' + CLS.DOC_TAB_NAME + '">' + esc(tabName) + '</span>'
      + '<button class="' + CLS.DOC_TAB_CLOSE + '" type="button" title="' + esc(t(MSG.TAB_CLOSE)) + '">×</button></div>';
  }
  $(ID.TABS).innerHTML = tabs;
  const selected = session.compareSelection.length;
  $(ID.COMPARE_BUTTON).disabled = selected !== 2;
  $(ID.CLEAR_SELECTION_BUTTON).disabled = selected === 0;
  $(ID.COMPARE_HINT).classList.toggle(CLS.HIDDEN, selected === 2 || session.workspaces.length < 2);   // shown once there is something to compare
  renderFileList();
}
