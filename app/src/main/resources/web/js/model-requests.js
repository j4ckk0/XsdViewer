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

/** What tells two requests of a tab apart, short of their texts: the workspace's parsed files may grow while listed files are parsed in the background. */
export const libraryKey = (ws) => String(parsedFiles(ws).length);
