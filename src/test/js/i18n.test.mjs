import { test } from 'node:test';
import assert from 'node:assert/strict';
import { format } from '../../main/resources/web/js/i18n.js';

test('format replaces the numbered placeholders and leaves the others', () => {
  assert.equal(format('{0} of {1}', [1, 'two']), '1 of two');
  assert.equal(format('{1} then {0}', ['a', 'b']), 'b then a');
  assert.equal(format('{0} and {2}', ['a']), 'a and {2}');
  assert.equal(format('none', []), 'none');
});
