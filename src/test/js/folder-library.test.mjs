import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normPath } from '../../main/resources/web/js/folder-library.js';

test('normPath resolves . and .. and backslashes', () => {
  assert.equal(normPath('a/b/../c.xsd'), 'a/c.xsd');
  assert.equal(normPath('./a/./b.xsd'), 'a/b.xsd');
  assert.equal(normPath('a\\b\\c.xsd'), 'a/b/c.xsd');
  assert.equal(normPath('/root/x/../../y.xsd'), 'y.xsd');
  assert.equal(normPath('../up.xsd'), 'up.xsd');
});
