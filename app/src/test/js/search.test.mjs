import { test } from 'node:test';
import assert from 'node:assert/strict';
import { matchedBy, matches } from '../../main/resources/web/js/search.js';

const node = { name: 'PurchaseOrderType', members: ['shipTo', 'billTo', 'orderDate'], doc: 'A whole purchase order.' };

test('the search matches the name, a member or the documentation, case-insensitively', () => {
  assert.equal(matches(node, ''), true);
  assert.equal(matches(node, 'ordertype'), true);
  assert.equal(matches(node, 'shipto'), true);
  assert.equal(matches(node, 'whole'), true);
  assert.equal(matches(node, 'nothing'), false);
  assert.equal(matches({ name: 'x' }, 'y'), false, 'no members, no doc');
});

test('matchedBy says what matched when the name did not', () => {
  assert.equal(matchedBy(node, 'order'), null, 'the name matches');
  assert.equal(matchedBy(node, 'billto'), 'billTo');
  assert.equal(typeof matchedBy(node, 'whole'), 'string');
  assert.equal(matchedBy(node, 'nothing'), null);
});
