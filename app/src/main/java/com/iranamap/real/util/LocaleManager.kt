package com.iranamap.real.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** برنامه به‌صورت پیش‌فرض فارسی و راست‌به‌چپ اجرا می‌شود. */
object LocaleManager {

    const val LANGUAGE_TAG = "fa-IR"

    fun applyPersian(context: Context) {
        val persian = Locale("fa", "IR")
        Locale.setDefault(persian)
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.isEmpty() || current.toLanguageTags() != LANGUAGE_TAG) {
            try {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(LANGUAGE_TAG))
            } catch (e: Exception) {
                // در برخی نسخه‌های اندروید، تنظیم زبان برنامه در دسترس نیست
            }
        }
    }
}
