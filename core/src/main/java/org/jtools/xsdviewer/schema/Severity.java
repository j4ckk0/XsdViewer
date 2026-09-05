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
/** How bad a validation problem is: the vocabulary of {@code severity} handed to the page. */
public final class Severity {

    private Severity() {}

    /** The document does not conform: an XSD error, a failed Schematron assertion. */
    public static final String ERROR = "error";
    /** Worth a look, does not make the document invalid: a validator warning, an assertion with a warning role. */
    public static final String WARNING = "warning";
    /** An assertion with an informative role. */
    public static final String INFO = "info";
    /** A Schematron expression the evaluator could not run (XPath 2 and later): nothing was checked there. */
    public static final String UNSUPPORTED = "unsupported";
}
