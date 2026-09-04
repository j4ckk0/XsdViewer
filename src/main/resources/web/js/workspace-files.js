/** The files a workspace knows (workspace.files), open in a tab or only listed: {name, path (on disk), rel (in an opened folder), text, model, failed}. */
import { LIBRARY_KEY_PREFIX, NAME_KEY_PREFIX } from './constants.js';
import { session } from './state.js';
import { activeWorkspace } from './tabs.js';

/** Every identity a file (or the tab showing it) answers to: its path on disk, its path in an opened folder, else its name. */
export function fileKeys(f) {
  const keys = [f.path, f.rel ? LIBRARY_KEY_PREFIX + f.rel : null].filter(Boolean);
  return keys.length ? keys : [NAME_KEY_PREFIX + (f.name || f.fileName)];
}

/** True when a file / tab is the one a resolved location identifies by {@code key}. */
export const hasKey = (f, key) => fileKeys(f).includes(key);

/** True when a tab shows a file with a location (on disk or in a folder): links can be resolved from it. */
export const isLocated = (f) => !!(f.path || f.rel);

/** The entry of {@code ws} sharing an identity with {@code file} ({key} or {path, rel, name}), or null. */
export function findFile(ws, file) {
  const keys = file.key ? [file.key] : fileKeys(file);
  return ws.files.find(f => fileKeys(f).some(k => keys.includes(k))) || null;
}

/** Adds {@code file} ({name, path?, rel?, text, model?}) to {@code ws}, or completes the entry it already has; returns the entry. */
/**
 * What saving a workspace writes: every file of it the server read from disk, and the names of those
 * it cannot write — a file opened in the browser, whose location the server never learnt. A workspace
 * knows more files than it has tabs, since a large folder leaves most of them listed.
 */
export function savableFiles(ws) {
  return {
    saved: ws.files.filter(entry => entry.path),
    skipped: ws.files.filter(entry => !entry.path).map(entry => entry.name),
  };
}

/** The files of the active workspace that are listed but not open in a tab. */
export const listedOnly = () => activeWorkspace().files.filter(entry => !tabOfFile(entry));

export function registerFile(ws, file) {
  let entry = findFile(ws, file);
  if (!entry) {
    entry = { name: file.name, path: file.path || null, rel: file.rel || null, text: file.text, model: file.model || null, failed: false };
    ws.files.push(entry);
    return entry;
  }
  if (file.model) { entry.model = file.model; entry.failed = false; }
  if (file.text != null) entry.text = file.text;
  if (file.path && !entry.path) entry.path = file.path;
  if (file.rel && !entry.rel) entry.rel = file.rel;
  return entry;
}

/** The tab showing {@code entry}, or null. */
export const tabOfFile = (entry) => session.tabs.find(tab => tab.file === entry) || null;

export const workspaceOfFile = (entry) => session.workspaces.find(ws => ws.files.includes(entry)) || null;
