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
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jtools.xsdviewer.compare.BusinessLines.Line;
import org.jtools.xsdviewer.compare.LineDiff.Op;

/**
 * The files of two workspaces paired by name, each pair with its status: what the Files section of
 * the comparison lists. A name of either side makes a pair; the names are sorted, so the two
 * workspaces read side by side.
 */
public final class WorkspacePairing {

    /** What became of a file from one side to the other. */
    public static final String SAME = "same", DIFFERENT = "different", MOVED = "moved", ONLY_LEFT = "only-left", ONLY_RIGHT = "only-right";

    /** One file name and what became of it. */
    public record Pair(String name, String status) {}

    private WorkspacePairing() {}

    /** Every name of either side, sorted, with the status of its pair; {@code businessOnly} says which lines count as a difference. */
    public static List<Pair> of(Map<String, String> left, Map<String, String> right, boolean businessOnly) {
        TreeSet<String> names = new TreeSet<>(left.keySet());
        names.addAll(right.keySet());
        List<Pair> pairs = new ArrayList<>();
        for (String name : names) pairs.add(new Pair(name, status(left.get(name), right.get(name), businessOnly)));
        return pairs;
    }

    /**
     * What became of one file: only one side has it, the compared lines are equal, they differ only
     * by moved blocks, or they differ. A null text is a side that does not have the file.
     */
    public static String status(String left, String right, boolean businessOnly) {
        if (left == null) return ONLY_RIGHT;
        if (right == null) return ONLY_LEFT;
        List<String> la = TextComparison.lines(left, businessOnly).stream().map(Line::text).toList();
        List<String> lb = TextComparison.lines(right, businessOnly).stream().map(Line::text).toList();
        if (la.equals(lb)) return SAME;
        List<Op> ops = LineDiff.diff(la, lb);
        return ops != null && LineDiff.onlyMoves(ops) ? MOVED : DIFFERENT;
    }
}
