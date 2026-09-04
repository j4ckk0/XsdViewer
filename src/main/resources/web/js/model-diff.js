/**
 * What differs between the content model of one declaration on two sides: every box of both trees is
 * marked same, changed, removed or added, and the counts are returned.
 *
 * Boxes are matched by what they are — a compositor by its kind, an element, an attribute or a group
 * reference by its name, a wildcard by its namespaces — and in order, by the same longest common
 * subsequence the line comparison uses, so an element inserted on one side does not shift everything
 * below it. A matched pair is *changed* when its occurrences, the type it names or the word of the
 * link leading to it disagree; what a box holds is then compared in turn.
 */
import { cardinalityText } from './cardinality.js';
import { OP, diffLines } from './diff.js';

/** The mark a box carries once compared: a class on its box, which the stylesheet colours. */
export const DIFF = { SAME: 'same', CHANGED: 'changed', REMOVED: 'removed', ADDED: 'added' };

/** What a box is, for matching it with the other side; a compositor has only its kind. */
const keyOf = (b) => b.kind + ':' + (b.name || b.namespace || '');

/** What a matched pair may disagree on: its occurrences, the type it names, the word of the link leading to it. */
const differs = (l, r) => cardinalityText(l.card || {}) !== cardinalityText(r.card || {})
  || (l.typeName || '') !== (r.typeName || '')
  || (l.word || '') !== (r.word || '');

const rowsOf = (b) => [...b.attributes, ...b.children];

/** {@code box} and everything under it are on one side only. */
function markAll(box, mark, counts) {
  box.diff = mark;
  counts[mark]++;
  for (const row of rowsOf(box)) markAll(row, mark, counts);
}

/** Two boxes that stand for the same thing: their own difference, then what they hold. */
function markPair(l, r, counts) {
  const mark = differs(l, r) ? DIFF.CHANGED : DIFF.SAME;
  l.diff = r.diff = mark;
  counts[mark]++;
  align(l.attributes, r.attributes, counts);
  align(l.children, r.children, counts);
}

/** Two lists of boxes, matched in order by what each box is. */
function align(ls, rs, counts) {
  const ops = diffLines(ls.map(keyOf), rs.map(keyOf));
  if (!ops) {   // too many boxes to align: pair them by position, the rest is one side's
    const common = Math.min(ls.length, rs.length);
    for (let i = 0; i < common; i++) markPair(ls[i], rs[i], counts);
    for (let i = common; i < ls.length; i++) markAll(ls[i], DIFF.REMOVED, counts);
    for (let i = common; i < rs.length; i++) markAll(rs[i], DIFF.ADDED, counts);
    return;
  }
  for (const o of ops) {
    if (o.op === OP.EQUAL) markPair(ls[o.a], rs[o.b], counts);
    else if (o.op === OP.DELETE) markAll(ls[o.a], DIFF.REMOVED, counts);
    else markAll(rs[o.b], DIFF.ADDED, counts);
  }
}

/**
 * Marks every box of the two content models, either of which may be null when that side does not
 * declare the object. The trees are marked in place; the counts say what was found.
 */
export function markDifferences(left, right) {
  const counts = { [DIFF.SAME]: 0, [DIFF.CHANGED]: 0, [DIFF.REMOVED]: 0, [DIFF.ADDED]: 0 };
  if (left && right) markPair(left, right, counts);
  else if (left) markAll(left, DIFF.REMOVED, counts);
  else if (right) markAll(right, DIFF.ADDED, counts);
  return counts;
}

/** True when the two models hold nothing that differs. */
export const same = (counts) => !counts[DIFF.CHANGED] && !counts[DIFF.REMOVED] && !counts[DIFF.ADDED];
