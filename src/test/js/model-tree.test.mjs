import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildTree } from '../../main/resources/web/js/model-tree.js';
import { NODE_KIND, PARTICLE } from '../../main/resources/web/js/constants.js';

/** A tab showing a file made of {@code nodes}, with {@code expanded} boxes open and no other file in its workspace. */
function tab(nodes, { edges = [], expanded = [] } = {}) {
  const outEdges = new Map();
  for (const e of edges) {
    if (!outEdges.has(e.from)) outEdges.set(e.from, []);
    outEdges.get(e.from).push(e);
  }
  return {
    nodes: new Map(nodes.map(n => [n.id, n])),
    outEdges,
    modelExpanded: new Set(expanded),
    workspace: { files: [] },
  };
}

const type = (name, content, attributes = []) =>
  ({ id: 'complexType:' + name, kind: NODE_KIND.COMPLEX_TYPE, name, content, attributes });
const sequence = (...children) => ({ kind: PARTICLE.SEQUENCE, min: 1, max: 1, children });

const items = type('Items', [sequence({ kind: PARTICLE.ELEMENT, name: 'item', type: 'complexType:Item', min: 0, max: -1 })]);
const item = type('Item', [sequence({ kind: PARTICLE.ELEMENT, name: 'sku', type: 'builtin:string', min: 1, max: 1 })],
  [{ name: 'partNum', type: 'simpleType:SKU', min: 1, max: 1 }]);

test('a compositor is a box of its own, holding the elements it holds', () => {
  const tree = buildTree(items, tab([items, item]));
  assert.equal(tree.name, 'Items');
  assert.equal(tree.root, true);
  assert.equal(tree.children.length, 1);
  assert.equal(tree.children[0].kind, PARTICLE.SEQUENCE);
  assert.equal(tree.children[0].children[0].name, 'item');
});

test('a named type is a folded handle until its path is opened, and then it holds what that type declares', () => {
  const folded = buildTree(items, tab([items, item])).children[0].children[0];
  assert.equal(folded.typeName, 'Item');
  assert.equal(folded.expandable, true);
  assert.equal(folded.expanded, false);
  assert.equal(folded.children.length, 0);

  const open = buildTree(items, tab([items, item], { expanded: ['/0/0'] })).children[0].children[0];
  assert.equal(open.expanded, true);
  assert.equal(open.children[0].kind, PARTICLE.SEQUENCE, 'the content of the type, from its own declaration');
  assert.equal(open.children[0].children[0].name, 'sku');
  assert.equal(open.attributes[0].name, 'partNum');
});

test('a type already open above stops, rather than repeating itself', () => {
  const tree = type('Tree', [sequence({ kind: PARTICLE.ELEMENT, name: 'child', type: 'complexType:Tree', min: 0, max: -1 })]);
  const child = buildTree(tree, tab([tree])).children[0].children[0];
  assert.equal(child.recursive, true);
  assert.equal(child.expandable, undefined, 'nothing to open: its content is the one being drawn');
});

test('a type declared nowhere, and a simple type, have nothing to open', () => {
  const unknown = type('T', [sequence(
    { kind: PARTICLE.ELEMENT, name: 'a', type: 'type:Absent', min: 1, max: 1 },
    { kind: PARTICLE.ELEMENT, name: 'b', type: 'simpleType:SKU', min: 1, max: 1 })]);
  const simple = { id: 'simpleType:SKU', kind: NODE_KIND.SIMPLE_TYPE, name: 'SKU', content: [], attributes: [] };
  const boxes = buildTree(unknown, tab([unknown, simple])).children[0].children;
  assert.equal(boxes[0].typeName, 'Absent', 'named after the id, the declaration being nowhere');
  assert.equal(boxes[0].expandable, undefined);
  assert.equal(boxes[1].expandable, undefined, 'a simple type holds no particle');
});

