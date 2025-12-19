/*
 * FTX1-Hamlib - Internationalization Support
 * Copyright (c) 2025 by Terrell Deppe (KJ5HST)
 */
package com.yaesu.hamlib.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Internationalization (i18n) support for FTX1-Hamlib.
 * Provides localized strings based on system locale or user preference.
 */
public class Messages {

    private static final String BUNDLE_NAME = "messages";
    private static final String PREF_LOCALE = "locale";

    private static ResourceBundle bundle;
    private static Locale currentLocale;

    static {
        // Load saved locale preference or use system default
        loadLocale();
    }

    /**
     * Gets a localized string for the given key.
     * @param key the message key
     * @return the localized string, or the key if not found
     */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Gets a localized string with parameter substitution.
     * @param key the message key
     * @param args arguments to substitute into the message
     * @return the formatted localized string
     */
    public static String get(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Gets the current locale.
     * @return the current locale
     */
    public static Locale getLocale() {
        return currentLocale;
    }

    /**
     * Sets the locale and reloads the resource bundle.
     * @param locale the new locale
     */
    public static void setLocale(Locale locale) {
        currentLocale = locale;
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);

        // Save preference
        Preferences prefs = Preferences.userNodeForPackage(Messages.class);
        prefs.put(PREF_LOCALE, locale.toLanguageTag());
    }

    /**
     * Gets available locales for which translations exist.
     * @return array of available locales
     */
    private static final Locale SPANISH = new Locale("es");
    private static final Locale HEBREW = new Locale("he");
    private static final Locale RUSSIAN = new Locale("ru");
    private static final Locale ARABIC = new Locale("ar");

    public static Locale[] getAvailableLocales() {
        // Sorted by amateur radio activity/popularity
        return new Locale[] {
            Locale.ENGLISH,    // USA, UK, Australia, etc. - largest ham population
            Locale.JAPANESE,   // Japan - 2nd largest ham population
            Locale.GERMAN,     // Germany - large European ham community
            RUSSIAN,           // Russia - significant ham population
            SPANISH,           // Spain, Latin America
            Locale.ITALIAN,    // Italy
            Locale.FRENCH,     // France
            ARABIC,            // Middle East/North Africa
            HEBREW,            // Israel
        };
    }

    /**
     * Loads the locale from preferences or system default.
     */
    private static void loadLocale() {
        Preferences prefs = Preferences.userNodeForPackage(Messages.class);
        String savedLocale = prefs.get(PREF_LOCALE, null);

        if (savedLocale != null) {
            currentLocale = Locale.forLanguageTag(savedLocale);
        } else {
            currentLocale = Locale.getDefault();
        }

        bundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
    }

    /**
     * Checks if using system default locale (no saved preference).
     * @return true if using system default
     */
    public static boolean isUsingDefault() {
        Preferences prefs = Preferences.userNodeForPackage(Messages.class);
        return prefs.get(PREF_LOCALE, null) == null;
    }

    /**
     * Resets to system default locale.
     */
    public static void resetToDefault() {
        Preferences prefs = Preferences.userNodeForPackage(Messages.class);
        prefs.remove(PREF_LOCALE);
        currentLocale = Locale.getDefault();
        bundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
    }
}
