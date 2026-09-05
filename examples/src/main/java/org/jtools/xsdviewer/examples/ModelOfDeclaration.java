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

import org.jtools.xsdviewer.model.Box;
import org.jtools.xsdviewer.model.ContentTree;
import org.jtools.xsdviewer.model.Library;
import org.jtools.xsdviewer.model.Library.File;
import org.jtools.xsdviewer.schema.SchemaGraph.Cardinality;
import org.jtools.xsdviewer.schema.SchemaGraph.Node;
import org.jtools.xsdviewer.schema.SchemaParser;

/**
 * The content model of one declaration — what a document of it holds — as the Model view draws it:
 * the compositors, the elements with their occurrences and types, the attributes, every named type
 * opened in place from whichever of the given files declares it.
 *
 * <pre>java -cp … org.jtools.xsdviewer.examples.ModelOfDeclaration complexType:PurchaseOrderType samples/purchaseOrder.xsd samples/ext.xsd</pre>
 *
 * The first file is the one the declaration is read from; the others are the rest of its workspace.
 */
public final class ModelOfDeclaration {

    private ModelOfDeclaration() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ModelOfDeclaration <kind:name> <file.xsd> [other files of the workspace...]");
            System.exit(2);
        }
        List<Path> files = new ArrayList<>();
        for (int i = 1; i < args.length; i++) files.add(Path.of(args[i]));
        run(args[0], files, System.out);
    }

    /** The library of the files, the first one being where the declaration lives. */
    static Library library(List<Path> paths) throws Exception {
        List<File> files = new ArrayList<>();
        for (Path p : paths) files.add(new File(p.getFileName().toString(), SchemaParser.parse(Files.readString(p))));
        return new Library(files);
    }

    static void run(String id, List<Path> paths, PrintStream out) throws Exception {
        Library library = library(paths);
        File home = library.files().get(0);
        Node root = home.node(id);
        if (root == null) {
            out.println(home.name + " declares no " + id);
            return;
        }
        // every box open, six levels deep at most (a recursive type stops where it repeats itself)
        Box tree = ContentTree.build(root, home, library, Set.of(), true);
        print(tree, "", out);
    }

    /** One line per box, indented by depth: the attributes of a box, then what it holds. */
    static void print(Box box, String indent, PrintStream out) {
        StringBuilder line = new StringBuilder(indent);
        if (!box.word.isEmpty()) line.append(box.word).append(' ');   // a chain box: the link's word, then what it leads to
        line.append(box.kind);
        if (!box.name.isEmpty()) line.append(' ').append(box.name);
        if (!box.typeName.isEmpty()) line.append(" : ").append(box.typeName);
        String card = Cardinality.text(box.card);
        if (!card.isEmpty() && !card.equals("1")) line.append(" [").append(card).append(']');
        if (box.recursive) line.append(" (recursive)");
        out.println(line);
        for (Box a : box.attributes) print(a, indent + "  @", out);
        for (Box c : box.children) print(c, indent + "  ", out);
    }
}
