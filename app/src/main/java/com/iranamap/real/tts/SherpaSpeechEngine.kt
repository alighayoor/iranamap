package com.iranamap.real.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.iranamap.real.core.PersianText
import com.iranamap.real.core.VoiceModel
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors

/**
 * موتور گفتار کاملاً آفلاین بر پایه‌ی sherpa-onnx و مدل‌های فارسی Piper.
 * هیچ داده‌ای به سرور ارسال نمی‌شود و به TTS اندروید وابسته نیست.
 */
class SherpaSpeechEngine(private val context: Context) : SpeechEngine {

    private data class Utterance(val text: String, val priority: Int, val id: String)

    private val logTag = "IranAmapTTS"
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "iranamap-tts") }
    private val lock = Object()
    private val pending = ArrayDeque<Utterance>()

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var stopRequested = false
    @Volatile private var speakingPriority = -1
    @Volatile private var running = true

    private var speed: Float = 1.0f
    private var volume: Float = 1.0f

    override val isReady: Boolean get() = tts != null

    init {
        executor.execute { workerLoop() }
    }

    @Synchronized
    override fun loadVoice(modelDir: File): Boolean {
        val model = VoiceInstaller.modelFileFor(modelDir) ?: run {
            Log.w(logTag, "مدل صوتی در ${modelDir.absolutePath} پیدا نشد")
            return false
        }
        val tokens = File(modelDir, "tokens.txt")
        val dataDir = File(modelDir, "espeak-ng-data")
        if (!model.exists() || !tokens.exists()) {
            Log.w(logTag, "فایل‌های مدل ناقص هستند: ${modelDir.absolutePath}")
            return false
        }
        releaseTts()
        // حالت کم‌مصرف: نخ کمتر برای Redmi 10C
        val isLite = try { com.iranamap.real.util.DevicePerformance.isLiteMode(context) } catch (_: Exception) { false }
        return try {
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = tokens.absolutePath,
                        dataDir = dataDir.absolutePath,
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 1.0f
                    ),
                    numThreads = if (isLite) 1 else 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxNumSentences = 1,
                silenceScale = 0.2f
            )
            val engine = OfflineTts(assetManager = null, config = config)
            tts = engine
            createTrack(engine.sampleRate())
            Log.i(logTag, "موتور صدا آماده شد: ${model.name} (${engine.sampleRate()}Hz)")
            true
        } catch (t: Throwable) {
            Log.e(logTag, "راه‌اندازی موتور صدا ناموفق بود", t)
            tts = null
            false
        }
    }

    private fun releaseTts() {
        try {
            track?.let {
                it.pause()
                it.flush()
                it.release()
            }
        } catch (e: Exception) {
            Log.w(logTag, "خطا در آزادسازی AudioTrack", e)
        }
        track = null
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.w(logTag, "خطا در آزادسازی موتور", e)
        }
        tts = null
    }

    private fun createTrack(sampleRate: Int) {
        // فیکس Redmi 10C: بعضی دستگاه‌ها PCM_FLOAT پشتیبانی نمی‌کنند -> fallback به PCM_16BIT
        val encodings = listOf(
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_16BIT
        )
        var created: AudioTrack? = null
        var lastError: Exception? = null
        for (encoding in encodings) {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    encoding
                ).coerceAtLeast(sampleRate)
                if (minBuf <= 0) continue
                val attributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setSampleRate(sampleRate)
                    .build()
                val t = AudioTrack(
                    attributes,
                    format,
                    minBuf * 2,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                if (t.state == AudioTrack.STATE_INITIALIZED) {
                    t.setVolume(volume)
                    runCatching { t.play() }
                    created = t
                    Log.i(logTag, "AudioTrack ساخته شد با encoding=${if (encoding == AudioFormat.ENCODING_PCM_FLOAT) "FLOAT" else "16BIT"} sampleRate=$sampleRate")
                    break
                } else {
                    runCatching { t.release() }
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(logTag, "ساخت AudioTrack با encoding $encoding ناموفق", e)
            }
        }
        if (created == null) {
            Log.e(logTag, "هیچ AudioTrackای ساخته نشد", lastError)
            throw lastError ?: IllegalStateException("AudioTrack init failed")
        }
        track = created
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.5f, 1.6f)
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.1f, 1f)
        try {
            track?.setVolume(this.volume)
        } catch (e: Exception) {
            Log.w(logTag, "تنظیم بلندی صدا ناموفق بود", e)
        }
    }

    override fun speak(text: String, priority: Int, utteranceId: String) {
        if (text.isBlank()) return
        if (tts == null) {
            Log.w(logTag, "موتور صدا آماده نیست؛ متن نادیده گرفته شد")
            return
        }
        synchronized(lock) {
            if (priority > speakingPriority) {
                // گفتار کم‌اهمیت‌تر قطع می‌شود تا هدایت مهم فوراً شنیده شود
                stopRequested = true
            }
            pending.addLast(Utterance(text, priority, utteranceId))
            lock.notifyAll()
        }
    }

    override fun stop() {
        synchronized(lock) {
            pending.clear()
            stopRequested = true
        }
    }

    override fun release() {
        running = false
        synchronized(lock) {
            pending.clear()
            stopRequested = true
            lock.notifyAll()
        }
        executor.shutdown()
        releaseTts()
    }

    private fun nextUtterance(): Utterance? {
        synchronized(lock) {
            while (running && pending.isEmpty()) {
                try {
                    lock.wait(500)
                } catch (e: InterruptedException) {
                    return null
                }
            }
            if (!running) return null
            // بالاترین اولویت اول پخش می‌شود
            val best = pending.maxByOrNull { it.priority } ?: return null
            pending.remove(best)
            return best
        }
    }

    private fun workerLoop() {
        while (running) {
            val utterance = nextUtterance() ?: continue
            val engine = tts ?: continue
            val output = track ?: continue
            stopRequested = false
            speakingPriority = utterance.priority
            val speechText = PersianText.forSpeech(utterance.text)
            try {
                if (output.playState != AudioTrack.PLAYSTATE_PLAYING) output.play()
                engine.generateWithCallback(
                    text = speechText,
                    sid = 0,
                    speed = speed,
                    callback = { samples: FloatArray ->
                        if (stopRequested || !running) {
                            0
                        } else {
                            output.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                            1
                        }
                    }
                )
            } catch (t: Throwable) {
                Log.e(logTag, "خطا در تولید گفتار", t)
            } finally {
                speakingPriority = -1
                try {
                    output.stop()
                    output.flush()
                    output.play()
                } catch (e: Exception) {
                    Log.w(logTag, "بازنشانی AudioTrack ناموفق بود", e)
                }
            }
        }
    }

    companion object {
        fun modelDirName(model: VoiceModel): String = model.folder
    }
}
