package com.iranamap.real.tts

import com.iranamap.real.core.Announcement

/** پخش اعلام‌های صوتی با اولویت‌بندی. */
class Announcer(private val engine: SpeechEngine) {

    fun announce(items: List<Announcement>) {
        if (items.isEmpty()) return
        items.sortedByDescending { it.priority }
            .forEach { engine.speak(it.text, it.priority, it.key) }
    }

    fun say(text: String, priority: Int = 50) {
        engine.speak(text, priority, "manual")
    }

    fun stop() = engine.stop()
}
