// Two files compared line by line, as the Files section of the comparison shows a differing pair.
//   node examples/api/compare-texts.mjs samples/compare/v1/product.xsd samples/compare/v2/product.xsd
import { readFile } from 'node:fs/promises';
import { compareTexts } from './xsdviewer.mjs';

const [left, right] = await Promise.all(process.argv.slice(2, 4).map(p => readFile(p, 'utf8')));
const r = await compareTexts(left, right, { businessOnly: true });
if (!r.ops) { console.log('too different to align'); process.exit(0); }
for (const op of r.ops) {
  if (op.op === '=') console.log(`  ${String(r.la[op.a].n).padStart(4)} ${r.la[op.a].text}`);
  else if (op.op === '-') console.log(`- ${String(r.la[op.a].n).padStart(4)} ${r.la[op.a].text}${op.moved ? '   (moved to ' + r.lb[op.movedTo].n + ')' : ''}`);
  else console.log(`+ ${String(r.lb[op.b].n).padStart(4)} ${r.lb[op.b].text}${op.moved ? '   (moved from ' + r.la[op.movedFrom].n + ')' : ''}`);
}
console.log(r.onlyMoves ? 'only moved blocks' : `${r.ops.filter(o => o.op !== '=').length} lines differ`);
