/**
 * Calling the XsdViewer server from JavaScript (Node 18 or later, or a browser page): the five
 * calls of its API, each stateless — the request carries the files' texts, the server keeps nothing.
 * The server is a local tool: scripts/run.sh --no-browser --keep-alive starts it on 127.0.0.1:8080;
 * XSDVIEWER_URL points these programs at another address.
 */
import { readFile, readdir } from 'node:fs/promises';
import { basename, join } from 'node:path';

export const url = process.env.XSDVIEWER_URL || 'http://127.0.0.1:8080';

/** A POST answered with JSON; the server's error message when it refuses the request. */
async function post(path, body, contentType = 'application/json') {
  const resp = await fetch(url + path, { method: 'POST', headers: { 'Content-Type': contentType }, body });
  const json = await resp.json();
  if (!resp.ok) throw new Error(json.error || String(resp.status));
  return json;
}

/** POST /api/parse: the graph of a schema text — declarations, links, content models. */
export const parse = (text) => post('/api/parse', text, 'text/plain; charset=utf-8');

/** POST /api/model: the content model tree of the declaration {@code id} of {@code files[home]}, every box open. */
export const model = (files, home, id) => post('/api/model', JSON.stringify({ files, home, id, openAll: true }));

/** POST /api/compare/declarations: the two trees marked, the counts, the links only one side has. */
export const compareDeclarations = (left, right) => post('/api/compare/declarations', JSON.stringify({ left, right }));

/** POST /api/compare/texts: two texts line by line; businessOnly leaves out comments, annotations and the wiring tags. */
export const compareTexts = (left, right, options = {}) => post('/api/compare/texts', JSON.stringify({ left, right, ...options }));

/** POST /api/compare/workspaces: the files of two workspaces paired by name, a status each. */
export const compareWorkspaces = (left, right, businessOnly = true) => post('/api/compare/workspaces', JSON.stringify({ left, right, businessOnly }));

/** The {name, text} of the files named, as a request lists them. */
export const filesOf = async (paths) => Promise.all(paths.map(async p => ({ name: basename(p), text: await readFile(p, 'utf8') })));

/** The schemas of a folder, as a workspace's files. */
export async function schemasOf(folder) {
  const names = (await readdir(folder)).filter(n => n.endsWith('.xsd')).sort();
  return filesOf(names.map(n => join(folder, n)));
}
