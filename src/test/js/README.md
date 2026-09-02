# Tests of the page

Unit tests of the page's pure modules (no DOM), run by Node's own test runner:

    node --test 'src/test/js/*.test.mjs'

`JavaScriptTestsTest` runs them from Maven when `node` is on the PATH (or named by the `NODE`
environment variable / the `node` system property); without Node they are skipped, with a note.
The CI workflow installs Node before Maven, so they always run there.
