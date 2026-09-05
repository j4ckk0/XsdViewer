package org.jtools.xsdviewer.examples;

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

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.jtools.xsdviewer.compare.ModelDiff;
import org.jtools.xsdviewer.compare.ModelDiff.Counts;
import org.jtools.xsdviewer.model.Box;
import org.jtools.xsdviewer.model.ContentTree;
import org.jtools.xsdviewer.model.Library;
import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.schema.SchemaParser;

/**
 * Two declarations compared as the Objects section of the comparison does it: the content model of
 * each, whole, and every box marked same, changed, removed or added — matched by what it is, so an
 * element inserted on one side does not shift the boxes below it.
 *
 * <pre>java -cp … org.jtools.xsdviewer.examples.CompareDeclarations complexType:ProductType samples/compare/v1 samples/compare/v2</pre>
 *
 * Each argument after the declaration is a folder: the schemas it holds are one side's workspace,
 * the declaration being looked for in each of them.
 */
public final class CompareDeclarations {

    private CompareDeclarations() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: CompareDeclarations <kind:name> <folder of version 1> <folder of version 2>");
            System.exit(2);
        }
        run(args[0], Path.of(args[1]), Path.of(args[2]), System.out);
    }

    /** The tree of the declaration in the folder's schemas, or null when none of them declares it. */
    static Box treeOf(String id, Path folder) throws Exception {
        List<File> files = new ArrayList<>();
        try (Stream<Path> paths = Files.list(folder)) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".xsd")).sorted().toList()) {
                files.add(new File(p.getFileName().toString(), SchemaParser.parse(Files.readString(p))));
            }
        }
        Library library = new Library(files);
        for (File f : files) {
            Node root = f.node(id);
            if (root != null) return ContentTree.build(root, f, library, Set.of(), true);
        }
        return null;
    }

    static void run(String id, Path left, Path right, PrintStream out) throws Exception {
        Box l = treeOf(id, left), r = treeOf(id, right);
        Counts counts = ModelDiff.mark(l, r);   // marks both trees in place
        out.printf("%s: %d same, %d changed, %d only in %s, %d only in %s%n", id, counts.same, counts.changed, counts.removed, left, counts.added, right);
        out.println();
        out.println("--- " + left);
        print(l, "", out);
        out.println("--- " + right);
        print(r, "", out);
    }

    /** One line per box with its mark; a box without one (the whole tree on one side only) is marked as its root is. */
    static void print(Box box, String indent, PrintStream out) {
        if (box == null) {
            out.println(indent + "(not declared here)");
            return;
        }
        String mark = switch (box.diff) {
            case ModelDiff.CHANGED -> "~ ";
            case ModelDiff.REMOVED -> "- ";
            case ModelDiff.ADDED -> "+ ";
            default -> "  ";
        };
        out.println(mark + indent + box.kind + (box.name.isEmpty() ? "" : " " + box.name) + (box.typeName.isEmpty() ? "" : " : " + box.typeName));
        for (Box a : box.attributes) print(a, indent + "  @", out);
        for (Box c : box.children) print(c, indent + "  ", out);
    }
}
