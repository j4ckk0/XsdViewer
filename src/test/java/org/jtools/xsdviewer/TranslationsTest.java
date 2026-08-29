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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The resource files of each language hold the same keys, and every key the code uses exists:
 * server texts ({@code messages*.properties}) and page labels ({@code web/i18n/*.json}).
 */
class TranslationsTest {

    private static final String BUNDLE_DIR = "/org/jtools/xsdviewer/";
    private static final Path WEB = Path.of("src/main/resources/web");
    private static final List<String> LANGUAGES = List.of("en", "fr");
    private static final String DEFAULT_LANGUAGE = "en";

    // ---- server ---------------------------------------------------------------------------

    private static Set<String> bundleKeys(String file) throws IOException {
        try (InputStream in = TranslationsTest.class.getResourceAsStream(BUNDLE_DIR + file)) {
            assertTrue(in != null, "missing " + file);
            return new TreeSet<>(new PropertyResourceBundle(in).keySet());
        }
    }

    private static Set<String> messageKeyConstants() throws IllegalAccessException {
        Set<String> keys = new TreeSet<>();
        for (Field f : MessageKey.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) keys.add((String) f.get(null));
        }
        return keys;
    }

    @Test
    void serverBundlesHaveTheSameKeys() throws Exception {
        Set<String> base = bundleKeys("messages.properties");
        assertEquals(messageKeyConstants(), base, "MessageKey constants vs messages.properties");
        assertEquals(base, bundleKeys("messages_fr.properties"), "messages.properties vs messages_fr.properties");
    }

    @Test
    void requestLocaleSelectsTheBundle() {
        try {
            Messages.setRequestLocale(Locale.FRENCH);
            assertEquals("POST attendu", Messages.get(MessageKey.POST_EXPECTED));
            Messages.setRequestLocale(Locale.ENGLISH);
            assertEquals("POST expected", Messages.get(MessageKey.POST_EXPECTED));
            Messages.setRequestLocale(Locale.GERMAN);   // no file: the base file, not the JVM's language
            assertEquals("POST expected", Messages.get(MessageKey.POST_EXPECTED));
        } finally {
            Messages.clearRequestLocale();
        }
        assertEquals("fr", Messages.localeOf("fr-FR,fr;q=0.9,en;q=0.8").getLanguage());
        assertEquals("en", Messages.localeOf("en").getLanguage());
        assertEquals(null, Messages.localeOf(null));
        assertEquals(null, Messages.localeOf(" "));
    }

    @Test
    void serverMessagesFormat() {
        assertFalse(Messages.get(MessageKey.USAGE).isEmpty());
        assertTrue(Messages.get(MessageKey.FILE_NOT_FOUND, "x.xsd").contains("x.xsd"));
    }

    // ---- page -----------------------------------------------------------------------------

    /** Keys of a flat JSON object of strings. */
    private static Set<String> jsonKeys(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "missing " + file);
        Set<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:", Pattern.MULTILINE).matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (m.find()) assertTrue(keys.add(m.group(1)), "duplicate key " + m.group(1) + " in " + file);
        return keys;
    }

    private static Set<String> keysUsedByPage() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        Matcher html = Pattern.compile("data-i18n(?:-[a-z]+)?=\"([^\"]+)\"").matcher(Files.readString(WEB.resolve("index.html")));
        while (html.find()) keys.add(html.group(1));
        // message-keys.js: 'a.b' is a key, 'a.' a prefix completed at run time (by a node kind)
        Matcher js = Pattern.compile("'([a-z][A-Za-z0-9]*\\.[A-Za-z0-9.]*)'").matcher(Files.readString(WEB.resolve("js/message-keys.js")));
        while (js.find()) keys.add(js.group(1));
        return keys;
    }

    private static boolean isUsed(String key, Set<String> used) {
        if (used.contains(key)) return true;
        for (String u : used) if (u.endsWith(".") && key.startsWith(u)) return true;
        return false;
    }

    @Test
    void pageLanguagesHaveTheSameKeys() throws Exception {
        Set<String> base = jsonKeys(WEB.resolve("i18n/" + DEFAULT_LANGUAGE + ".json"));
        for (String lang : LANGUAGES) {
            assertEquals(base, jsonKeys(WEB.resolve("i18n/" + lang + ".json")), DEFAULT_LANGUAGE + ".json vs " + lang + ".json");
        }
    }

    @Test
    void everyKeyThePageUsesIsTranslated() throws Exception {
        Set<String> base = jsonKeys(WEB.resolve("i18n/" + DEFAULT_LANGUAGE + ".json"));
        Set<String> used = keysUsedByPage();
        assertFalse(used.isEmpty());
        Set<String> missing = new TreeSet<>();
        for (String key : used) if (!key.endsWith(".") && !base.contains(key)) missing.add(key);
        assertTrue(missing.isEmpty(), "used by the page but not in " + DEFAULT_LANGUAGE + ".json: " + missing);
        Set<String> unused = new TreeSet<>();
        for (String key : base) if (!isUsed(key, used)) unused.add(key);
        assertTrue(unused.isEmpty(), "in " + DEFAULT_LANGUAGE + ".json but never used: " + unused);
    }
}
