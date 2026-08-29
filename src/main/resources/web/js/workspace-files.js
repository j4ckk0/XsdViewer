/**
 * The files of a workspace (workspace.files): every schema it knows, open in a tab or only listed
 * in the Files panel. An entry is {name, path, rel, text, model, failed}: path on disk when the
 * server read it, rel (its path in an opened folder) when the browser did, model once parsed.
 */
import { LIBRARY_KEY_PREFIX, NAME_KEY_PREFIX } from './constants.js';
import { session } from './state.js';

/** Every identity a file answers to: its path on disk, its path in an opened folder, else its name. */
export function fileKeys(f) {
  const keys = [f.path, f.rel ? LIBRARY_KEY_PREFIX + f.rel : null].filter(Boolean);
  return keys.length ? keys : [NAME_KEY_PREFIX + f.name];
}

/** The entry of {@code ws} sharing an identity with {@code file} ({key} or {path, rel, name}), or null. */
export function findFile(ws, file) {
  const keys = file.key ? [file.key] : fileKeys(file);
  return ws.files.find(f => fileKeys(f).some(k => keys.includes(k))) || null;
}

/** Adds {@code file} ({name, path?, rel?, text, model?}) to {@code ws}, or completes the entry it already has; returns the entry. */
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
