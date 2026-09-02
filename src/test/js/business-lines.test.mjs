import { test } from 'node:test';
import assert from 'node:assert/strict';
import { businessLines } from '../../main/resources/web/js/business-lines.js';

const texts = (s) => businessLines(s).map(l => l.text);

test('comments, annotations and wiring tags are dropped, indentation ignored', () => {
  const xsd = `<?xml version="1.0"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="urn:x">
  <xs:import namespace="urn:y" schemaLocation="y.xsd"/>
  <!-- a comment -->
  <xs:element name="a" type="xs:string">
    <xs:annotation><xs:documentation>doc</xs:documentation></xs:annotation>
  </xs:element>
  <xs:complexType   name="T">
    <xs:annotation>
      <xs:documentation>multi
      line</xs:documentation>
    </xs:annotation>
    <xs:sequence/>
  </xs:complexType>
</xs:schema>`;
  assert.deepEqual(texts(xsd), [
    '<xs:element name="a" type="xs:string">',
    '</xs:element>',
    '<xs:complexType name="T">',
    '<xs:sequence/>',
    '</xs:complexType>',
  ]);
});

test('line numbers are those of the original text', () => {
  const lines = businessLines('<!-- c -->\n\n<xs:element name="a"/>\n<xs:annotation/>\n<xs:element name="b"/>');
  assert.deepEqual(lines.map(l => l.n), [3, 5]);
});

test('a comment spanning lines hides what is inside, keeps what is around', () => {
  assert.deepEqual(texts('<a><!-- x\ny\nz --><b/>'), ['<a>', '<b/>']);
});

test('an empty annotation tag is dropped without swallowing what follows', () => {
  assert.deepEqual(texts('<xs:annotation/><xs:element name="a"/>'), ['<xs:element name="a"/>']);
});
