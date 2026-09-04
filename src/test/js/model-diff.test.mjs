import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildTree } from '../../main/resources/web/js/model-tree.js';
import { DIFF, markDifferences, same } from '../../main/resources/web/js/model-diff.js';
import { NODE_KIND, PARTICLE } from '../../main/resources/web/js/constants.js';

/** A tab holding one file made of {@code nodes}. */
const tab = (nodes) => ({ nodes: new Map(nodes.map(n => [n.id, n])), outEdges: new Map(), modelExpanded: new Set(), workspace: { files: [] } });
const element = (name, type, min = 1, max = 1) => ({ kind: PARTICLE.ELEMENT, name, type, min, max });
const sequence = (...children) => ({ kind: PARTICLE.SEQUENCE, min: 1, max: 1, children });
/** A complexType whose content is one sequence of {@code children}, with {@code attributes}. */
const type = (name, children, attributes = []) =>
  ({ id: 'complexType:' + name, kind: NODE_KIND.COMPLEX_TYPE, name, content: [sequence(...children)], attributes });

/** The two content models of {@code name}, marked. */
function compare(left, right) {
  const l = buildTree(left, tab([left]), { openAll: true });
  const r = buildTree(right, tab([right]), { openAll: true });
  return { left: l, right: r, counts: markDifferences(l, r) };
}

/** The boxes of a tree in order, root first. */
const boxes = (b) => [b, ...[...b.attributes, ...b.children].flatMap(boxes)];
const named = (tree, name) => boxes(tree).find(b => b.name === name);

test('two identical models differ in nothing', () => {
  const { counts } = compare(type('T', [element('a', 'builtin:string')]), type('T', [element('a', 'builtin:string')]));
  assert.equal(same(counts), true);
  assert.equal(counts[DIFF.SAME], 3, 'the type, its sequence and its element');
});

test('an element on one side only is removed, and the other stays same', () => {
  const { left, right, counts } = compare(
    type('T', [element('a', 'builtin:string'), element('gone', 'builtin:string')]),
    type('T', [element('a', 'builtin:string')]));
  assert.equal(named(left, 'gone').diff, DIFF.REMOVED);
  assert.equal(named(left, 'a').diff, DIFF.SAME);
  assert.equal(counts[DIFF.REMOVED], 1);
  assert.equal(counts[DIFF.ADDED], 0);
});

test('an element inserted on one side does not shift the boxes below it', () => {
  const { left, right, counts } = compare(
    type('T', [element('a', 'builtin:string'), element('c', 'builtin:string')]),
    type('T', [element('a', 'builtin:string'), element('b', 'builtin:string'), element('c', 'builtin:string')]));
  assert.equal(named(right, 'b').diff, DIFF.ADDED);
  assert.equal(named(right, 'c').diff, DIFF.SAME, 'matched by what it is, not by where it sits');
  assert.equal(named(left, 'c').diff, DIFF.SAME);
  assert.equal(counts[DIFF.ADDED], 1);
  assert.equal(counts[DIFF.REMOVED], 0);
});

test('occurrences that disagree make the box changed, not one removed and one added', () => {
  const { left, right, counts } = compare(
    type('T', [element('a', 'builtin:string', 0, 1)]),
    type('T', [element('a', 'builtin:string', 1, -1)]));
  assert.equal(named(left, 'a').diff, DIFF.CHANGED);
  assert.equal(named(right, 'a').diff, DIFF.CHANGED);
  assert.equal(counts[DIFF.CHANGED], 1);
  assert.equal(same(counts), false);
});

test('a type that changed makes the box changed', () => {
  const { left, counts } = compare(
    type('T', [element('a', 'builtin:string')]),
    type('T', [element('a', 'builtin:decimal')]));
  assert.equal(named(left, 'a').diff, DIFF.CHANGED);
  assert.equal(counts[DIFF.CHANGED], 1);
});

test('attributes are compared as elements are', () => {
  const { left, right, counts } = compare(
    type('T', [], [{ name: 'kept', type: 'builtin:string', min: 1, max: 1 }, { name: 'gone', type: 'builtin:string', min: 0, max: 1 }]),
    type('T', [], [{ name: 'kept', type: 'builtin:string', min: 1, max: 1 }, { name: 'new', type: 'builtin:string', min: 0, max: 1 }]));
  assert.equal(named(left, 'gone').diff, DIFF.REMOVED);
  assert.equal(named(right, 'new').diff, DIFF.ADDED);
  assert.equal(named(left, 'kept').diff, DIFF.SAME);
});

test('a compositor that changed carries what it holds', () => {
  const left = { id: 'complexType:T', kind: NODE_KIND.COMPLEX_TYPE, name: 'T', attributes: [],
    content: [{ kind: PARTICLE.CHOICE, min: 1, max: 1, children: [element('a', 'builtin:string')] }] };
  const right = type('T', [element('a', 'builtin:string')]);   // a sequence instead of a choice
  const { counts } = compare(left, right);
  assert.equal(counts[DIFF.REMOVED], 2, 'the choice and its element');
  assert.equal(counts[DIFF.ADDED], 2, 'the sequence and its element');
});

test('a declaration on one side only is wholly removed', () => {
  const l = buildTree(type('T', [element('a', 'builtin:string')]), tab([]), { openAll: true });
  const counts = markDifferences(l, null);
  assert.equal(l.diff, DIFF.REMOVED);
  assert.equal(counts[DIFF.REMOVED], 3);
  assert.equal(same(counts), false);
});

test('a named type is opened on both sides, so a change inside it is seen', () => {
  const inner = (t) => ({ id: 'complexType:Inner', kind: NODE_KIND.COMPLEX_TYPE, name: 'Inner', attributes: [],
    content: [sequence(element('deep', t))] });
  const outer = { id: 'complexType:Outer', kind: NODE_KIND.COMPLEX_TYPE, name: 'Outer', attributes: [],
    content: [sequence(element('held', 'complexType:Inner'))] };
  const l = buildTree(outer, tab([outer, inner('builtin:string')]), { openAll: true });
  const r = buildTree(outer, tab([outer, inner('builtin:decimal')]), { openAll: true });
  const counts = markDifferences(l, r);
  assert.equal(named(l, 'deep').diff, DIFF.CHANGED, 'the element of the named type, opened on both sides');
  assert.equal(counts[DIFF.CHANGED], 1);
});
