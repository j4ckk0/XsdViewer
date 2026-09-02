/**
 * Discovery of the schemas a file links to (its imports / includes resolved relative to its own
 * location, recursively): they join the workspace's Files panel and open as tabs only when at most MAX_AUTO_OPEN.
 */
import { parseSchema } from './api.js';
import { busy } from './busy.js';
import { MAX_AUTO_OPEN, TEXT } from './constants.js';
import { ensureTab } from './file-tabs.js';
import { renderGraph } from './graph.js';
import { plural, t } from './i18n.js';
import { MSG } from './message-keys.js';
import { resolveLocation } from './schema-loader.js';
import { session } from './state.js';
import { renderNavigation } from './tabs.js';
import { toast } from './toast.js';
import { fileKeys, isLocated, registerFile } from './workspace-files.js';

/** Upper bound on the files one file can bring in through its links. */
const MAX_LINKED_SCHEMAS = 200;

/** Runs are serialised so that two files opened together do not both bring in the schema they share. */
let chain = Promise.resolve();

/** Finds the schemas linked from {@code root} (recursively) that the workspace does not know yet. Returns when done. */
export function openLinkedSchemas(root) {
  chain = chain.then(() => busy(t(MSG.BUSY_LINKED), discover(root))).catch(() => { /* reported by resolveLocation */ });
  return chain;
}

async function discover(root) {
  if (!root.model || !isLocated(root)) return;
  const ws = root.workspace;
  const known = new Set([...ws.files.flatMap(fileKeys), ...fileKeys(root)]);
  const queue = [root];
  const found = [];
  while (queue.length && found.length < MAX_LINKED_SCHEMAS) {
    const src = queue.shift();
    for (const i of src.model.imports) {
      if (!i.schemaLocation || found.length >= MAX_LINKED_SCHEMAS) continue;
      const f = await resolveLocation(src, i.schemaLocation, true);
      if (!f || known.has(f.key)) continue;
      known.add(f.key);
      let model;
      try { model = await parseSchema(f.text); } catch (e) { continue; }   // not a schema: not followed
      const entry = registerFile(ws, { name: f.name, path: f.path, rel: f.rel, text: f.text, model });
      found.push(entry);
      queue.push(entry);
    }
  }
  if (!found.length) return;
  if (found.length > MAX_AUTO_OPEN) {
    renderNavigation();
    toast(plural(found.length, MSG.LINKED_LISTED_ONE, MSG.LINKED_LISTED_OTHER));
    return;
  }
  for (const entry of found) await ensureTab(entry);
  toast(plural(found.length, MSG.LINKED_OPENED_ONE, MSG.LINKED_OPENED_OTHER, found.map(f => f.name).join(TEXT.LIST_SEPARATOR)));
  if (session.active.model) renderGraph();   // the other tabs feed the graph (users, level 2)
}
