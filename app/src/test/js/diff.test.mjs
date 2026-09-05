import { test } from 'node:test';
import assert from 'node:assert/strict';
import { OP, diffLines, onlyMoves, splitLines } from '../../main/resources/web/js/diff.js';

const ops = (a, b) => diffLines(splitLines(a), splitLines(b));
const script = (o) => o.map(x => x.op).join('');

test('splitLines takes both line endings', () => {
  assert.deepEqual(splitLines('a\r\nb\nc'), ['a', 'b', 'c']);
});

test('identical texts are all equal ops', () => {
  const o = ops('a\nb\nc', 'a\nb\nc');
  assert.equal(script(o), '===');
  assert.deepEqual(o.map(x => [x.a, x.b]), [[0, 0], [1, 1], [2, 2]]);
});

test('an insertion and a deletion in the middle', () => {
  assert.equal(script(ops('a\nb\nc', 'a\nx\nb\nc')), '=+==');
  assert.equal(script(ops('a\nb\nc', 'a\nc')), '=-=');
  assert.equal(script(ops('a\nb\nc', 'a\nB\nc')), '=-+=');
});

test('a moved block is marked on both sides', () => {
  const o = ops('a\nb\nc\nd\ne', 'c\nd\na\nb\ne');
  const moved = o.filter(x => x.moved);
  assert.ok(moved.length >= 4, 'the two lines of the block, deleted and inserted');
  const del = moved.find(x => x.op === OP.DELETE);
  assert.equal(typeof del.movedTo, 'number');
  const ins = moved.find(x => x.op === OP.INSERT);
  assert.equal(typeof ins.movedFrom, 'number');
  assert.equal(onlyMoves(o), true);
});

test('a single moved line counts when it is the only one with its text', () => {
  const o = ops('a\nb\nc', 'b\nc\na');
  assert.equal(onlyMoves(o), true);
});

test('an edit next to a move is not only moves', () => {
  const o = ops('a\nb\nc\nd', 'c\nd\na\nX');
  assert.equal(onlyMoves(o), false);
});

test('texts too large to align give null', () => {
  const a = Array.from({ length: 4000 }, (_, i) => 'a' + i);
  const b = Array.from({ length: 4000 }, (_, i) => 'b' + i);
  assert.equal(diffLines(a, b), null);
});
