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

// --- what saving a workspace writes

import { savableFiles } from '../../main/resources/web/js/workspace-files.js';

const entry = (name, path) => ({ name, path, rel: null, text: '', model: null, failed: false });

test('a workspace is saved with every file it knows, not only those open in a tab', () => {
  const ws = { files: [entry('a.xsd', '/d/a.xsd'), entry('b.xsd', '/d/b.xsd'), entry('c.xsd', '/d/c.xsd')] };
  const { saved, skipped } = savableFiles(ws);
  assert.deepEqual(saved.map(e => e.path), ['/d/a.xsd', '/d/b.xsd', '/d/c.xsd'], 'a large folder leaves most files listed, and they belong to the workspace');
  assert.deepEqual(skipped, []);
});

test('a file the server never located cannot be saved, and is named', () => {
  const ws = { files: [entry('a.xsd', '/d/a.xsd'), entry('dropped.xsd', null)] };
  const { saved, skipped } = savableFiles(ws);
  assert.deepEqual(saved.map(e => e.name), ['a.xsd']);
  assert.deepEqual(skipped, ['dropped.xsd']);
});
