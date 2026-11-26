package com.redhat.cloud.notifications.connector.email;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static com.redhat.cloud.notifications.connector.email.LocaleUtils.DEFAULT_LOCALE;
import static com.redhat.cloud.notifications.connector.email.LocaleUtils.toLocale;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleUtilsTest {

    @Test
    void testNullReturnsDefault() {
        assertEquals(DEFAULT_LOCALE, toLocale(null));
    }

    @Test
    void testBlankReturnsDefault() {
        assertEquals(DEFAULT_LOCALE, toLocale(""));
        assertEquals(DEFAULT_LOCALE, toLocale("   "));
    }

    @Test
    void testUnderscoreSeparatedLocaleCode() {
        Locale locale = toLocale("fr_FR");
        assertEquals("fr", locale.getLanguage());
        assertEquals("FR", locale.getCountry());
    }

    @Test
    void testEnUsLocaleCode() {
        Locale locale = toLocale("en_US");
        assertEquals("en", locale.getLanguage());
        assertEquals("US", locale.getCountry());
    }

    @Test
    void testLanguageOnlyLocaleCode() {
        Locale locale = toLocale("fr");
        assertEquals("fr", locale.getLanguage());
        assertEquals("", locale.getCountry());
    }

    @Test
    void testUnparseableLocaleCodeReturnsDefault() {
        // A value with no resolvable language falls back to the default locale.
        assertEquals(DEFAULT_LOCALE, toLocale("_"));
    }

    @Test
    void testValueIsTrimmed() {
        Locale locale = toLocale("  fr_FR  ");
        assertEquals("fr", locale.getLanguage());
        assertEquals("FR", locale.getCountry());
    }
}
