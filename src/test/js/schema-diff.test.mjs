import { test } from 'node:test';
import assert from 'node:assert/strict';
import { diffModels, linkKey, neighbourhoodKeys } from '../../main/resources/web/js/schema-diff.js';

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

test('a link is keyed by what it is, not where it is written', () => {
  const string = node('builtin:string', 'builtin');
  const required = { from: 'complexType:T', to: 'builtin:string', label: 'name', min: 1, max: 1 };
  assert.equal(linkKey(string, required), linkKey(string, { ...required, from: 'complexType:U' }), 'the owner is not part of the key');
  assert.notEqual(linkKey(string, required), linkKey(string, { ...required, min: 0 }), 'a changed cardinality is another link');
});

test('the neighbourhood of a node holds its links both ways', () => {
  const t = node('complexType:T'), e = node('element:e'), s = node('builtin:string', 'builtin');
  const out = { from: t.id, to: s.id, label: 'name' }, inn = { from: e.id, to: t.id, label: 'type' };
  const place = { nodes: new Map([[t.id, t], [e.id, e], [s.id, s]]), outEdges: new Map([[t.id, [out]]]), inEdges: new Map([[t.id, [inn]]]) };
  const keys = neighbourhoodKeys(place, t.id);
  assert.equal(keys.size, 2);
  assert.ok(keys.has(linkKey(s, out)) && keys.has(linkKey(e, inn)));
});
