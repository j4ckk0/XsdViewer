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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The line diff of two texts: the edit script turning the lines of one into the lines of the other
 * (a longest common subsequence, the common start and end trimmed first), with the moved blocks
 * recognised afterwards — a deleted run that reappears inserted elsewhere is a move, not a change.
 */
public final class LineDiff {

    /** Beyond this many cells of the LCS table (after trimming) no diff is computed: the texts are too different to align. */
    public static final int MAX_CELLS = 9_000_000;
    public static final char EQUAL = '=', DELETE = '-', INSERT = '+';
    /** A block is a move from this many lines on; a single line counts when it is the only unmatched line with that text on each side. */
    private static final int MIN_MOVED_RUN = 2;
    private static final int MAX_MOVE_SEARCH = 200;
    private static final String LINE_BREAK = "\r?\n";

    /** One step of the script: the line index on each side ({@code -1} on the side the line is not), and the move it belongs to, if any. */
    public static final class Op {
        public final char op;
        public final int a, b;
        public boolean moved;
        /** For a deleted line that reappears: where it went; for an inserted line that came from elsewhere: where from. */
        public int movedTo = -1, movedFrom = -1;

        Op(char op, int a, int b) {
            this.op = op;
            this.a = a;
            this.b = b;
        }
    }

    private LineDiff() {}

    /** The lines of a text, either line ending. */
    public static List<String> splitLines(String text) {
        return Arrays.asList(text.split(LINE_BREAK, -1));
    }

    /** The edit script turning {@code a} into {@code b}, or null when the two are too different to align within {@link #MAX_CELLS}. */
    public static List<Op> diff(List<String> a, List<String> b) {
        int start = 0;
        while (start < a.size() && start < b.size() && a.get(start).equals(b.get(start))) start++;
        int endA = a.size(), endB = b.size();
        while (endA > start && endB > start && a.get(endA - 1).equals(b.get(endB - 1))) {
            endA--;
            endB--;
        }
        int n = endA - start, m = endB - start;
        if ((long) n * m > MAX_CELLS) return null;

        // lcs[i][j]: length of the longest common subsequence of a[start+i..endA) and b[start+j..endB)
        int w = m + 1;
        short[] lcs = new short[(n + 1) * (m + 1)];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i * w + j] = a.get(start + i).equals(b.get(start + j))
                        ? (short) (lcs[(i + 1) * w + j + 1] + 1)
                        : (short) Math.max(lcs[(i + 1) * w + j], lcs[i * w + j + 1]);
            }
        }
        List<Op> ops = new ArrayList<>();
        for (int k = 0; k < start; k++) ops.add(new Op(EQUAL, k, k));
        int i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a.get(start + i).equals(b.get(start + j))) {
                ops.add(new Op(EQUAL, start + i, start + j));
                i++;
                j++;
            } else if (j >= m || (i < n && lcs[(i + 1) * w + j] >= lcs[i * w + j + 1])) {
                ops.add(new Op(DELETE, start + i, -1));
                i++;
            } else {
                ops.add(new Op(INSERT, -1, start + j));
                j++;
            }
        }
        for (int k = 0; k < a.size() - endA; k++) ops.add(new Op(EQUAL, endA + k, endB + k));
        markMoves(ops, a, b);
        return ops;
    }

    /** True when the texts differ only by moved blocks. */
    public static boolean onlyMoves(List<Op> ops) {
        boolean any = false;
        for (Op o : ops) {
            if (o.moved) any = true;
            else if (o.op != EQUAL) return false;
        }
        return any;
    }

    /** A line of a run of one kind: its op and its text. */
    private record Lined(Op op, String text) {}

    /** The runs of consecutive ops of {@code kind}, each with its lines' texts. */
    private static List<List<Lined>> runsOf(List<Op> ops, char kind, List<String> texts) {
        List<List<Lined>> runs = new ArrayList<>();
        List<Lined> current = null;
        for (Op op : ops) {
            if (op.op != kind) {
                current = null;
                continue;
            }
            if (current == null) {
                current = new ArrayList<>();
                runs.add(current);
            }
            current.add(new Lined(op, texts.get(kind == DELETE ? op.a : op.b)));
        }
        return runs;
    }

    /** The longest common contiguous sub-run of two runs over their unmarked lines: {length, x, y} (positions in the runs). */
    private static int[] longestCommon(List<Lined> xs, List<Lined> ys) {
        int[] best = { 0, 0, 0 };
        int[] previous = new int[ys.size() + 1];
        for (int i = 1; i <= xs.size(); i++) {
            int[] row = new int[ys.size() + 1];
            for (int j = 1; j <= ys.size(); j++) {
                Lined x = xs.get(i - 1), y = ys.get(j - 1);
                if (!x.op().moved && !y.op().moved && x.text().equals(y.text())) {
                    row[j] = previous[j - 1] + 1;
                    if (row[j] > best[0]) best = new int[] { row[j], i - row[j], j - row[j] };
                }
            }
            previous = row;
        }
        return best;
    }

    private static int unmatched(List<List<Lined>> runs, String text) {
        int n = 0;
        for (List<Lined> r : runs) for (Lined l : r) if (!l.op().moved && l.text().equals(text)) n++;
        return n;
    }

    /**
     * Marks the deleted lines that reappear as inserted lines elsewhere (and those inserted lines).
     * Greedy on the longest common block first, so that a moved block edited a little shows as a move
     * plus the edit.
     */
    private static void markMoves(List<Op> ops, List<String> a, List<String> b) {
        List<List<Lined>> dels = runsOf(ops, DELETE, a), inss = runsOf(ops, INSERT, b);
        if (dels.isEmpty() || inss.isEmpty()) return;
        for (int round = 0; round < MAX_MOVE_SEARCH; round++) {
            int[] best = null;
            List<Lined> bestD = null, bestI = null;
            for (List<Lined> d : dels) {
                for (List<Lined> ins : inss) {
                    int[] c = longestCommon(d, ins);
                    if (c[0] > 0 && (best == null || c[0] > best[0])) {
                        best = c;
                        bestD = d;
                        bestI = ins;
                    }
                }
            }
            if (best == null) return;
            String text = bestD.get(best[1]).text();
            boolean telling = unmatched(dels, text) == 1 && unmatched(inss, text) == 1;
            if (best[0] < MIN_MOVED_RUN && !telling) return;
            Op from = bestD.get(best[1]).op(), to = bestI.get(best[2]).op();
            for (int k = 0; k < best[0]; k++) {
                Op d = bestD.get(best[1] + k).op(), ins = bestI.get(best[2] + k).op();
                d.moved = true;
                d.movedTo = to.b + k;
                ins.moved = true;
                ins.movedFrom = from.a + k;
            }
        }
    }
}
