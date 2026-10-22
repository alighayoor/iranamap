package com.iranamap.real.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iranamap.real.core.AnnounceOptions
import com.iranamap.real.core.Verbosity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class MapStyleKind { LIGHT, DARK, NEUTRAL }

data class AppSettings(
    val voiceId: String = "amir",
    val speechRate: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val verbosity: Verbosity = Verbosity.NORMAL,
    val announceStreetNames: Boolean = true,
    val announceLights: Boolean = true,
    val announceSpeed: Boolean = true,
    val announceArrival: Boolean = true,
    val announceTraffic: Boolean = true,
    val mapStyle: MapStyleKind = MapStyleKind.DARK,
    val is3d: Boolean = true,
    val showTrafficLights: Boolean = true,
    val showCameras: Boolean = true,
    val speedLimitAlerts: Boolean = true,
    val speedWarningKmh: Int = 100
) {
    fun announceOptions(): AnnounceOptions = AnnounceOptions(
        verbosity = verbosity,
        streetNames = announceStreetNames,
        lights = announceLights,
        speedWarnings = announceSpeed,
        arrival = announceArrival,
        traffic = announceTraffic,
        speedWarningKmh = speedWarningKmh
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iranamap_settings")

class SettingsRepository private constructor(private val context: Context) {

    private object Keys {
        val voiceId = stringPreferencesKey("voice_id")
        val speechRate = floatPreferencesKey("speech_rate")
        val speechVolume = floatPreferencesKey("speech_volume")
        val verbosity = stringPreferencesKey("verbosity")
        val announceStreet = booleanPreferencesKey("announce_street")
        val announceLights = booleanPreferencesKey("announce_lights")
        val announceSpeed = booleanPreferencesKey("announce_speed")
        val announceArrival = booleanPreferencesKey("announce_arrival")
        val announceTraffic = booleanPreferencesKey("announce_traffic")
        val mapStyle = stringPreferencesKey("map_style")
        val is3d = booleanPreferencesKey("is_3d")
        val showLights = booleanPreferencesKey("show_lights")
        val showCameras = booleanPreferencesKey("show_cameras")
        val speedLimitAlerts = booleanPreferencesKey("speed_limit_alerts")
        val speedWarning = intPreferencesKey("speed_warning_kmh")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            voiceId = p[Keys.voiceId] ?: "amir",
            speechRate = p[Keys.speechRate] ?: 1.0f,
            speechVolume = p[Keys.speechVolume] ?: 1.0f,
            verbosity = runCatching { Verbosity.valueOf(p[Keys.verbosity] ?: Verbosity.NORMAL.name) }
                .getOrDefault(Verbosity.NORMAL),
            announceStreetNames = p[Keys.announceStreet] ?: true,
            announceLights = p[Keys.announceLights] ?: true,
            announceSpeed = p[Keys.announceSpeed] ?: true,
            announceArrival = p[Keys.announceArrival] ?: true,
            announceTraffic = p[Keys.announceTraffic] ?: true,
            mapStyle = runCatching { MapStyleKind.valueOf(p[Keys.mapStyle] ?: MapStyleKind.DARK.name) }
                .getOrDefault(MapStyleKind.DARK),
            is3d = p[Keys.is3d] ?: true,
            showTrafficLights = p[Keys.showLights] ?: true,
            showCameras = p[Keys.showCameras] ?: true,
            speedLimitAlerts = p[Keys.speedLimitAlerts] ?: true,
            speedWarningKmh = p[Keys.speedWarning] ?: 100
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setVoiceId(id: String) = edit { it[Keys.voiceId] = id }
    suspend fun setSpeechRate(value: Float) = edit { it[Keys.speechRate] = value.coerceIn(0.5f, 1.6f) }
    suspend fun setSpeechVolume(value: Float) = edit { it[Keys.speechVolume] = value.coerceIn(0.1f, 1f) }
    suspend fun setVerbosity(value: Verbosity) = edit { it[Keys.verbosity] = value.name }
    suspend fun setAnnounceStreetNames(value: Boolean) = edit { it[Keys.announceStreet] = value }
    suspend fun setAnnounceLights(value: Boolean) = edit { it[Keys.announceLights] = value }
    suspend fun setAnnounceSpeed(value: Boolean) = edit { it[Keys.announceSpeed] = value }
    suspend fun setAnnounceArrival(value: Boolean) = edit { it[Keys.announceArrival] = value }
    suspend fun setAnnounceTraffic(value: Boolean) = edit { it[Keys.announceTraffic] = value }
    suspend fun setMapStyle(value: MapStyleKind) = edit { it[Keys.mapStyle] = value.name }
    suspend fun set3d(value: Boolean) = edit { it[Keys.is3d] = value }
    suspend fun setShowTrafficLights(value: Boolean) = edit { it[Keys.showLights] = value }
    suspend fun setShowCameras(value: Boolean) = edit { it[Keys.showCameras] = value }
    suspend fun setSpeedLimitAlerts(value: Boolean) = edit { it[Keys.speedLimitAlerts] = value }
    suspend fun setSpeedWarningKmh(value: Int) = edit { it[Keys.speedWarning] = value.coerceIn(40, 200) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) instance = SettingsRepository(context.applicationContext)
                }
            }
        }

        fun get(context: Context): SettingsRepository {
            init(context)
            return instance!!
        }
    }
}
