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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jtools.xsdviewer.compare.LineDiff.Op;
import org.junit.jupiter.api.Test;

class LineDiffTest {

    private static List<Op> ops(String a, String b) {
        return LineDiff.diff(LineDiff.splitLines(a), LineDiff.splitLines(b));
    }

    private static String script(List<Op> ops) {
        StringBuilder sb = new StringBuilder();
        for (Op o : ops) sb.append(o.op);
        return sb.toString();
    }

    @Test
    void splitLinesTakesBothLineEndings() {
        assertEquals(List.of("a", "b", "c"), LineDiff.splitLines("a\r\nb\nc"));
    }

    @Test
    void identicalTextsAreAllEqualOps() {
        List<Op> o = ops("a\nb\nc", "a\nb\nc");
        assertEquals("===", script(o));
        assertEquals(List.of(0, 1, 2), o.stream().map(x -> x.a).toList());
        assertEquals(List.of(0, 1, 2), o.stream().map(x -> x.b).toList());
    }

    @Test
    void anInsertionAndADeletionInTheMiddle() {
        assertEquals("=+==", script(ops("a\nb\nc", "a\nx\nb\nc")));
        assertEquals("=-=", script(ops("a\nb\nc", "a\nc")));
        assertEquals("=-+=", script(ops("a\nb\nc", "a\nB\nc")));
    }

    @Test
    void aMovedBlockIsMarkedOnBothSides() {
        List<Op> o = ops("a\nb\nc\nd\ne", "c\nd\na\nb\ne");
        List<Op> moved = o.stream().filter(x -> x.moved).toList();
        assertTrue(moved.size() >= 4, "the two lines of the block, deleted and inserted");
        Op del = moved.stream().filter(x -> x.op == LineDiff.DELETE).findFirst().orElseThrow();
        assertTrue(del.movedTo >= 0);
        Op ins = moved.stream().filter(x -> x.op == LineDiff.INSERT).findFirst().orElseThrow();
        assertTrue(ins.movedFrom >= 0);
        assertTrue(LineDiff.onlyMoves(o));
    }

    @Test
    void aSingleMovedLineCountsWhenItIsTheOnlyOneWithItsText() {
        assertTrue(LineDiff.onlyMoves(ops("a\nb\nc", "b\nc\na")));
    }

    @Test
    void anEditNextToAMoveIsNotOnlyMoves() {
        assertFalse(LineDiff.onlyMoves(ops("a\nb\nc\nd", "c\nd\na\nX")));
    }

    @Test
    void textsTooLargeToAlignGiveNull() {
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            a.add("a" + i);
            b.add("b" + i);
        }
        assertNull(LineDiff.diff(a, b));
    }
}
