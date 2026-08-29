/** The schema files of the folders opened in the browser (session.library): registration and lookup by schemaLocation. */
import { LIBRARY_KEY_PREFIX, PATH_SEPARATOR, SCHEMA_FILE_PATTERN } from './constants.js';
import { session } from './state.js';

const CURRENT_DIR = '.';
const PARENT_DIR = '..';
const LAST_SEGMENT = /[^/]*$/;

/** "a/b/../c.xsd" -> "a/c.xsd" */
export function normPath(p) {
  const out = [];
  for (const seg of p.replace(/\\/g, PATH_SEPARATOR).split(PATH_SEPARATOR)) {
    if (seg === '' || seg === CURRENT_DIR) continue;
    if (seg === PARENT_DIR) out.pop(); else out.push(seg);
  }
  return out.join(PATH_SEPARATOR);
}

/** Registers the schema files of an opened / dropped folder; {@code relOf} gives a File's path in that folder. Returns how many. */
export function addToLibrary(files, relOf) {
  let n = 0;
  for (const f of files) {
    const r = normPath(relOf(f));
    if (!SCHEMA_FILE_PATTERN.test(r)) continue;
    session.library.set(r, f);
    n++;
  }
  return n;
}

/** Files (recursively) of the entries of a drop, with their path relative to the drop: [{file, rel}]. */
export async function filesOfEntries(entries) {
  const out = [];
  async function walk(entry) {
    if (entry.isFile) {
      const file = await new Promise((res, rej) => entry.file(res, rej));
      out.push({ file, rel: entry.fullPath });
    } else if (entry.isDirectory) {
      const reader = entry.createReader();
      for (;;) {   // readEntries returns the children by batches
        const batch = await new Promise((res, rej) => reader.readEntries(res, rej));
        if (!batch.length) break;
        for (const e of batch) await walk(e);
      }
    }
  }
  for (const e of entries) await walk(e);
  return out;
}

/**
 * The library file at {@code location} (a schemaLocation of the file in tab {@code src}): relative
 * to the file's own folder when known, else (unless {@code strict}) anywhere by path suffix.
 * {key, name, text, path, rel} or null.
 */
export async function findInLibrary(src, location, strict = false) {
  if (!session.library.size) return null;
  let rel = src.rel ? normPath(src.rel.replace(LAST_SEGMENT, '') + location) : null;
  if (rel && !session.library.has(rel)) rel = null;
  if (!rel && !strict) {
    const suffix = normPath(location);
    for (const k of session.library.keys()) if (k === suffix || k.endsWith(PATH_SEPARATOR + suffix)) { rel = k; break; }
  }
  if (!rel) return null;
  const file = session.library.get(rel);
  return { key: LIBRARY_KEY_PREFIX + rel, name: file.name, text: await file.text(), path: null, rel };
}

/** Identity of a loaded file (for the visited set when following links): server path or library path. */
export const tabKey = (t) => t.path || (t.rel ? LIBRARY_KEY_PREFIX + t.rel : null);
