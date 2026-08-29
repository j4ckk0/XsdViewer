package org.jtools.xsdviewer;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The texts the server shows or sends to the page (console, API errors, generated documentation),
 * read from {@code messages.properties} (English, the base file) or {@code messages_<language>.properties}.
 * Keys are the constants of {@link MessageKey}.
 *
 * <p>The locale is the one of the request being handled when the page said which language it
 * shows ({@code Accept-Language}, see {@link #setRequestLocale}), else the JVM's default. A
 * language without a file falls back to the base file, never to the JVM's language.
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
