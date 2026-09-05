package org.jtools.xsdviewer.schema;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jtools.xsdviewer.MessageKey;
import org.jtools.xsdviewer.Messages;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Resolves the {@code include}s of a Schematron in place: each is replaced by the root of the
 * file it names, read next to the Schematron (files only, no URL, nesting bounded). What could
 * not be read is left in place and reported.
 */
final class SchematronIncludes {

    private static final int MAX_DEPTH = 16;
    private static final String REMOTE_LOCATION_MARK = "://";

    private SchematronIncludes() {}

    /** Replaces the includes under {@code root}; answers why each one that could not be read was left as it is. */
    static List<String> resolve(Element root, Path file) {
        List<String> failures = new ArrayList<>();
        resolve(root, file, 0, failures);
        return failures;
    }

    private static void resolve(Element e, Path file, int depth, List<String> failures) {
        for (Element inc : new ArrayList<>(SchematronDom.descendants(e, SchematronNames.INCLUDE))) {
            String href = inc.getAttribute(SchematronNames.ATTR_HREF);
            Path target = file.resolveSibling(href).normalize();
            if (href.isEmpty() || href.contains(REMOTE_LOCATION_MARK) || !Files.isRegularFile(target) || depth >= MAX_DEPTH) {
                failures.add(Messages.get(MessageKey.INCLUDE_NOT_FOUND, href));
                continue;
            }
            Element included;
            try {
                included = SecureXmlFactories.newDocumentBuilder().parse(target.toFile()).getDocumentElement();
            } catch (Exception ex) {
                failures.add(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                continue;
            }
            Node imported = inc.getOwnerDocument().importNode(included, true);
            inc.getParentNode().replaceChild(imported, inc);
            if (imported instanceof Element ie) resolve(ie, target, depth + 1, failures);
        }
    }
}
