package com.iranamap.real.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import com.iranamap.real.core.PersianText
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * موتور ترکیبی: اول فایل‌های از پیش ضبط‌شده آناهیتا/آرش، بعد fallback به Sherpa TTS.
 * در تمام قسمت‌های برنامه از همین موتور استفاده می‌شود.
 *
 * استفاده:
 *   val engine = HybridSpeechEngine(context, sherpaEngine, lexiconRepo)
 *   engine.setVoice("anahita") // یا "arash" یا "amir"
 *   engine.speak("به راست بپیچید", priority=85)
 */
class HybridSpeechEngine(
    private val context: Context,
    private val sherpa: SpeechEngine,
    private val lexicon: LexiconRepository
) : SpeechEngine {

    private val tag = "HybridTTS"
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "hybrid-tts") }
    private var voiceId: String = "amir" // anahita | arash | amir | ...
    private var volume: Float = 1.0f
    private var speed: Float = 1.0f
    private var running = true
    private val queue = ConcurrentLinkedQueue<Utterance>()
    @Volatile private var currentPlayer: MediaPlayer? = null
    @Volatile private var speakingPriority = -1

    data class Utterance(val text: String, val priority: Int, val id: String)

    override val isReady: Boolean get() = sherpa.isReady || voiceId in setOf("anahita", "arash")

    init {
        executor.execute { workerLoop() }
    }

    fun setVoice(id: String) {
        voiceId = id.lowercase()
        Log.i(tag, "Voice set to $voiceId")
    }

    override fun loadVoice(modelDir: java.io.File): Boolean {
        // برای آناهیتا/آرش، فایل‌های از پیش ضبط‌شده کافی است - نیازی به مدل onnx نیست
        if (voiceId in setOf("anahita", "arash")) {
            val available = lexicon.availableBaseFor(voiceId)
            Log.i(tag, "Lexicon voice $voiceId: ${available.size} base phrases available (no onnx needed)")
            return true
        }
        return sherpa.loadVoice(modelDir)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 1.6f)
        sherpa.setSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.1f, 1f)
        sherpa.setVolume(volume)
        try { currentPlayer?.setVolume(volume, volume) } catch (_: Exception) {}
    }

    override fun speak(text: String, priority: Int, utteranceId: String) {
        if (text.isBlank()) return
        synchronized(queue) {
            if (priority > speakingPriority) {
                // قطع فعلی برای اولویت بالاتر
                try { currentPlayer?.stop() } catch (_: Exception) {}
                sherpa.stop()
            }
            queue.add(Utterance(text, priority, utteranceId))
            (queue as Object).notifyAll()
        }
    }

    override fun stop() {
        queue.clear()
        try { currentPlayer?.stop() } catch (_: Exception) {}
        sherpa.stop()
    }

    override fun release() {
        running = false
        queue.clear()
        try { currentPlayer?.release() } catch (_: Exception) {}
        sherpa.release()
        executor.shutdown()
    }

    private fun workerLoop() {
        while (running) {
            val ut = synchronized(queue) {
                while (queue.isEmpty() && running) try { (queue as Object).wait(500) } catch (_: InterruptedException) {}
                if (!running) null else queue.poll()?.let { best ->
                    // بالاترین اولویت را بردار
                    val all = mutableListOf(best) + queue.toList()
                    queue.clear()
                    val top = all.maxByOrNull { it.priority }!!
                    // بقیه را برگردان
                    all.filter { it != top }.forEach { queue.add(it) }
                    top
                }
            } ?: continue

            speakingPriority = ut.priority
            val handled = tryPlayLexicon(ut.text)
            if (!handled) {
                sherpa.speak(ut.text, ut.priority, ut.id)
                // صبر تا Sherpa تمام کند (تقریبی: 150ms per word)
                val words = ut.text.split(" ").size
                Thread.sleep((words * 180L + 400L).coerceAtMost(4000L))
            }
            speakingPriority = -1
        }
    }

    /** سعی کن متن را با فایل‌های لکسیکون پخش کنی. اگر دقیقاً match نبود، false برگردان. */
    private fun tryPlayLexicon(text: String): Boolean {
        // نگاشت متن کامل به phraseId (برای عبارت‌های ثابت)
        val normalized = PersianText.normalize(text.trim().trimEnd('.','،'))
        val phraseId = mapTextToPhraseId(normalized) ?: return false
        val file = lexicon.resolveBase(voiceId, phraseId) ?: return false
        return try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .build()
                )
                setVolume(volume, volume)
                prepare()
                start()
            }
            currentPlayer = player
            Log.i(tag, "Lexicon play: $voiceId/$phraseId")
            // صبر تا تمام شود
            while (player.isPlaying && running) Thread.sleep(80)
            player.release()
            currentPlayer = null
            true
        } catch (e: Exception) {
            Log.w(tag, "Lexicon play failed for $phraseId", e)
            try { currentPlayer?.release() } catch (_: Exception) {}
            currentPlayer = null
            false
        }
    }

    /** نگاشت متن VoicePhrases به id فایل base */
    private fun mapTextToPhraseId(normalized: String): String? {
        // تطبیق دقیق برای عبارت‌های ثابت (بدون slot داینامیک)
        return when (normalized) {
            "مسیریابی آغاز شد" -> "route_started"
            "حرکت کنید" -> "maneuver_depart"
            "به مسیر ادامه دهید" -> "maneuver_continue"
            "مستقیم ادامه دهید" -> "maneuver_straight"
            "به چپ بپیچید" -> "maneuver_turn_left"
            "به راست بپیچید" -> "maneuver_turn_right"
            "کمی به چپ متمایل شوید" -> "maneuver_slight_left"
            "کمی به راست متمایل شوید" -> "maneuver_slight_right"
            "به‌شدت به چپ بپیچید" -> "maneuver_sharp_left"
            "به‌شدت به راست بپیچید" -> "maneuver_sharp_right"
            "دور بزنید و برگردید" -> "maneuver_uturn"
            "وارد میدان شوید" -> "maneuver_roundabout_enter"
            "با مسیر اصلی ادغام شوید" -> "maneuver_merge"
            "وارد بزرگراه شوید" -> "maneuver_ramp"
            "از بزرگراه خارج شوید" -> "maneuver_exit"
            "از شاخه‌ی چپ ادامه دهید" -> "maneuver_fork_left"
            "از شاخه‌ی راست ادامه دهید" -> "maneuver_fork_right"
            "به مقصد رسیدید. سفر خوبی داشته باشید" -> "arrival_generic"
            "از مسیر خارج شدید. در حال محاسبه‌ی مسیر جدید هستم" -> "off_route"
            "مسیر جدید محاسبه شد. مسیریابی ادامه دارد" -> "rerouted"
            "مقصد انتخاب شد" -> "destination_set"
            "مسیریابی پایان یافت" -> "navigation_stopped"
            "سیگنال موقعیت‌یاب ضعیف است. لطفاً مسیر را با احتیاط ادامه دهید" -> "gps_lost"
            "سرعت شما بیش از حد مجاز است. لطفاً آرام‌تر برانید" -> "speed_warning_generic"
            else -> {
                // برای جملات دارای فاصله/زمان داینامیک، فعلاً fallback به Sherpa (در فاز بعدی با قطعه‌قطعه کردن)
                null
            }
        }
    }
}
