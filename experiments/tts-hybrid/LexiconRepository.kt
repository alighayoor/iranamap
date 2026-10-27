package com.iranamap.real.tts

import android.content.Context
import android.util.Log
import java.io.File

/**
 * مخزن لکسیکون صوتی از پیش ضبط‌شده (Concatenative TTS).
 * اولویت: 1) فایل صوتی آماده آناهیتا/آرش  2) fallback به Sherpa TTS لایو
 *
 * ساختار پوشه:
 *   voices/anahita/base/*.mp3         -> 51 عبارت ثابت (به راست بپیچید ...)
 *   voices/anahita/shiraz/*.mp3        -> نام خیابان‌ها (بلوار چمران ...)
 *   voices/arash/base/*.mp3            -> همین برای آرش
 */
class LexiconRepository(private val context: Context) {

    private val tag = "LexiconRepo"

    // نگاشت base phrase id -> نام فایل
    private fun basePath(voiceId: String, phraseId: String): String =
        "voices/$voiceId/base/$phraseId.mp3"

    private fun streetPath(voiceId: String, city: String, index: String): String =
        "voices/$voiceId/$city/$index.mp3"

    /** آیا فایل base برای این صدا موجود است؟ (assets یا filesDir) */
    fun hasBasePhrase(voiceId: String, phraseId: String): Boolean {
        return existsInAssets(basePath(voiceId, phraseId)) || existsInFiles(basePath(voiceId, phraseId))
    }

    /** آیا فایل خیابان موجود است؟ */
    fun hasStreet(voiceId: String, city: String, index: String): Boolean {
        return existsInAssets(streetPath(voiceId, city, index)) || existsInFiles(streetPath(voiceId, city, index))
    }

    private fun existsInAssets(assetPath: String): Boolean = try {
        context.assets.openFd(assetPath).use { true }
    } catch (_: Exception) { false }

    private fun existsInFiles(relative: String): Boolean =
        File(context.filesDir, relative).exists()

    /** مسیر فایل قابل پخش (assets -> copy to cache, یا filesDir) */
    fun resolveBase(voiceId: String, phraseId: String): File? {
        val rel = basePath(voiceId, phraseId)
        val inFiles = File(context.filesDir, rel)
        if (inFiles.exists()) return inFiles
        // از assets کپی به cache برای پخش با MediaPlayer
        return try {
            context.assets.open(rel).use { input ->
                val tmp = File(context.cacheDir, "lexicon_${voiceId}_$phraseId.mp3").apply { parentFile?.mkdirs() }
                tmp.outputStream().use { output -> input.copyTo(output) }
                tmp
            }
        } catch (e: Exception) {
            Log.d(tag, "base not found: $rel")
            null
        }
    }

    fun resolveStreet(voiceId: String, city: String, index: String): File? {
        val rel = streetPath(voiceId, city, index)
        val inFiles = File(context.filesDir, rel)
        if (inFiles.exists()) return inFiles
        return try {
            context.assets.open(rel).use { input ->
                val tmp = File(context.cacheDir, "lexicon_${voiceId}_${city}_$index.mp3").apply { parentFile?.mkdirs() }
                tmp.outputStream().use { output -> input.copyTo(output) }
                tmp
            }
        } catch (e: Exception) {
            Log.d(tag, "street not found: $rel")
            null
        }
    }

    /** لیست base phrases که برای این صدا آماده‌اند */
    fun availableBaseFor(voiceId: String): List<String> {
        val fromAssets = try {
            context.assets.list("voices/$voiceId/base")?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val fromFiles = File(context.filesDir, "voices/$voiceId/base").listFiles()?.map { it.name } ?: emptyList()
        return (fromAssets + fromFiles).map { it.removeSuffix(".mp3") }.distinct().sorted()
    }

    companion object {
        // 51 base phrase ids - مطابق base_phrases.json
        val BASE_IDS = listOf(
            "route_started","route_started_to","route_distance_is","route_time_is","route_lights_info",
            "maneuver_depart","maneuver_continue","maneuver_straight","maneuver_turn_left","maneuver_turn_right",
            "maneuver_slight_left","maneuver_slight_right","maneuver_sharp_left","maneuver_sharp_right",
            "maneuver_uturn","maneuver_roundabout_enter","maneuver_roundabout_exit_generic","maneuver_merge","maneuver_ramp","maneuver_exit",
            "maneuver_fork_left","maneuver_fork_right","maneuver_in_prefix","maneuver_now","maneuver_enter_street",
            "arrival_generic","arrival_to","off_route","rerouted","off_route_far","destination_set","destination_set_to",
            "navigation_stopped","gps_lost","speed_warning_generic","camera_ahead","speed_limit_is",
            "light_red_ahead","light_green_ahead","light_yellow_ahead","light_red_wait","light_remaining","green_wave",
            "traffic_ahead","faster_route","remaining_summary","meters_unit","kilometers_unit","seconds_unit","minutes_unit","hours_unit"
        )
    }
}
