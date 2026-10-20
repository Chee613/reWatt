package com.rewatt.app.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Single source of truth for the app's selected language, backed by AppCompat's per-app locale.
 *
 * AppCompat persists the choice across restarts (via the AppLocalesMetadataHolderService declared
 * in the manifest) and applies it to every AppCompat activity. Because the locale is stored
 * statically by AppCompat, background code (WorkManager, services, repositories) can read it for
 * Gemini prompts without any Context or SharedPreferences plumbing.
 */
object LocaleManager {
    const val ENGLISH = "en"
    const val MALAY = "ms"

    /** Apply a language tag ("en"/"ms") app-wide. Triggers AppCompat activity recreation. */
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Current language tag, defaulting to English when no explicit choice is set. */
    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) ENGLISH else locales[0]?.language ?: ENGLISH
    }

    fun isMalay(): Boolean = currentTag() == MALAY

    /** java.util.Locale for building a locale-wrapped Context (e.g. notification strings). */
    fun currentLocale(): Locale = Locale(currentTag())

    /** Human-readable language name injected into Gemini prompts so it replies in that language. */
    fun promptLanguageName(): String = if (isMalay()) "Bahasa Melayu (Malay)" else "English"
}
