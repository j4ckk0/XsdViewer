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

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The texts the server shows or sends to the page, from {@code messages_<language>.properties}
 * (English base file) in the language the request asked for, else the JVM's. Keys: {@link MessageKey}.
 */
public final class Messages {

    public static final String BUNDLE_NAME = "org.jtools.xsdviewer.messages";

    private static final ResourceBundle.Control NO_DEFAULT_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final ThreadLocal<Locale> REQUEST_LOCALE = new ThreadLocal<>();
    private static final String LANGUAGE_SEPARATORS = "[,;]";

    private Messages() {}

    /** The message {@code key} in the current locale, with {@code {0}}, {@code {1}}... replaced by {@code args} ({@link MessageFormat}). */
    public static String get(String key, Object... args) {
        ResourceBundle bundle = bundle(locale());
        return new MessageFormat(bundle.getString(key), bundleLocale(bundle)).format(args);
    }

    /** The locale of the request being handled, else the JVM's default. */
    public static Locale locale() {
        Locale l = REQUEST_LOCALE.get();
        return l != null ? l : Locale.getDefault();
    }

    /** Makes the messages of the current thread (one request) use {@code locale}; null restores the default. */
    public static void setRequestLocale(Locale locale) {
        if (locale == null) REQUEST_LOCALE.remove();
        else REQUEST_LOCALE.set(locale);
    }

    public static void clearRequestLocale() {
        REQUEST_LOCALE.remove();
    }

    /** The first language of an {@code Accept-Language} header ({@code fr-FR,fr;q=0.9,en;q=0.8} → fr), null when absent. */
    public static Locale localeOf(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return null;
        String first = acceptLanguage.split(LANGUAGE_SEPARATORS)[0].trim();
        return first.isEmpty() ? null : Locale.forLanguageTag(first);
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_NAME, locale, NO_DEFAULT_LOCALE_FALLBACK);
    }

    /** The bundle's own locale; the base file is English. */
    private static Locale bundleLocale(ResourceBundle bundle) {
        Locale l = bundle.getLocale();
        return l.getLanguage().isEmpty() ? Locale.ENGLISH : l;
    }
}
