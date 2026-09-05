/** The source of one declaration on its own, cut out of its file: what the comparison shows as text. */
import { splitLines } from './diff.js';

/**
 * The lines a node spans, numbered as in its file: [{n, text}]. Empty when the file does not
 * declare it (a built-in, or an object of another schema: its lines are 0).
 */
export function declarationLines(text, node) {
  if (!text || !node || node.line <= 0 || !node.endLine || node.endLine < node.line) return [];
  return splitLines(text).slice(node.line - 1, node.endLine).map((line, i) => ({ n: node.line + i, text: line }));
}

const SPACING = /\s+/g;

/** A line as it is compared: indentation and the runs of blanks inside it ignored, so two declarations written at different depths still match. */
export const shapeOf = (line) => line.replace(SPACING, ' ').trim();
