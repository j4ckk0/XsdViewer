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

/**
 * What a box is called when it is folded: the trail of what it and its parents are, from the root.
 * A matched pair shares one trail, so folding a box folds the one matching it on the other side, and
 * the trail says the same thing each time the models are compared — folds outlive a redrawing.
 */
const TRAIL_SEPARATOR = '/', OCCURRENCE = '#';
const trailOf = (parent, box, occurrence) => parent + TRAIL_SEPARATOR + keyOf(box) + OCCURRENCE + occurrence;

/** {@code box} and everything under it are on one side only, so its trail is that side's. */
function markAll(box, mark, counts, trail) {
  box.diff = mark;
  box.foldKey = mark + ':' + trail;
  counts[mark]++;
  const seen = new Map();
  for (const row of rowsOf(box)) {
    const at = (seen.get(keyOf(row)) || 0) + 1;
    seen.set(keyOf(row), at);
    markAll(row, mark, counts, trailOf(trail, row, at));
  }
}

/** Two boxes that stand for the same thing: their own difference, then what they hold. */
function markPair(l, r, counts, trail) {
  const mark = differs(l, r) ? DIFF.CHANGED : DIFF.SAME;
  l.diff = r.diff = mark;
  l.foldKey = r.foldKey = trail;
  counts[mark]++;
  align(l.attributes, r.attributes, counts, trail);
  align(l.children, r.children, counts, trail);
}

/** Two lists of boxes, matched in order by what each box is; each box takes its trail from its parent's. */
function align(ls, rs, counts, trail) {
  const seen = new Map();
  const next = (box) => {
    const at = (seen.get(keyOf(box)) || 0) + 1;
    seen.set(keyOf(box), at);
    return trailOf(trail, box, at);
  };
  const ops = diffLines(ls.map(keyOf), rs.map(keyOf));
  if (!ops) {   // too many boxes to align: pair them by position, the rest is one side's
    const common = Math.min(ls.length, rs.length);
    for (let i = 0; i < common; i++) markPair(ls[i], rs[i], counts, next(ls[i]));
    for (let i = common; i < ls.length; i++) markAll(ls[i], DIFF.REMOVED, counts, next(ls[i]));
    for (let i = common; i < rs.length; i++) markAll(rs[i], DIFF.ADDED, counts, next(rs[i]));
    return;
  }
  for (const o of ops) {
    if (o.op === OP.EQUAL) markPair(ls[o.a], rs[o.b], counts, next(ls[o.a]));
    else if (o.op === OP.DELETE) markAll(ls[o.a], DIFF.REMOVED, counts, next(ls[o.a]));
    else markAll(rs[o.b], DIFF.ADDED, counts, next(rs[o.b]));
  }
}

/**
 * Marks every box of the two content models, either of which may be null when that side does not
 * declare the object. The trees are marked in place; the counts say what was found.
 */
export function markDifferences(left, right) {
  const counts = { [DIFF.SAME]: 0, [DIFF.CHANGED]: 0, [DIFF.REMOVED]: 0, [DIFF.ADDED]: 0 };
  if (left && right) markPair(left, right, counts, '');
  else if (left) markAll(left, DIFF.REMOVED, counts, '');
  else if (right) markAll(right, DIFF.ADDED, counts, '');
  return counts;
}

/** True when the two models hold nothing that differs. */
export const same = (counts) => !counts[DIFF.CHANGED] && !counts[DIFF.REMOVED] && !counts[DIFF.ADDED];
