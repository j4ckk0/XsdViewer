package org.jtools.xsdviewer;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The texts the server shows or sends to the page (console, API errors, generated documentation),
 * read from {@code messages.properties} (English) or {@code messages_<language>.properties}
 * for the JVM's default locale. Keys are the constants of {@link MessageKey}.
 */
public final class Messages {

    public static final String BUNDLE_NAME = "org.jtools.xsdviewer.messages";

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

    private Messages() {}

    /** The message {@code key}, with {@code {0}}, {@code {1}}... replaced by {@code args} ({@link MessageFormat}). */
    public static String get(String key, Object... args) {
        return new MessageFormat(BUNDLE.getString(key), locale()).format(args);
    }

    public static Locale locale() {
        Locale l = BUNDLE.getLocale();
        return l.getLanguage().isEmpty() ? Locale.ENGLISH : l;
    }
}
