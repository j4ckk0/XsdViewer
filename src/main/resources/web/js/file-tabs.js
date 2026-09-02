/** Opening a workspace file in a tab (from its cached text and model), and parsing listed files in the background. */
import { parseSchema } from './api.js';
import { t } from './i18n.js';
import { MSG } from './message-keys.js';
import { renderFileList } from './file-list.js';
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

/** Runs are serialised: one workspace at a time. */
let chain = Promise.resolve();
/** Parses in flight at once within a run (the server handles each request on its own thread). */
const PARALLEL_PARSES = 4;
/** The Files panel is redrawn at most this often while files are being parsed: a search sees the new objects without a redraw per file. */
const REDRAW_INTERVAL_MS = 300;

/** Parses the files of {@code ws} that have no model yet (a few at a time), redrawing the Files panel as they come. */
export function parseInBackground(ws) {
  chain = chain.then(async () => {
    const queue = ws.files.filter(entry => !entry.model && !entry.failed);
    let lastRedraw = 0;
    const worker = async () => {
      for (let entry = queue.shift(); entry; entry = queue.shift()) {
        await ensureModel(entry, false);
        if (Date.now() - lastRedraw >= REDRAW_INTERVAL_MS) { lastRedraw = Date.now(); renderFileList(); }
      }
    };
    await Promise.all(Array.from({ length: PARALLEL_PARSES }, worker));
    renderFileList();
  }).catch(() => { /* nothing to report: a file that fails is shown as such */ });
  return chain;
}
