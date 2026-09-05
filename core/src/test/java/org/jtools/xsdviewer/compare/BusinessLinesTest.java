package org.jtools.xsdviewer.compare;

/*-
 * #%L
 * XsdViewer
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.jtools.xsdviewer.compare.BusinessLines.Line;
import org.junit.jupiter.api.Test;

class BusinessLinesTest {

    private static List<String> texts(String s) {
        return BusinessLines.of(s).stream().map(Line::text).toList();
    }

    @Test
    void commentsAnnotationsAndWiringTagsAreDroppedIndentationIgnored() {
        String xsd = """
                <?xml version="1.0"?>
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
                </xs:schema>""";
        assertEquals(List.of("<xs:element name=\"a\" type=\"xs:string\">", "</xs:element>", "<xs:complexType name=\"T\">", "<xs:sequence/>", "</xs:complexType>"), texts(xsd));
    }

    @Test
    void lineNumbersAreThoseOfTheOriginalText() {
        List<Line> lines = BusinessLines.of("<!-- c -->\n\n<xs:element name=\"a\"/>\n<xs:annotation/>\n<xs:element name=\"b\"/>");
        assertEquals(List.of(3, 5), lines.stream().map(Line::n).toList());
    }

    @Test
    void aCommentSpanningLinesHidesWhatIsInsideKeepsWhatIsAround() {
        assertEquals(List.of("<a>", "<b/>"), texts("<a><!-- x\ny\nz --><b/>"));
    }

    @Test
    void anEmptyAnnotationTagIsDroppedWithoutSwallowingWhatFollows() {
        assertEquals(List.of("<xs:element name=\"a\"/>"), texts("<xs:annotation/><xs:element name=\"a\"/>"));
    }

    @Test
    void everyLineNumbered() {
        assertEquals(List.of(new Line(1, "a"), new Line(2, ""), new Line(3, "c")), BusinessLines.all("a\n\nc"));
    }
}
