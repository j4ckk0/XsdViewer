// Two declarations compared: their trees with every box marked, the counts, the links only one side has.
//   node examples/api/compare-declarations.mjs complexType:ProductType samples/compare/v1 samples/compare/v2
// Each folder is one side's workspace; the declaration is looked for in each of its schemas.
import { compareDeclarations, schemasOf } from './xsdviewer.mjs';

const [, , id, leftFolder, rightFolder] = process.argv;
const side = async (folder) => {
  const files = await schemasOf(folder);
  // the home file is the one declaring the id: the server would refuse a home that does not (400)
  const home = files.findIndex(f => f.text.includes(`name="${id.split(':')[1]}"`));
  return { files, home, id };
};
const r = await compareDeclarations(await side(leftFolder), await side(rightFolder));
console.log(`${id}: ${r.counts.same} same, ${r.counts.changed} changed, ${r.counts.removed} only in ${leftFolder}, ${r.counts.added} only in ${rightFolder}`);
const marks = { same: '  ', changed: '~ ', removed: '- ', added: '+ ' };
const print = (box, indent) => {
  if (!box) { console.log('  (not declared here)'); return; }
  console.log(`${marks[box.diff]}${indent}${box.kind}${box.name ? ' ' + box.name : ''}${box.typeName ? ' : ' + box.typeName : ''}`);
  box.attributes.forEach(a => print(a, indent + '  @'));
  box.children.forEach(c => print(c, indent + '  '));
};
console.log(`--- ${leftFolder}`); print(r.left, '');
console.log(`--- ${rightFolder}`); print(r.right, '');
console.log(`links only in ${leftFolder}: ${r.links.onlyLeft.map(l => l.label).join(', ')}`);
console.log(`links only in ${rightFolder}: ${r.links.onlyRight.map(l => l.label).join(', ')}`);
