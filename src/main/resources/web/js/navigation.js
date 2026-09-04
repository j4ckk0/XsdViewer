/** Moving the selection: within the file (with a back history) and across files, following links to external declarations. */
import { NODE_KIND, TEXT } from './constants.js';
import { findIn, findInWorkspace, kindsOf, locationsFor } from './declarations.js';
import { renderDetails } from './details.js';
import { $, ID } from './dom.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { renderMainView, renderPage } from './page.js';
import { ensureTab } from './file-tabs.js';
import { resolveLocation } from './schema-loader.js';
import { fileKeys, hasKey, registerFile } from './workspace-files.js';
import { renderNodeListSelection } from './sidebar.js';
import { session } from './state.js';
import { activateTab, tabsOf } from './tabs.js';
import { highlightTextLine } from './text-view.js';
import { toast } from './toast.js';


/** Selects a node of the active tab and redraws the views; follows it when it is an external placeholder. */
export function select(id, pushHistory = true) {
  const st = session.active;
  if (!st.nodes.has(id)) return;
  if (pushHistory && st.selected && st.selected !== id) st.history.push(st.selected);
  st.selected = id;
  $(ID.BACK_BUTTON).disabled = st.history.length === 0;
  renderNodeListSelection();
  renderMainView();
  renderDetails();
  highlightTextLine(true);
  const n = st.nodes.get(id);
  if (n.kind === NODE_KIND.EXTERNAL && pushHistory) followExternal(n);
}

export function goBack() {
  const prev = session.active.history.pop();
  if (prev) select(prev, false);
}

/**
 * Shows tab {@code tab} with node {@code id} selected, in the view being read: a click on an object
 * moves the selection, never the view, even when it lands in another file whose tab was left elsewhere.
 */
export function jumpTo(tab, id) {
  tab.view = session.active.view;
  if (activateTab(tab)) renderPage();
  select(id);
}

/** Follows a link to something this file does not declare: another file of the workspace (open, or listed and opened now), else the files its imports / includes name (opened on the way), else the user's file chooser. */
export async function followExternal(node) {
  const from = session.active, name = node.name, kinds = kindsOf(node), ns = node.ns || '';
  const found = findInWorkspace(name, kinds, ns, from);
  if (found) {
    const tab = found.place.tab || await ensureTab(found.place.entry);
    if (tab) { jumpTo(tab, found.id); return; }
  }
  const locs = locationsFor(from, ns);
  if (!locs.length) {
    askForFile(name, kinds, ns, t(MSG.EXTERNAL_NO_LOCATION, name));
    return;
  }
  // The imported files are read (opened folder or server) and their own imports / includes followed.
  if (!from.path && from.located) await from.located;
  const visited = new Set(fileKeys(from));
  const queue = locs.map(location => ({ src: from, location }));
  const examined = [], missing = [];
  while (queue.length) {
    const { src, location } = queue.shift();
    const f = await resolveLocation(src, location);
    if (!f) { if (!missing.includes(location)) missing.push(location); continue; }
    if (visited.has(f.key)) continue;
    visited.add(f.key);
    let tab = tabsOf(from.workspace).find(x => hasKey(x, f.key));
    if (!tab) {
      tab = await ensureTab(registerFile(from.workspace, { name: f.name, path: f.path, rel: f.rel, text: f.text }));
      if (!tab) continue;
    }
    examined.push(tab);
    const id = findIn(tab, name, kinds, ns);
    if (id) { jumpTo(tab, id); return; }
    for (const l of locationsFor(tab, ns)) queue.push({ src: tab, location: l });
  }
  const why = missing.length ? t(MSG.EXTERNAL_MISSING, missing.join(TEXT.LIST_SEPARATOR))
    : examined.length ? t(MSG.EXTERNAL_NOT_FOUND_IN, name, examined.map(x => x.fileName).join(TEXT.LIST_SEPARATOR))
    : t(MSG.EXTERNAL_NOT_FOUND, name);
  askForFile(name, kinds, ns, t(MSG.EXTERNAL_CHOOSE_FILE, why, name));
}

/** Opens the file chooser for the file declaring {@code name}; the jump completes when it is loaded. */
function askForFile(name, kinds, ns, hint) {
  session.pendingJump = { name, kinds, ns };
  toast(hint);
  $(ID.FILE_INPUT).click();   // needs a recent user gesture: fine, this follows a click on the node
}

/** After a file is loaded by the user: jump to the declaration a link was waiting for, if it is in there. */
export function checkPendingJump(tab) {
  const jump = session.pendingJump;
  if (!jump) return;
  const id = findIn(tab, jump.name, jump.kinds, jump.ns);
  if (!id) return;
  session.pendingJump = null;
  jumpTo(tab, id);
}
