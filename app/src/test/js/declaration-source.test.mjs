import { test } from 'node:test';
import assert from 'node:assert/strict';
import { declarationLines } from '../../main/resources/web/js/declaration-source.js';

const text = ['<xs:schema>', '  <xs:complexType name="T">', '    <xs:sequence/>', '  </xs:complexType>', '  <xs:element name="e"/>', '</xs:schema>'].join('\n');

test('the lines a declaration spans, numbered as in the file', () => {
  assert.deepEqual(declarationLines(text, { line: 2, endLine: 4 }).map(l => l.n), [2, 3, 4]);
  assert.equal(declarationLines(text, { line: 2, endLine: 4 })[0].text, '  <xs:complexType name="T">');
  assert.deepEqual(declarationLines(text, { line: 5, endLine: 5 }).map(l => l.text), ['  <xs:element name="e"/>'], 'a self-closed tag is one line');
});

test('nothing for a node the file does not declare', () => {
  assert.deepEqual(declarationLines(text, { line: 0, endLine: 0 }), []);
  assert.deepEqual(declarationLines(text, { line: 3, endLine: 0 }), [], 'a start without an end is not a span');
  assert.deepEqual(declarationLines(null, { line: 2, endLine: 4 }), []);
});
