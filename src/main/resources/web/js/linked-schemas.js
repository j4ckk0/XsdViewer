/**
 * Automatic loading of the schemas a file links to: its xs:import / xs:include / xs:redefine whose
 * schemaLocation resolves relative to the file's own location, and theirs in turn, opened in
 * background tabs of the file's workspace. Nothing is guessed: a file without a known location links nowhere.
 */
import { TEXT } from './constants.js';
import { renderGraph } from './graph.js';
import { plural } from './i18n.js';
import { tabKeys } from './folder-library.js';
import { MSG } from './message-keys.js';
import { loadInto, resolveLocation } from './schema-loader.js';
import { session } from './state.js';
import { closeTab, newTab, renderTabBar, tabsOf } from './tabs.js';
import { toast } from './toast.js';

/** Upper bound on the tabs one file can open through its links. */
const MAX_LINKED_SCHEMAS = 50;

/** Runs are serialised so that two files opened together do not both open the schema they share. */
let chain = Promise.resolve();

/** Opens the schemas linked from {@code root} (recursively) that are not open yet. Returns when done. */
export function openLinkedSchemas(root) {
  chain = chain.then(() => open(root)).catch(() => { /* reported by loadInto / resolveLocation */ });
  return chain;
}

async function open(root) {
  if (!root.model || !tabKeys(root).length) return;
  const visited = new Set(tabsOf(root.workspace).flatMap(tabKeys));
  const queue = [root];
  const opened = [];
  while (queue.length && opened.length < MAX_LINKED_SCHEMAS) {
    const src = queue.shift();
    for (const i of src.model.imports) {
      if (!i.schemaLocation || opened.length >= MAX_LINKED_SCHEMAS) continue;
      const f = await resolveLocation(src, i.schemaLocation, true);
      if (!f || visited.has(f.key)) continue;
      visited.add(f.key);
      const tab = newTab(root.workspace);
      if (!(await loadInto(tab, f.name, f.text, f.path))) { closeTab(tab); renderTabBar(); continue; }
      tab.rel = f.rel;
      opened.push(tab);
      queue.push(tab);
    }
  }
  if (!opened.length) return;
  toast(plural(opened.length, MSG.LINKED_OPENED_ONE, MSG.LINKED_OPENED_OTHER, opened.map(t => t.fileName).join(TEXT.LIST_SEPARATOR)));
  if (session.active.model) renderGraph();   // the other tabs feed the graph (users, level 2)
}
