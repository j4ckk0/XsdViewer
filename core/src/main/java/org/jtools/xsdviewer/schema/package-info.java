/**
 * Reading a schema file and validating a document against it.
 *
 * <p>The public surface: {@link org.jtools.xsdviewer.schema.SchemaParser} turns the text of an XSD, a
 * WSDL 1.1 or a Schematron into a {@link org.jtools.xsdviewer.schema.SchemaGraph} — one node per
 * global declaration, one edge per direct link, the content model and the lines of each;
 * {@link org.jtools.xsdviewer.schema.XmlValidator} and
 * {@link org.jtools.xsdviewer.schema.SchematronValidator} check a document; the vocabularies
 * ({@link org.jtools.xsdviewer.schema.NodeKind}, {@link org.jtools.xsdviewer.schema.LinkLabel},
 * {@link org.jtools.xsdviewer.schema.ParticleKind}, {@link org.jtools.xsdviewer.schema.Family},
 * {@link org.jtools.xsdviewer.schema.Severity} and the {@code *Names} classes, which hold the words each specification defines) name what they
 * produce; {@link org.jtools.xsdviewer.schema.SchemaException} is what they throw.
 *
 * <p>Two families of constants, which the names keep apart. {@code XsdNames}, {@code WsdlNames} and
 * {@code SchematronNames} hold the words each specification defines — what a file being read is
 * written in, fixed by W3C, OASIS and ISO. {@code NodeKind}, {@code LinkLabel}, {@code ParticleKind},
 * {@code Family} and {@code Severity} hold the words of the model produced, which this project
 * defines and the page mirrors in {@code js/constants.js} — a contract {@code PageContractTest}
 * checks. Neither family is a message key: the texts a reader sees are
 * {@link org.jtools.xsdviewer.MessageKey}, translated in {@code messages*.properties}.
 *
 * <p>Everything else here is package-private and no part of that promise: the parsers of each
 * vocabulary, the line index, the DOM helpers, the XML factories. A consumer cannot reach them, and
 * they change without notice.
 */
package org.jtools.xsdviewer.schema;

/*-
 * #%L
 * XsdViewer core
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
