package com.iranamap.real

import android.app.Application
import android.content.Context
import android.util.Log
import com.iranamap.real.settings.SettingsRepository
import com.iranamap.real.tts.VoiceInstaller
import com.iranamap.real.util.DevicePerformance
import com.iranamap.real.util.LocaleManager
import org.maplibre.android.MapLibre

class IranAmapApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        LocaleManager.applyPersian(this)
        // MapLibre بدون کلید هم کار می‌کند؛ مقداردهی محافظت‌شده تا هرگز کرش نکند
        runCatching { MapLibre.getInstance(this) }
        SettingsRepository.init(this)
        VoiceInstaller.init(this)
        // تشخیص دستگاه کم‌رم و لاگ برای عیب‌یابی
        val profile = DevicePerformance.logProfile(this)
        if (profile.shouldUseLiteMode) {
            Log.i("IranAmapApp", "حالت کم‌مصرف فعال شد - سه‌بعدی سبک و مصرف رم بهینه")
        }
    }

    companion object {
        lateinit var instance: IranAmapApp
            private set

        val context: Context get() = instance.applicationContext
    }
}
