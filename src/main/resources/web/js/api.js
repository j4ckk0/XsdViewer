/** The calls to the XsdViewer server (paths: constants.js API, handlers: the server package). */
import { API, API_PARAM, HTTP } from './constants.js';

/** The server did not answer at all (stopped, or the page was loaded from elsewhere). */
export class ServerUnreachableError extends Error {
  constructor(cause) {
    super(cause.message);
    this.name = ServerUnreachableError.name;
  }
}

async function request(url, init) {
  try {
    return await fetch(url, init);
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

/** GET /api/open: {name, path, text} of the schema at {@code location} relative to {@code basePath}, or null. */
export async function openLocation(basePath, location) {
  const resp = await request(API.OPEN + query({ [API_PARAM.BASE]: basePath || '', [API_PARAM.LOCATION]: location }));
  if (!resp.ok) return null;
  return await resp.json();
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
