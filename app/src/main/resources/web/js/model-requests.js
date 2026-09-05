/**
 * What the server is given to build or compare a content model: the parsed files of a workspace —
 * their names and texts, which is all a request carries, the server keeping nothing between calls —
 * and the index of the one the declaration is read from. One shape serves the Model view
 * ({@code POST /api/model}) and each side of the comparison ({@code POST /api/compare/declarations}).
 */

/** The files of a workspace the server can read: those parsed, hence known to be schemas. */
const parsedFiles = (ws) => ws.files.filter(f => f.model && f.text != null);

/**
 * One side of a request: the parsed files of {@code ws}, {@code home} the index of {@code entry} among
 * them, and the declaration {@code id}. A tab whose file is registered in no workspace is sent alone,
 * as {@code fallback} (its name and text).
 */
export function sideOf(ws, entry, fallback, id) {
  const files = parsedFiles(ws);
  const list = files.map(f => ({ name: f.name, text: f.text }));
  let home = files.indexOf(entry);
  if (home < 0) {
    list.push({ name: fallback.fileName, text: fallback.text });
    home = list.length - 1;
  }
  return { files: list, home, id };
}

/** The side of a declaration of a tab. */
export const tabSide = (tab, id) => sideOf(tab.workspace, tab.file, tab, id);

/**
 * What tells two requests apart, short of sending the texts twice: how many files of the workspace are
 * parsed — they grow while listed files are parsed in the background — and how long they are together,
 * so that a file opened again with another content is not answered from what was asked before.
 */
export const libraryKey = (ws) => {
  const files = parsedFiles(ws);
  return files.length + ':' + files.reduce((n, f) => n + f.text.length, 0);
};
