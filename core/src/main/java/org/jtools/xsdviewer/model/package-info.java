/**
 * The content model of a declaration: what a document of it holds.
 *
 * <p>{@link org.jtools.xsdviewer.model.ContentTree} builds a tree of
 * {@link org.jtools.xsdviewer.model.Box}es — the compositors, the elements with their occurrences and
 * types, the attributes — walking an anonymous type in place and opening a named one from whichever
 * file of the {@link org.jtools.xsdviewer.model.Library} declares it. It is what the Model view of
 * the page draws and what the comparison of two declarations aligns.
 */
package org.jtools.xsdviewer.model;

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
