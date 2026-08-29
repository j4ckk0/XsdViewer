/** Getting a schema into a tab: parsing through the server, indexing, and finding the files links point to. */
import { ServerUnreachableError, locateFile, openLocation, parseSchema } from './api.js';
import { REMOTE_LOCATION_MARK } from './constants.js';
import { $, ID } from './dom.js';
import { t } from './i18n.js';
import { findInLibrary } from './folder-library.js';
import { MSG } from './message-keys.js';
import { indexSchema } from './schema-index.js';
import { newScroll, session } from './state.js';
import { renderNavigation } from './tabs.js';
import { toast } from './toast.js';
import { registerFile } from './workspace-files.js';

/** Parses the schema into the tab and registers the file in its workspace; false (reported) when the server refuses it. {@code rel}: its path in an opened folder. */
export async function loadInto(st, name, text, path, rel = null) {
  let json;
  try {
    json = await parseSchema(text);
  } catch (e) {
    toast(e instanceof ServerUnreachableError ? t(MSG.SERVER_UNREACHABLE, e.message) : t(MSG.CANNOT_PARSE, name, e.message));
    return false;
  }
  fillTab(st, name, text, path, rel, json);
  st.file = registerFile(st.workspace, { name, path, rel, text, model: json });
  renderNavigation();
  return true;
}

/** Fills the tab {@code st} with a parsed schema: the file, its indexes, a fresh selection and history. */
export function fillTab(st, name, text, path, rel, model) {
  st.fileName = name;
  st.path = path || null;
  st.rel = rel || null;
  // A file opened in the browser comes without its folder: ask the server where it is (in the background).
  st.located = path ? null : locate(st, name, text);
  st.text = text;
  indexSchema(st, model);
  st.history = [];
  st.filter = '';
  st.scroll = newScroll();
}

/** Asks the server for the path of a file opened in the browser (same name and content on disk); sets {@code st.path}. Resolves to the path or null. */
async function locate(st, name, text) {
  try {
    const path = await locateFile(name, text);
    if (!path || st.fileName !== name) return null;   // not found, or the tab was reused meanwhile
    st.path = path;
    if (st.file && !st.file.path) st.file.path = path;
    if (st === session.active) $(ID.FILE_NAME).title = path;
    renderNavigation();
    return path;
  } catch (e) {
    return null;   // server unreachable: reported when something else is fetched
  }
}

/** The file a schemaLocation of {@code src} points to: in the opened folders, then through the server; {@code strict} = relative to {@code src} only. Null when none. */
export async function resolveLocation(src, location, strict = false) {
  if (location.includes(REMOTE_LOCATION_MARK)) return null;
  const fromLibrary = await findInLibrary(src, location, strict);
  if (fromLibrary) return fromLibrary;
  if (strict && !src.path) return null;
  try {
    const f = await openLocation(src.path, location, strict);
    return f ? { key: f.path, name: f.name, text: f.text, path: f.path, rel: null } : null;
  } catch (e) {
    toast(t(MSG.SERVER_UNREACHABLE, e.message));
    return null;
  }
}
