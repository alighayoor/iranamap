package com.iranamap.real.ui

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.iranamap.real.R
import com.iranamap.real.core.PersianText
import com.iranamap.real.core.VoiceCatalog
import com.iranamap.real.core.VoiceModel
import com.iranamap.real.settings.SettingsRepository
import com.iranamap.real.tts.HybridSpeechEngine
import com.iranamap.real.tts.LexiconRepository
import com.iranamap.real.tts.SherpaSpeechEngine
import com.iranamap.real.tts.VoiceInstaller
import com.iranamap.real.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** مدیریت گوینده‌های فارسی آفلاین: دانلود، انتخاب و پیش‌نمایش. */
class VoiceSettingsActivity : AppCompatActivity() {

    private val repo by lazy { SettingsRepository.get(this) }
    private val sherpa by lazy { SherpaSpeechEngine(this) }
    private val lexicon by lazy { LexiconRepository(this) }
    private val engine by lazy { HybridSpeechEngine(this, sherpa, lexicon) }
    private lateinit var adapter: VoiceAdapter
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekVolume: SeekBar
    private var selectedVoiceId: String = "amir"
    private var bindingSliders = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice)

        seekSpeed = findViewById(R.id.seekSpeed)
        seekVolume = findViewById(R.id.seekVolume)
        val rv: RecyclerView = findViewById(R.id.rvVoices)
        adapter = VoiceAdapter(
            onAction = { model -> downloadVoice(model) },
            onPreview = { model -> previewVoice(model) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<MaterialButton>(R.id.btnTestVoice).setOnClickListener {
            engine.speak(getString(R.string.voice_test_phrase), 90, "test")
        }

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (bindingSliders || !fromUser) return
                val value = 0.6f + (progress / 100f) * 0.9f
                engine.setSpeed(value)
                lifecycleScope.launch { repo.setSpeechRate(value) }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (bindingSliders || !fromUser) return
                val value = (progress / 100f).coerceIn(0.1f, 1f)
                engine.setVolume(value)
                lifecycleScope.launch { repo.setSpeechVolume(value) }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        lifecycleScope.launch {
            val settings = repo.current()
            selectedVoiceId = settings.voiceId
            bindingSliders = true
            seekSpeed.progress = (((settings.speechRate - 0.6f) / 0.9f) * 100).toInt().coerceIn(0, 100)
            seekVolume.progress = (settings.speechVolume * 100).toInt().coerceIn(0, 100)
            bindingSliders = false
            engine.setSpeed(settings.speechRate)
            engine.setVolume(settings.speechVolume)
            loadInstalledVoice(settings.voiceId)
            refreshList()
        }
    }

    private suspend fun loadInstalledVoice(voiceId: String) {
        (engine as? HybridSpeechEngine)?.setVoice(voiceId)
        if (voiceId in setOf("arash", "anahita")) {
            val count = lexicon.availableBaseFor(voiceId).size
            if (count > 0) {
                withContext(Dispatchers.IO) { engine.loadVoice(java.io.File("")) }
                return
            }
            // fallback به امیر برای جملات داینامیک
            val fb = VoiceCatalog.bundled
            val dir = VoiceInstaller.ensureInstalled(fb).getOrNull() ?: return
            withContext(Dispatchers.IO) { sherpa.loadVoice(dir); engine.loadVoice(dir) }
            return
        }
        val model = VoiceCatalog.find(voiceId) ?: VoiceCatalog.bundled
        val dir = VoiceInstaller.ensureInstalled(model).getOrNull() ?: return
        withContext(Dispatchers.IO) { engine.loadVoice(dir) }
    }

    private fun refreshList() {
        val rows = VoiceCatalog.all.map { model ->
            val installed = when (model.id) {
                "arash", "anahita" -> lexicon.availableBaseFor(model.id).isNotEmpty()
                else -> VoiceInstaller.isInstalled(model)
            }
            VoiceRow(
                model = model,
                installed = installed,
                selected = model.id == selectedVoiceId
            )
        }
        adapter.submit(rows)
    }

    private fun downloadVoice(model: VoiceModel) {
        lifecycleScope.launch {
            toast(getString(R.string.downloading))
            val result = VoiceInstaller.download(model) { progress ->
                runOnUiThread { adapter.updateProgress(model.id, (progress * 100).toInt()) }
            }
            result.onSuccess {
                adapter.markInstalled(model.id)
                selectVoice(model)
                loadInstalledVoice(model.id)
                toast(getString(R.string.download_complete))
            }.onFailure {
                adapter.submit(
                    VoiceCatalog.all.map { m ->
                        VoiceRow(m, VoiceInstaller.isInstalled(m), m.id == selectedVoiceId)
                    }
                )
                toast(getString(R.string.download_failed))
            }
        }
    }

    private fun selectVoice(model: VoiceModel) {
        selectedVoiceId = model.id
        lifecycleScope.launch {
            repo.setVoiceId(model.id)
            refreshList()
        }
    }

    private fun previewVoice(model: VoiceModel) {
        lifecycleScope.launch {
            toast("در حال آماده‌سازی صدا...")
            if (model.id in setOf("arash", "anahita")) {
                val count = lexicon.availableBaseFor(model.id).size
                if (count == 0) {
                    toast("${model.displayName} هنوز نصب نشده - ${count} فایل پایه پیدا شد. fallback به امیر")
                }
                (engine as? HybridSpeechEngine)?.setVoice(model.id)
                selectVoice(model)
                val settings = repo.current()
                engine.setSpeed(settings.speechRate)
                engine.setVolume(settings.speechVolume)
                toast("پخش نمونه: ${model.displayName}")
                engine.speak(getString(R.string.voice_test_phrase), 90, "preview_${System.currentTimeMillis()}")
                return@launch
            }
            val isInstalled = VoiceInstaller.isInstalled(model)
            if (!isInstalled) {
                toast("صدا نصب نشده - در حال نصب ${model.displayName}...")
                val res = VoiceInstaller.ensureInstalled(model)
                if (res.isFailure) {
                    toast(getString(R.string.speech_engine_missing) + ": ${res.exceptionOrNull()?.message?.take(40)}")
                    android.util.Log.e("VoiceSettings", "ensureInstalled failed", res.exceptionOrNull())
                    return@launch
                }
            }
            selectVoice(model)
            (engine as? HybridSpeechEngine)?.setVoice(model.id)
            val loaded = withContext(Dispatchers.IO) {
                runCatching { engine.loadVoice(VoiceInstaller.modelDir(model)) }.getOrDefault(false)
            }
            if (!loaded) {
                toast(getString(R.string.speech_engine_missing) + " - بارگذاری ناموفق")
                android.util.Log.e("VoiceSettings", "loadVoice failed for ${model.id}")
                return@launch
            }
            val settings = repo.current()
            engine.setSpeed(settings.speechRate)
            engine.setVolume(settings.speechVolume)
            if (!engine.isReady) {
                toast("موتور صدا آماده نیست - دوباره تلاش کنید")
                return@launch
            }
            toast("پخش نمونه: ${model.displayName}")
            engine.speak(getString(R.string.voice_test_phrase), 90, "preview_${System.currentTimeMillis()}")
        }
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}
