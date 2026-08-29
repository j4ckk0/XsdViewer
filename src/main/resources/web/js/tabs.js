/** The document tabs: creating, switching, closing, and the tab bar. Callers redraw the page (renderPage) after a switch. */
import { $, CLS, DATA, ID, dataAttr, esc } from './dom.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { newTabState, session } from './state.js';

/** Adds an empty tab (with the current view) at the end; does not activate it. */
export function newTab() {
  const tab = newTabState();
  tab.view = session.active.view;
  session.tabs.push(tab);
  renderTabBar();
  return tab;
}

/** Makes {@code tab} the active one, keeping the scroll positions of the previous one. Returns false when it already was. */
export function activateTab(tab) {
  if (tab === session.active) return false;
  session.active.scroll = currentScroll();
  session.active = tab;
  return true;
}

/** Removes {@code tab} (the last one is emptied instead). Returns true when the active tab's content changed. */
export function closeTab(tab) {
  const tabs = session.tabs;
  if (tabs.length === 1) {
    resetTab(tab);
    return true;
  }
  const i = tabs.indexOf(tab);
  tabs.splice(i, 1);
  if (tab !== session.active) return false;
  session.active = tabs[Math.min(i, tabs.length - 1)];
  return true;
}

/** Closes every tab (File ▸ Close all tabs, opening a workspace): one empty tab remains, with the current view. */
export function closeAllTabs() {
  const first = newTabState();
  first.view = session.active.view;
  session.tabs.length = 0;
  session.tabs.push(first);
  session.active = first;
  session.pendingJump = null;
  return first;
}

/** Empties a tab (File ▸ Close), keeping its view. */
export function resetTab(tab) {
  Object.assign(tab, newTabState(), { view: tab.view });
}

function currentScroll() {
  return { text: $(ID.TEXT).scrollTop, graphTop: $(ID.GRAPH_CANVAS).scrollTop, graphLeft: $(ID.GRAPH_CANVAS).scrollLeft };
}

export function renderTabBar() {
  let html = '';
  session.tabs.forEach((tab, i) => {
    const name = tab.fileName || t(MSG.TAB_UNTITLED);
    html += '<div class="' + CLS.DOC_TAB + (tab === session.active ? ' ' + CLS.ACTIVE : '') + '"'
      + dataAttr(DATA.TAB_INDEX, i) + ' title="' + esc(tab.path || name) + '">'
      + '<span class="' + CLS.DOC_TAB_NAME + '">' + esc(name) + '</span>'
      + '<button class="' + CLS.DOC_TAB_CLOSE + '" type="button" title="' + esc(t(MSG.TAB_CLOSE)) + '">×</button></div>';
  });
  $(ID.TABS).innerHTML = html;
}
