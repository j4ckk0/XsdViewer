package org.jtools.xsdviewer;

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

/** What this build is: the version from the jar manifest ({@code Implementation-Version}, set by Maven) and the Java runtime. */
public final class BuildInfo {

    /** Version shown when there is no manifest (run from the classes directory, e.g. from an IDE). */
    public static final String DEVELOPMENT_VERSION = "dev";

    private BuildInfo() {}

    public static String version() {
        String v = BuildInfo.class.getPackage() == null ? null : BuildInfo.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? DEVELOPMENT_VERSION : v;
    }

    private static final String JAVA_VERSION_PROPERTY = "java.version";

    /** "21.0.12": the runtime's version without build details. */
    public static String javaVersion() {
        return System.getProperty(JAVA_VERSION_PROPERTY, Integer.toString(Runtime.version().feature()));
    }
}
