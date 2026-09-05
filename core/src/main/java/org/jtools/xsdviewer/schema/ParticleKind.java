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
/**
 * The kinds of particle of a content model ({@link SchemaGraph.Particle}): what the Model view draws
 * as a box. The compositors and the element keep their XSD names; a base type is the derivation's word.
 */
public final class ParticleKind {

    private ParticleKind() {}

    public static final String SEQUENCE = XsdVocabulary.SEQUENCE;
    public static final String CHOICE = XsdVocabulary.CHOICE;
    public static final String ALL = XsdVocabulary.ALL;
    public static final String ELEMENT = XsdVocabulary.ELEMENT;
    /** A reference to a global group: its content is the group's. */
    public static final String GROUP = XsdVocabulary.GROUP;
    /** A wildcard: its namespace constraint is its name. */
    public static final String ANY = XsdVocabulary.ANY;
    /** The base type of a complex type, whose content comes first. */
    public static final String EXTENDS = LinkLabel.EXTENDS;
    public static final String RESTRICTS = LinkLabel.RESTRICTS;

    /** A sequence, a choice or an all: a particle holding others, drawn as a box of its own. */
    public static boolean isCompositor(String kind) {
        return SEQUENCE.equals(kind) || CHOICE.equals(kind) || ALL.equals(kind);
    }
}