test('a global element takes the content model of the type it is of', () => {
  const element = { id: 'element:purchaseOrder', kind: NODE_KIND.ELEMENT, name: 'purchaseOrder', content: [], attributes: [] };
  const tree = buildTree(element, tab([element, items, item],
    { edges: [{ from: element.id, to: items.id, label: 'type' }] }));
  assert.equal(tree.children[0].kind, PARTICLE.SEQUENCE);
  assert.equal(tree.children[0].children[0].name, 'item');
});

test('an anonymous type is drawn in place, with the attributes it declares', () => {
  const holder = type('Holder', [sequence({
    kind: PARTICLE.ELEMENT, name: 'inline', min: 1, max: 1,
    children: [sequence({ kind: PARTICLE.ELEMENT, name: 'deep', type: 'builtin:string', min: 1, max: 1 })],
    attributes: [{ name: 'lang', type: 'builtin:string', min: 0, max: 1 }],
  })]);
  const inline = buildTree(holder, tab([holder])).children[0].children[0];
  assert.equal(inline.expandable, undefined, 'nothing to open: its content is already here');
  assert.equal(inline.children[0].children[0].name, 'deep');
  assert.equal(inline.attributes[0].name, 'lang');
});

// --- a WSDL's or a Schematron's own objects: no content model, but a chain, which is their model

const node = (kind, name) => ({ id: kind + ':' + name, kind, name, content: [], attributes: [] });
const service = node(NODE_KIND.SERVICE, 'PurchaseOrderService');
const portType = node(NODE_KIND.PORT_TYPE, 'PurchaseOrderPortType');
const operation = node(NODE_KIND.OPERATION, 'submitPurchaseOrder');
const chain = [
  { from: service.id, to: portType.id, label: 'PurchaseOrderPort' },
  { from: portType.id, to: operation.id, label: 'operation' },
];

test('a service has its chain for a model: a box named after what the link leads to', () => {
  const tree = buildTree(service, tab([service, portType, operation], { edges: chain }));
  assert.equal(tree.children.length, 1);
  const port = tree.children[0];
  assert.equal(port.name, 'PurchaseOrderPortType', 'named after what it leads to');
  assert.equal(port.word, 'PurchaseOrderPort', 'the link is the port');
  assert.equal(port.kind, NODE_KIND.PORT_TYPE);
  assert.equal(port.ref, portType.id, 'a click selects it');
  assert.equal(port.expandable, true);
  assert.equal(port.children.length, 0, 'folded until its path is opened');
});

test('a chain opens one level at a time, as a content model does', () => {
  const tree = buildTree(service, tab([service, portType, operation], { edges: chain, expanded: ['/0'] }));
  const op = tree.children[0].children[0];
  assert.equal(op.name, 'submitPurchaseOrder');
  assert.equal(op.word, 'operation');
  assert.equal(op.expandable, undefined, 'the operation carries nothing further here');
});

test('where a chain reaches the schema, the content model takes over', () => {
  const message = node(NODE_KIND.MESSAGE, 'SubmitPurchaseOrderRequest');
  const element = { id: 'element:purchaseOrder', kind: NODE_KIND.ELEMENT, name: 'purchaseOrder', attributes: [],
    content: [sequence({ kind: PARTICLE.ELEMENT, name: 'shipTo', type: 'complexType:USAddress', min: 1, max: 1 })] };
  const tree = buildTree(message, tab([message, element],
    { edges: [{ from: message.id, to: element.id, label: 'order' }], expanded: ['/0'] }));
  const part = tree.children[0];
  assert.equal(part.word, 'order', 'the part names the link');
  assert.equal(part.kind, NODE_KIND.ELEMENT);
  assert.equal(part.children[0].kind, PARTICLE.SEQUENCE, 'the element content model, not a chain');
  assert.equal(part.children[0].children[0].name, 'shipTo');
});

test('a declaration that names nothing else draws nothing', () => {
  const simple = node(NODE_KIND.SIMPLE_TYPE, 'SKU');
  const tree = buildTree(simple, tab([simple]));
  assert.equal(tree.children.length, 0);
  assert.equal(tree.attributes.length, 0);
});
