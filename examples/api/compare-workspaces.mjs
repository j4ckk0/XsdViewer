// The schemas of two folders paired by name, each pair with its status.
//   node examples/api/compare-workspaces.mjs samples/compare/v1 samples/compare/v2
import { compareWorkspaces, schemasOf } from './xsdviewer.mjs';

const [, , left, right] = process.argv;
const { pairs } = await compareWorkspaces(await schemasOf(left), await schemasOf(right));
for (const p of pairs) console.log(`  ${p.name.padEnd(16)} ${p.status}`);
