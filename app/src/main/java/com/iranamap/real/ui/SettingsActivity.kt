package com.iranamap.real.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.iranamap.real.BuildConfig
import com.iranamap.real.R
import com.iranamap.real.core.VoiceCatalog
import com.iranamap.real.core.Verbosity
import com.iranamap.real.settings.AppSettings
import com.iranamap.real.settings.MapStyleKind
import com.iranamap.real.settings.SettingsRepository
import com.iranamap.real.util.toast
import kotlinx.coroutines.launch

/** تنظیمات کامل: صدا، اعلام‌ها، نقشه، ناوبری و نقشه‌های آفلاین. */
class SettingsActivity : AppCompatActivity() {

    private val repo by lazy { SettingsRepository.get(this) }

    private lateinit var tvCurrentVoice: TextView
    private lateinit var tvMapStyle: TextView
    private lateinit var tvAbout: TextView
    private lateinit var swStreet: MaterialSwitch
    private lateinit var swLights: MaterialSwitch
    private lateinit var swSpeed: MaterialSwitch
    private lateinit var swArrival: MaterialSwitch
    private lateinit var swTraffic: MaterialSwitch
    private lateinit var sw3d: MaterialSwitch
    private lateinit var swShowLights: MaterialSwitch
    private lateinit var swShowCameras: MaterialSwitch
    private lateinit var swSpeedLimit: MaterialSwitch
    private lateinit var rgVerbosity: RadioGroup
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvCurrentVoice = findViewById(R.id.tvCurrentVoice)
        tvMapStyle = findViewById(R.id.tvMapStyle)
        tvAbout = findViewById(R.id.tvAbout)
        swStreet = findViewById(R.id.swStreetNames)
        swLights = findViewById(R.id.swLights)
        // داده‌ی زنده‌ی چراغ موجود نیست؛ سوئیچ غیرفعال است تا وعده‌ی عملکرد ندهد.
        swLights.isEnabled = false
        swSpeed = findViewById(R.id.swSpeedWarn)
        swArrival = findViewById(R.id.swArrival)
        swTraffic = findViewById(R.id.swTraffic)
        swTraffic.isEnabled = false // سرویس ترافیک زنده و اعلام آن هنوز متصل نیست.
        sw3d = findViewById(R.id.sw3d)
        swShowLights = findViewById(R.id.swShowLights)
        swShowCameras = findViewById(R.id.swShowCameras)
        swSpeedLimit = findViewById(R.id.swSpeedLimit)
        // مسیرهای فعلی منبع قابل اتکای محدودیت سرعت ندارند.
        swSpeedLimit.isEnabled = false
        rgVerbosity = findViewById(R.id.rgVerbosity)

        findViewById<LinearLayout>(R.id.rowVoiceSettings).setOnClickListener {
            startActivity(Intent(this, VoiceSettingsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowOffline).setOnClickListener {
            startActivity(Intent(this, CityDownloadActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.rowMapStyle).setOnClickListener { showStylePicker() }

        swStreet.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setAnnounceStreetNames(v) } }
        swLights.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setAnnounceLights(v) } }
        swSpeed.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setAnnounceSpeed(v) } }
        swArrival.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setAnnounceArrival(v) } }
        swTraffic.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setAnnounceTraffic(v) } }
        sw3d.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.set3d(v) } }
        swShowLights.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setShowTrafficLights(v) } }
        swShowCameras.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setShowCameras(v) } }
        swSpeedLimit.setOnCheckedChangeListener { _, v -> if (!binding) launch { repo.setSpeedLimitAlerts(v) } }

        rgVerbosity.setOnCheckedChangeListener { _, checkedId ->
            if (binding) return@setOnCheckedChangeListener
            val verbosity = when (checkedId) {
                R.id.rbLow -> Verbosity.LOW
                R.id.rbHigh -> Verbosity.HIGH
                else -> Verbosity.NORMAL
            }
            launch { repo.setVerbosity(verbosity) }
        }

        tvAbout.text = getString(R.string.about_text) + "\n\n" +
            getString(R.string.version, BuildConfig.VERSION_NAME)

        lifecycleScope.launch { repo.settings.collect { bind(it) } }
    }

    private fun launch(block: suspend () -> Unit) {
        lifecycleScope.launch { block() }
    }

    private fun bind(s: AppSettings) {
        binding = true
        val voice = VoiceCatalog.find(s.voiceId) ?: VoiceCatalog.bundled
        tvCurrentVoice.text = "${voice.displayName} (${voice.genderFa})"
        swStreet.isChecked = s.announceStreetNames
        swLights.isChecked = false
        swSpeed.isChecked = s.announceSpeed
        swArrival.isChecked = s.announceArrival
        swTraffic.isChecked = false
        sw3d.isChecked = s.is3d
        swShowLights.isChecked = s.showTrafficLights
        swShowCameras.isChecked = s.showCameras
        swSpeedLimit.isChecked = false
        tvMapStyle.text = when (s.mapStyle) {
            MapStyleKind.DARK -> getString(R.string.night_mode)
            MapStyleKind.LIGHT -> getString(R.string.day_mode)
            MapStyleKind.NEUTRAL -> getString(R.string.style_fiord)
        }
        rgVerbosity.check(
            when (s.verbosity) {
                Verbosity.LOW -> R.id.rbLow
                Verbosity.HIGH -> R.id.rbHigh
                else -> R.id.rbNormal
            }
        )
        binding = false
    }

    private fun showStylePicker() {
        val options = arrayOf(
            getString(R.string.style_liberty),
            getString(R.string.style_dark),
            getString(R.string.style_fiord)
        )
        val values = arrayOf(MapStyleKind.LIGHT, MapStyleKind.DARK, MapStyleKind.NEUTRAL)
        AlertDialog.Builder(this)
            .setTitle(R.string.map_style)
            .setItems(options) { _, which ->
                launch { repo.setMapStyle(values[which]) }
                toast(options[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
