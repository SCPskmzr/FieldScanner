package com.fieldscanner.app.voice.remote

import android.content.Context
import com.fieldscanner.app.voice.AppLanguage
import com.fieldscanner.app.voice.LanguagePreference

/** Persists the selected language code in a small private `SharedPreferences` file. */
class SharedPrefsLanguagePreference(context: Context) : LanguagePreference {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun current(): AppLanguage =
        AppLanguage.fromCode(prefs.getString(KEY_LANGUAGE, null)) ?: AppLanguage.DEFAULT

    override fun update(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    private companion object {
        const val PREFS_NAME = "field_scanner_prefs"
        const val KEY_LANGUAGE = "app_language"
    }
}
