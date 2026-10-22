package com.iranamap.real.tts

/** موتور گفتار مستقل از TTS سیستم اندروید. */
interface SpeechEngine {

    val isReady: Boolean

    /** بارگذاری یک صدا از مسیر پوشه‌ی نصب‌شده. */
    fun loadVoice(modelDir: java.io.File): Boolean

    fun setSpeed(speed: Float)

    fun setVolume(volume: Float)

    /** گفتن متن با اولویت بالاتر = قطع‌کردن گفتار کم‌اولویت‌تر. */
    fun speak(text: String, priority: Int = 50, utteranceId: String = "")

    fun stop()

    fun release()
}

/** موتور جایگزین زمانی که موتور آفلاین در دسترس نیست (برنامه هرگز کرش نمی‌کند). */
class SilentSpeechEngine : SpeechEngine {
    override val isReady: Boolean get() = false
    override fun loadVoice(modelDir: java.io.File): Boolean = false
    override fun setSpeed(speed: Float) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun speak(text: String, priority: Int, utteranceId: String) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
