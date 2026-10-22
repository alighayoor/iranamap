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
import com.iranamap.real.tts.SherpaSpeechEngine
import com.iranamap.real.tts.VoiceInstaller
import com.iranamap.real.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** مدیریت گوینده‌های فارسی آفلاین: دانلود، انتخاب و پیش‌نمایش. */
class VoiceSettingsActivity : AppCompatActivity() {

    private val repo by lazy { SettingsRepository.get(this) }
    private val engine by lazy { SherpaSpeechEngine(this) }
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
        val model = VoiceCatalog.find(voiceId) ?: VoiceCatalog.bundled
        val dir = VoiceInstaller.ensureInstalled(model).getOrNull() ?: return
        withContext(Dispatchers.IO) { engine.loadVoice(dir) }
    }

    private fun refreshList() {
        val rows = VoiceCatalog.all.map { model ->
            VoiceRow(
                model = model,
                installed = VoiceInstaller.isInstalled(model),
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
            if (!VoiceInstaller.isInstalled(model)) {
                toast(getString(R.string.speech_engine_missing))
                return@launch
            }
            selectVoice(model)
            loadInstalledVoice(model.id)
            val settings = repo.current()
            engine.setSpeed(settings.speechRate)
            engine.setVolume(settings.speechVolume)
            engine.speak(getString(R.string.voice_test_phrase), 90, "preview")
        }
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}
