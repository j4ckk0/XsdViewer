import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cardinalityText, hasCardinality, isOptional } from '../../main/resources/web/js/cardinality.js';

test('a type link has no cardinality', () => {
  const edge = { from: 'a', to: 'b', label: 'type' };
  assert.equal(hasCardinality(edge), false);
  assert.equal(isOptional(edge), false);
  assert.equal(cardinalityText(edge), '');
});

test('cardinalities are written as in UML', () => {
  assert.equal(cardinalityText({ min: 1, max: 1 }), '1');
  assert.equal(cardinalityText({ min: 0, max: 1 }), '0..1');
  assert.equal(cardinalityText({ min: 1, max: -1 }), '1..*');
  assert.equal(cardinalityText({ min: 0, max: -1 }), '0..*');
  assert.equal(cardinalityText({ min: 2, max: 6 }), '2..6');
  assert.equal(cardinalityText({ min: 0, max: 0 }), '0');
});

test('optional means a minimum of zero', () => {
  assert.equal(isOptional({ min: 0, max: 1 }), true);
  assert.equal(isOptional({ min: 0, max: -1 }), true);
  assert.equal(isOptional({ min: 1, max: -1 }), false);
});
