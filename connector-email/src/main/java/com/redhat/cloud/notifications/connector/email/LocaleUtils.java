package com.redhat.cloud.notifications.connector.email;

import java.util.Locale;

public final class LocaleUtils {

    /**
     * Default locale used when a user has no locale, or an unparseable one.
     * Matches the default (English) message bundles.
     */
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private LocaleUtils() {
    }

    /**
     * Converts a locale code coming from the IT service (e.g. {@code en_US}, {@code fr_FR}) into a {@link Locale}.
     * Returns {@link #DEFAULT_LOCALE} when the code is null, blank or cannot be parsed into a language.
     */
    public static Locale toLocale(final String localeCode) {
        if (localeCode == null || localeCode.isBlank()) {
            return DEFAULT_LOCALE;
        }
        // IT service uses the underscore separator (en_US); Locale.forLanguageTag expects a hyphen (en-US).
        Locale locale = Locale.forLanguageTag(localeCode.trim().replace('_', '-'));
        if (locale.getLanguage().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        return locale;
    }
}
