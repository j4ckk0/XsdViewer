/** Getting a schema into a tab: parsing through the server, indexing, and finding the files links point to. */
import { ServerUnreachableError, locateFile, openLocation, parseSchema } from './api.js';
import { REMOTE_LOCATION_MARK } from './constants.js';
import { $, ID } from './dom.js';
import { t } from './i18n.js';
import { findInLibrary } from './library.js';
import { MSG } from './message-keys.js';
import { indexSchema } from './schema-index.js';
import { newScroll, session } from './state.js';
import { renderTabBar } from './tabs.js';
import { toast } from './toast.js';

/** Parses the schema and fills the tab {@code st} with it (no rendering besides the tab bar). Returns false on error. */
export async function loadInto(st, name, text, path) {
  let json;
  try {
    json = await parseSchema(text);
  } catch (e) {
    toast(e instanceof ServerUnreachableError ? t(MSG.SERVER_UNREACHABLE, e.message) : t(MSG.CANNOT_PARSE, name, e.message));
    return false;
  }
  st.fileName = name;
  st.path = path || null;
  st.rel = null;
  // A file opened in the browser comes without its folder: ask the server where it is (in the background).
  st.located = path ? null : locate(st, name, text);
  st.text = text;
  indexSchema(st, json);
  st.history = [];
  st.filter = '';
  st.scroll = newScroll();
  renderTabBar();
  return true;
}

/** Asks the server for the path of a file opened in the browser (same name and content on disk); sets {@code st.path}. Resolves to the path or null. */
async function locate(st, name, text) {
  try {
    const path = await locateFile(name, text);
    if (!path || st.fileName !== name) return null;   // not found, or the tab was reused meanwhile
    st.path = path;
    if (st === session.active) $(ID.FILE_NAME).title = path;
    renderTabBar();
    return path;
  } catch (e) {
    return null;   // server unreachable: reported when something else is fetched
  }
}

/**
 * Finds the schema at {@code location} (a schemaLocation of the file in tab {@code src}):
 * in the opened folders first, then through the server. With {@code strict}, only relative to
 * the file's own location (nothing when that location is unknown). Returns {key, name, text, path, rel} or null.
 */
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
