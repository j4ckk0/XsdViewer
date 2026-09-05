// The graph of a schema file: its declarations and the links between them.
//   node examples/api/parse.mjs samples/purchaseOrder.xsd
import { readFile } from 'node:fs/promises';
import { parse } from './xsdviewer.mjs';

const graph = await parse(await readFile(process.argv[2], 'utf8'));
console.log(`target namespace: ${graph.targetNamespace || '(none)'}`);
for (const n of graph.nodes) {
  if (n.kind === 'builtin' || n.kind === 'external') continue;   // placeholders for what the file uses without declaring
  console.log(`  ${n.kind.padEnd(14)} ${n.name.padEnd(28)} lines ${n.line}-${n.endLine}`);
}
for (const e of graph.edges) console.log(`  ${e.from} --${e.label}${e.min != null ? ' ' + e.min + '..' + (e.max < 0 ? '*' : e.max) : ''}--> ${e.to}`);
