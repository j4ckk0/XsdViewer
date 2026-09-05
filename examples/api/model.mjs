// The content model tree of a declaration — what a document of it holds — every box open.
//   node examples/api/model.mjs complexType:PurchaseOrderType samples/purchaseOrder.xsd samples/ext.xsd
// The first file is the one the declaration is read from; the others are the rest of its workspace,
// where the named types it uses may be declared.
import { filesOf, model } from './xsdviewer.mjs';

const [, , id, ...paths] = process.argv;
const tree = await model(await filesOf(paths), 0, id);
const print = (box, indent) => {
  const card = box.card && (box.card.min !== 1 || box.card.max !== 1) ? ` [${box.card.min}..${box.card.max < 0 ? '*' : box.card.max}]` : '';
  console.log(`${indent}${box.word ? box.word + ' ' : ''}${box.kind}${box.name ? ' ' + box.name : ''}${box.typeName ? ' : ' + box.typeName : ''}${card}${box.recursive ? ' (recursive)' : ''}`);
  box.attributes.forEach(a => print(a, indent + '  @'));
  box.children.forEach(c => print(c, indent + '  '));
};
print(tree, '');
