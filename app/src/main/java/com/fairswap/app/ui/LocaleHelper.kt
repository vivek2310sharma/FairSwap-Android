package com.fairswap.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "fairswap_locale_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    val SUPPORTED_LANGUAGES = listOf("en", "hi")

    fun isSupportedLanguage(languageCode: String): Boolean {
        return SUPPORTED_LANGUAGES.contains(languageCode.lowercase().trim())
    }

    fun normalizeLanguage(languageCode: String?): String {
        val normalized = languageCode?.lowercase()?.trim()
        return if (SUPPORTED_LANGUAGES.contains(normalized)) normalized!! else "en"
    }

    fun getSelectedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LANGUAGE, "en")
        return normalizeLanguage(stored)
    }

    fun setSelectedLanguage(context: Context, languageCode: String) {
        val validCode = normalizeLanguage(languageCode)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, validCode).apply()

        val appLocales = LocaleListCompat.forLanguageTags(validCode)
        AppCompatDelegate.setApplicationLocales(appLocales)
    }

    fun applyLocale(context: Context, languageCode: String = getSelectedLanguage(context)): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        val configContext = context.createConfigurationContext(config)

        return object : android.content.ContextWrapper(context) {
            override fun getResources(): android.content.res.Resources {
                return configContext.resources
            }
        }
    }
}
