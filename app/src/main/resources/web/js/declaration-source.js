/** The source of one declaration on its own, cut out of its file: what the comparison shows as text. */
const LINE_BREAK = /\r?\n/;

/**
 * The lines a node spans, numbered as in its file: [{n, text}]. Empty when the file does not
 * declare it (a built-in, or an object of another schema: its lines are 0).
 */
export function declarationLines(text, node) {
  if (!text || !node || node.line <= 0 || !node.endLine || node.endLine < node.line) return [];
  return text.split(LINE_BREAK).slice(node.line - 1, node.endLine).map((line, i) => ({ n: node.line + i, text: line }));
}
