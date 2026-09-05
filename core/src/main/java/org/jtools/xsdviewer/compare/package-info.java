/**
 * Comparing: two declarations, two texts, two schemas, two workspaces.
 *
 * <p>{@link org.jtools.xsdviewer.compare.ModelDiff} marks every box of two content models same,
 * changed, removed or added; {@link org.jtools.xsdviewer.compare.TextComparison} aligns two texts
 * line by line ({@link org.jtools.xsdviewer.compare.LineDiff} for the edit script,
 * {@link org.jtools.xsdviewer.compare.BusinessLines} for what a schema's lines are worth comparing);
 * {@link org.jtools.xsdviewer.compare.SchemaDiff} says what two schemas declare and link that the
 * other does not; {@link org.jtools.xsdviewer.compare.WorkspacePairing} pairs the files of two
 * workspaces by name and gives each pair its status.
 */
package org.jtools.xsdviewer.compare;

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
