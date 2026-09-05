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

import java.util.prefs.Preferences;

/**
 * The settings the user changes from the page's Settings menu, kept from one run to the next
 * in the user's {@link Preferences} (the registry on Windows, {@code ~/.java/.userPrefs} on
 * Linux, {@code ~/Library/Preferences} on macOS): no file of ours to manage. The command line
 * takes precedence for a run ({@code --keep-alive}, {@code --no-browser}).
 */
public final class UserSettings {

    /** System property naming another preferences node (tests). */
    public static final String NODE_PROPERTY = "xsdviewer.preferences";
    static final String DEFAULT_NODE = "org/jtools/xsdviewer";
    static final String AUTO_STOP = "autoStop";
    static final boolean AUTO_STOP_DEFAULT = true;

    private UserSettings() {}

    static Preferences node() {
        return Preferences.userRoot().node(System.getProperty(NODE_PROPERTY, DEFAULT_NODE));
    }

    /** Whether the server stops by itself once every page has been closed. */
    public static boolean autoStop() {
        return node().getBoolean(AUTO_STOP, AUTO_STOP_DEFAULT);
    }

    public static void setAutoStop(boolean autoStop) {
        node().putBoolean(AUTO_STOP, autoStop);
    }
}
