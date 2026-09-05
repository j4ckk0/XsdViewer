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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jtools.xsdviewer.compare.LineDiff.Op;
import org.jtools.xsdviewer.model.Box;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;

/**
 * What differs between the content model of one declaration on two sides: every box of both trees is
 * marked same, changed, removed or added, and the counts are returned.
 *
 * Boxes are matched by what they are — a compositor by its kind, an element, an attribute or a group
 * reference by its name, a wildcard by its namespaces — and in order, by the same longest common
 * subsequence the line comparison uses, so an element inserted on one side does not shift everything
 * below it. A matched pair is *changed* when its occurrences, the type it names or the word of the
 * link leading to it disagree; what a box holds is then compared in turn.
 */
public final class ModelDiff {

    /** The mark a box carries once compared: a class on its box, which the page's stylesheet colours. */
    public static final String SAME = "same", CHANGED = "changed", REMOVED = "removed", ADDED = "added";

    /** How many boxes of each mark the two trees hold. */
    public static final class Counts {
        public int same, changed, removed, added;

        /** True when the two models hold nothing that differs. */
        public boolean same() {
            return changed == 0 && removed == 0 && added == 0;
        }

        private void count(String mark) {
            switch (mark) {
                case SAME -> same++;
                case CHANGED -> changed++;
                case REMOVED -> removed++;
                default -> added++;
            }
        }
    }

    /**
     * What a box is called when it is folded: the trail of what it and its parents are, from the root.
     * A matched pair shares one trail, so folding a box folds the one matching it on the other side, and
     * the trail says the same thing each time the models are compared — folds outlive a redrawing.
     */
    private static final String TRAIL_SEPARATOR = "/", OCCURRENCE = "#", KEY_SEPARATOR = ":";

    private ModelDiff() {}

    /** What a box is, for matching it with the other side; a compositor has only its kind. */
    private static String keyOf(Box b) {
        return b.kind + KEY_SEPARATOR + (!b.name.isEmpty() ? b.name : b.namespace);
    }

    /** What a matched pair may disagree on: its occurrences, the type it names, the word of the link leading to it. */
    private static boolean differs(Box l, Box r) {
        return !Cardinality.text(l.card).equals(Cardinality.text(r.card)) || !l.typeName.equals(r.typeName) || !l.word.equals(r.word);
    }

    private static List<Box> rowsOf(Box b) {
        List<Box> rows = new ArrayList<>(b.attributes);
        rows.addAll(b.children);
        return rows;
    }

    /** The trails of the boxes under one parent: each box's key, and its rank among its siblings of the same key. */
    private static final class Trails {
        private final Map<String, Integer> seen = new HashMap<>();
        private final String parent;

        Trails(String parent) {
            this.parent = parent;
        }

        String next(Box box) {
            int at = seen.merge(keyOf(box), 1, Integer::sum);
            return parent + TRAIL_SEPARATOR + keyOf(box) + OCCURRENCE + at;
        }
    }

    /** {@code box} and everything under it are on one side only, so its trail is that side's. */
    private static void markAll(Box box, String mark, Counts counts, String trail) {
        box.diff = mark;
        box.foldKey = mark + KEY_SEPARATOR + trail;
        counts.count(mark);
        Trails trails = new Trails(trail);
        for (Box row : rowsOf(box)) markAll(row, mark, counts, trails.next(row));
    }

    /** Two boxes that stand for the same thing: their own difference, then what they hold. */
    private static void markPair(Box l, Box r, Counts counts, String trail) {
        String mark = differs(l, r) ? CHANGED : SAME;
        l.diff = mark;
        r.diff = mark;
        l.foldKey = trail;
        r.foldKey = trail;
        counts.count(mark);
        align(l.attributes, r.attributes, counts, trail);
        align(l.children, r.children, counts, trail);
    }

    /** Two lists of boxes, matched in order by what each box is; each box takes its trail from its parent's. */
    private static void align(List<Box> ls, List<Box> rs, Counts counts, String trail) {
        Trails trails = new Trails(trail);
        List<String> lk = new ArrayList<>(), rk = new ArrayList<>();
        for (Box b : ls) lk.add(keyOf(b));
        for (Box b : rs) rk.add(keyOf(b));
        List<Op> ops = LineDiff.diff(lk, rk);
        if (ops == null) {   // too many boxes to align: pair them by position, the rest is one side's
            int common = Math.min(ls.size(), rs.size());
            for (int i = 0; i < common; i++) markPair(ls.get(i), rs.get(i), counts, trails.next(ls.get(i)));
            for (int i = common; i < ls.size(); i++) markAll(ls.get(i), REMOVED, counts, trails.next(ls.get(i)));
            for (int i = common; i < rs.size(); i++) markAll(rs.get(i), ADDED, counts, trails.next(rs.get(i)));
            return;
        }
        for (Op o : ops) {
            if (o.op == LineDiff.EQUAL) markPair(ls.get(o.a), rs.get(o.b), counts, trails.next(ls.get(o.a)));
            else if (o.op == LineDiff.DELETE) markAll(ls.get(o.a), REMOVED, counts, trails.next(ls.get(o.a)));
            else markAll(rs.get(o.b), ADDED, counts, trails.next(rs.get(o.b)));
        }
    }

    /**
     * Marks every box of the two content models, either of which may be null when that side does not
     * declare the object. The trees are marked in place; the counts say what was found.
     */
    public static Counts mark(Box left, Box right) {
        Counts counts = new Counts();
        if (left != null && right != null) markPair(left, right, counts, "");
        else if (left != null) markAll(left, REMOVED, counts, "");
        else if (right != null) markAll(right, ADDED, counts, "");
        return counts;
    }
}
