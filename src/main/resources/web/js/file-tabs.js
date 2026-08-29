/** Opening a workspace file in a tab (from its cached text and model), and parsing listed files in the background. */
import { parseSchema } from './api.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { fillTab } from './schema-loader.js';
import { newTab, renderNavigation, tabsOf } from './tabs.js';
import { toast } from './toast.js';
import { tabOfFile, workspaceOfFile } from './workspace-files.js';

/** Parses the entry when it has no model yet; false when the server refuses it (reported when asked). */
export async function ensureModel(entry, report = true) {
  if (entry.model) return true;
  try {
    entry.model = await parseSchema(entry.text);
    entry.failed = false;
    return true;
  } catch (e) {
    entry.failed = true;
    if (report) toast(t(MSG.CANNOT_PARSE, entry.name, e.message));
    return false;
  }
}

/** The tab showing {@code entry}, opened in its workspace when there is none (an empty tab is reused). Null when the file cannot be parsed. */
export async function ensureTab(entry) {
  const existing = tabOfFile(entry);
  if (existing) return existing;
  if (!(await ensureModel(entry, true))) return null;
  const ws = workspaceOfFile(entry);
  const tab = tabsOf(ws).find(x => !x.model) || newTab(ws);
  fillTab(tab, entry.name, entry.text, entry.path, entry.rel, entry.model);
  tab.file = entry;
  renderNavigation();
  return tab;
}

/** Runs are serialised: one parse at a time. */
let chain = Promise.resolve();

/** Parses, one after the other, the files of {@code ws} that have no model yet, redrawing the Files panel as they come. */
export function parseInBackground(ws) {
  chain = chain.then(async () => {
    for (const entry of ws.files) {
      if (entry.model || entry.failed) continue;
      await ensureModel(entry, false);
      renderNavigation();
    }
  }).catch(() => { /* nothing to report: a file that fails is shown as such */ });
  return chain;
}
