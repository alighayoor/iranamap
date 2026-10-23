package com.iranamap.real.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * تشخیص دستگاه کم‌رم (مثل Redmi 10C) برای فعال‌سازی حالت کم‌مصرف
 * - حالت سه‌بعدی سبک، کاهش لایه‌ها، کاهش مصرف TTS
 */
object DevicePerformance {

    data class Profile(
        val isLowRamDevice: Boolean,
        val totalRamMb: Long,
        val memoryClassMb: Int,
        val shouldUseLiteMode: Boolean
    )

    fun detect(context: Context): Profile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRamFlag = activityManager.isLowRamDevice
        val memClass = activityManager.memoryClass // MB per app
        val largeMemClass = activityManager.largeMemoryClass

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)

        // Redmi 10C: 3-4GB total, but available per app ~192MB
        // منطق: اگر total < 4000MB یا memoryClass < 256 یا isLowRamDevice = true => LiteMode
        val shouldLite = isLowRamFlag || memClass <= 192 || totalMb <= 3500

        return Profile(
            isLowRamDevice = isLowRamFlag,
            totalRamMb = totalMb,
            memoryClassMb = memClass,
            shouldUseLiteMode = shouldLite
        )
    }

    fun logProfile(context: Context): Profile {
        val p = detect(context)
        android.util.Log.i(
            "IranAmapPerf",
            "حالت دستگاه: lowRamFlag=${p.isLowRamDevice} total=${p.totalRamMb}MB memClass=${p.memoryClassMb}MB liteMode=${p.shouldUseLiteMode} model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}"
        )
        return p
    }

    fun isLiteMode(context: Context): Boolean = detect(context).shouldUseLiteMode
}
