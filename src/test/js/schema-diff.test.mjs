import { test } from 'node:test';
import assert from 'node:assert/strict';
import { diffModels } from '../../main/resources/web/js/schema-diff.js';

const node = (id, kind = id.split(':')[0]) => ({ id, kind, name: id.split(':')[1] });
const model = (nodes, edges) => ({ nodes, edges });

test('identical models are the same', () => {
  const m = model([node('element:a'), node('builtin:string')], [{ from: 'element:a', to: 'builtin:string', label: 'type' }]);
  const d = diffModels(m, m);
  assert.equal(d.same, true);
  assert.deepEqual(d.nodesOnlyLeft, []);
});

test('placeholders do not count, declarations and links do', () => {
  const left = model([node('element:a'), node('external:X', 'external')], [{ from: 'element:a', to: 'external:X', label: 'ref', min: 1, max: 1 }]);
  const right = model([node('element:a'), node('element:b'), node('builtin:string')], [{ from: 'element:a', to: 'external:X', label: 'ref', min: 0, max: 1 }]);
  const d = diffModels(left, right);
  assert.equal(d.same, false);
  assert.deepEqual(d.nodesOnlyLeft, []);
  assert.deepEqual(d.nodesOnlyRight.map(n => n.id), ['element:b']);
  assert.equal(d.edgesOnlyLeft.length, 1, 'the same link with another cardinality is another link');
  assert.equal(d.edgesOnlyRight.length, 1);
});
