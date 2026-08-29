/** The calls to the XsdViewer server (paths: constants.js API, handlers: the server package). */
import { API, API_PARAM, HTTP } from './constants.js';
import { language } from './i18n.js';

/** The server did not answer at all (stopped, or the page was loaded from elsewhere). */
export class ServerUnreachableError extends Error {
  constructor(cause) {
    super(cause.message);
    this.name = ServerUnreachableError.name;
  }
}

/** fetch with the page's language, so that the server answers (errors, generated documentation) in it. */
async function request(url, init = {}) {
  const headers = Object.assign({ [HTTP.ACCEPT_LANGUAGE_HEADER]: language }, init.headers || {});
  try {
    return await fetch(url, Object.assign({}, init, { headers }));
  } catch (e) {
    throw new ServerUnreachableError(e);
  }
}

const textBody = (text) => ({ method: HTTP.POST, headers: { [HTTP.CONTENT_TYPE_HEADER]: HTTP.TEXT_PLAIN_UTF8 }, body: text });
const query = (params) => '?' + new URLSearchParams(params).toString();

/** POST /api/parse: the graph of a schema text. Throws Error(message) when it cannot be parsed. */
export async function parseSchema(text) {
  const resp = await request(API.PARSE, textBody(text));
  const json = await resp.json();
  if (!resp.ok) throw new Error(json.error || String(resp.status));
  return json;
}

/** POST /api/locate: where a file with this name and content sits on disk, or null. */
export async function locateFile(name, text) {
  const resp = await request(API.LOCATE + query({ [API_PARAM.NAME]: name }), textBody(text));
  if (!resp.ok) return null;
  return (await resp.json()).path;
}

/**
 * GET /api/open: {name, path, text} of the schema at {@code location} relative to {@code basePath}, or null.
 * With {@code strict}, only the directory of {@code basePath} is tried.
 */
export async function openLocation(basePath, location, strict = false) {
  const params = { [API_PARAM.BASE]: basePath || '', [API_PARAM.LOCATION]: location };
  if (strict) params[API_PARAM.STRICT] = String(true);
  const resp = await request(API.OPEN + query(params));
  if (!resp.ok) return null;
  return await resp.json();
}

/** GET /api/capabilities: {dialogs} — what the server can do for the page. */
export async function fetchCapabilities() {
  const resp = await request(API.CAPABILITIES);
  return resp.ok ? await resp.json() : {};
}

/** The JSON answer of a POST, or an Error carrying the server's message. */
async function post(url, body) {
  const init = body === undefined ? { method: HTTP.POST }
    : { method: HTTP.POST, headers: { [HTTP.CONTENT_TYPE_HEADER]: HTTP.JSON }, body: JSON.stringify(body) };
  const resp = await request(url, init);
  const json = await resp.json();
  if (!resp.ok) throw new Error(json.error || String(resp.status));
  return json;
}

/** POST /api/choose: the schemas picked in the server's native dialog, [{name, path, text}] (empty when cancelled). */
export async function chooseFiles() {
  return (await post(API.CHOOSE)).files;
}

/**
 * POST /api/workspace/save: {path} of the workspace written through the server's "save as" dialog, or {cancelled}.
 * {@code suggested} is the workspace file proposed by the dialog (the last one opened), or null.
 */
export async function saveWorkspaceFile(files, active, suggested) {
  return post(API.WORKSPACE_SAVE, suggested ? { files, active, path: suggested } : { files, active });
}

/** POST /api/workspace/open: {workspace, active, files: [{name, path, text}], missing} of the workspace picked in the server's dialog, or {cancelled}. */
export async function openWorkspaceFile() {
  return post(API.WORKSPACE_OPEN);
}

/** GET /api/initial: {name, path, text} of the file given on the command line, or null. */
export async function fetchInitialFile() {
  const resp = await request(API.INITIAL);
  if (!resp.ok) return null;
  return await resp.json();
}

/** POST /api/quit: stops the server. Throws when it refuses. */
export async function quitServer() {
  const resp = await request(API.QUIT, { method: HTTP.POST });
  if (!resp.ok) throw new Error(String(resp.status));
}
