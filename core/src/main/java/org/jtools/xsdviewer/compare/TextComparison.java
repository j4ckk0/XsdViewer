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

import java.util.List;
import java.util.regex.Pattern;

import org.jtools.xsdviewer.compare.BusinessLines.Line;
import org.jtools.xsdviewer.compare.LineDiff.Op;

/**
 * Two texts compared line by line: which lines are compared, on what they are matched, and the edit
 * script turning one into the other.
 *
 * <p>{@code businessOnly} compares the lines that define a schema, leaving out comments,
 * {@code xs:annotation} blocks and the wiring tags ({@link BusinessLines}). {@code ignoreSpacing}
 * matches lines on their shape — indentation and the runs of blanks inside them ignored — while the
 * lines answered stay as they are written: two declarations at different depths then match line for
 * line. The lines always carry their number in the original text, so a reader is shown where they are.
 */
public final class TextComparison {

    /** The lines of each side, the edit script (null when the texts are too different to align), and whether the two differ only by moved blocks. */
    public record Result(List<Line> la, List<Line> lb, List<Op> ops, boolean onlyMoves) {}

    private static final Pattern SPACING = Pattern.compile("\\s+");
    /** A Windows line ending is a line ending: the two texts may come from different machines. */
    private static final String WINDOWS_LINE_BREAK = "\r\n", LINE_BREAK = "\n";

    private TextComparison() {}

    public static Result of(String left, String right, boolean businessOnly, boolean ignoreSpacing) {
        List<Line> la = lines(left, businessOnly), lb = lines(right, businessOnly);
        List<Op> ops = LineDiff.diff(matched(la, ignoreSpacing), matched(lb, ignoreSpacing));
        return new Result(la, lb, ops, ops != null && LineDiff.onlyMoves(ops));
    }

    /** The lines compared, with their number in the original text: every line, or the business lines only. */
    public static List<Line> lines(String text, boolean businessOnly) {
        String canonical = text.replace(WINDOWS_LINE_BREAK, LINE_BREAK);
        return businessOnly ? BusinessLines.of(canonical) : BusinessLines.all(canonical);
    }

    /** What the diff matches the lines on: the lines themselves, or their shape. */
    private static List<String> matched(List<Line> lines, boolean ignoreSpacing) {
        return lines.stream().map(l -> ignoreSpacing ? shapeOf(l.text()) : l.text()).toList();
    }

    /** A line's shape: its indentation and the runs of blanks inside it collapsed to one space. */
    public static String shapeOf(String line) {
        return SPACING.matcher(line).replaceAll(" ").trim();
    }
}
