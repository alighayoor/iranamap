package com.iranamap.real.core

import kotlin.math.abs

enum class Verbosity { LOW, NORMAL, HIGH }

data class AnnounceOptions(
    val verbosity: Verbosity = Verbosity.NORMAL,
    val streetNames: Boolean = true,
    val lights: Boolean = true,
    val speedWarnings: Boolean = true,
    val arrival: Boolean = true,
    val traffic: Boolean = true,
    val lightWarnDistanceMeters: Double = 260.0,
    val speedWarningKmh: Int = 100,
    val lightCooldownMillis: Long = 25_000L,
    val summaryEveryMeters: Double = 5000.0
)

enum class AnnounceKind {
    ROUTE_START, MANEUVER_FAR, MANEUVER_MID, MANEUVER_NEAR, MANEUVER_NOW,
    ARRIVE, OFF_ROUTE, REROUTED, LIGHT_RED, LIGHT_YELLOW, LIGHT_GREEN,
    SPEED_WARNING, GREEN_WAVE, DESTINATION_SET, SUMMARY, STOPPED
}

data class Announcement(
    val kind: AnnounceKind,
    val text: String,
    val priority: Int,
    val key: String
)

data class RouteBrief(
    val destinationName: String?,
    val totalMeters: Double,
    val totalSeconds: Long,
    val lightCount: Int,
    val firstManeuver: ManeuverInfo?
)

data class NavState(
    val stepIndex: Int,
    val maneuver: ManeuverKind,
    val streetName: String?,
    val exitNumber: Int?,
    val distanceToManeuverMeters: Double,
    val stepLengthMeters: Double,
    val speedKmh: Double,
    val remainingMeters: Double,
    val remainingSeconds: Long,
    val offRoute: Boolean,
    val arrived: Boolean,
    val secondsOfDay: Long,
    val nowMillis: Long
)

data class LightAhead(val id: Long, val distanceMeters: Double, val forecast: LightForecast)

/**
 * سیاست اعلام صوتی: چه چیزی، کِی و با چه اولویتی گفته شود.
 * همه‌ی ورودی‌ها تزریق‌شدنی هستند تا رفتار آن با تست بررسی شود.
 */
class SpeechPlanner(private var options: AnnounceOptions = AnnounceOptions()) {

    private val spokenKeys = HashSet<String>()
    private val lightSpokenAt = HashMap<Long, Long>()
    private var lastSpeedWarningMillis = 0L
    private var lastSummaryAtMeters = Double.MAX_VALUE
    private var offRouteAnnounced = false
    private var arrivedAnnounced = false
    private val cameraSpokenAt = HashMap<Long, Long>()
    private var lastSpeedLimitWarnAt = -1_000_000L

    fun updateOptions(newOptions: AnnounceOptions) {
        options = newOptions
    }

    fun reset() {
        spokenKeys.clear()
        lightSpokenAt.clear()
        lastSpeedWarningMillis = 0L
        lastSummaryAtMeters = Double.MAX_VALUE
        offRouteAnnounced = false
        arrivedAnnounced = false
        cameraSpokenAt.clear()
        lastSpeedLimitWarnAt = -1_000_000L
    }

    private fun thresholds(): Triple<Double, Double, Double> = when (options.verbosity) {
        Verbosity.LOW -> Triple(0.0, 0.0, 150.0)
        Verbosity.NORMAL -> Triple(0.0, 400.0, 180.0)
        Verbosity.HIGH -> Triple(900.0, 400.0, 180.0)
    }

    private fun speakableKey(kind: AnnounceKind, stepIndex: Int, suffix: String): String = "$kind:$stepIndex:$suffix"

    private fun emit(kind: AnnounceKind, text: String, priority: Int, key: String, out: MutableList<Announcement>) {
        if (spokenKeys.add(key)) out.add(Announcement(kind, text, priority, key))
    }

    fun onRouteStart(brief: RouteBrief): List<Announcement> {
        val out = ArrayList<Announcement>()
        if (options.verbosity != Verbosity.LOW) {
            emit(
                AnnounceKind.ROUTE_START,
                VoicePhrases.routeStarted(brief.destinationName, brief.totalMeters, brief.totalSeconds, brief.lightCount),
                90, "start", out
            )
            lastSummaryAtMeters = brief.totalMeters
        }
        val first = brief.firstManeuver
        if (first != null && first.kind != ManeuverKind.DEPART) {
            val key = speakableKey(AnnounceKind.MANEUVER_MID, 1, "start")
            val text = VoicePhrases.maneuver(first.kind, first.streetName, first.exitNumber, first.distanceMeters, first.distanceMeters < 40)
            emit(AnnounceKind.MANEUVER_MID, text, 80, key, out)
        }
        return out
    }

    fun onNavigation(state: NavState): List<Announcement> {
        val out = ArrayList<Announcement>()
        val (far, mid, near) = thresholds()

        if (state.arrived) {
            if (!arrivedAnnounced && options.arrival) {
                arrivedAnnounced = true
                out.add(Announcement(AnnounceKind.ARRIVE, VoicePhrases.arrival(state.streetName), 100, "arrive"))
            }
            return out
        }

        if (state.offRoute) {
            if (!offRouteAnnounced) {
                offRouteAnnounced = true
                out.add(Announcement(AnnounceKind.OFF_ROUTE, VoicePhrases.offRoute(), 95, "offroute"))
            }
            return out
        }
        offRouteAnnounced = false

        val d = state.distanceToManeuverMeters
        val idx = state.stepIndex
        val longEnough = state.stepLengthMeters >= 90.0

        if (far > 0 && longEnough && d in far * 0.75..far * 1.15) {
            emit(
                AnnounceKind.MANEUVER_FAR,
                VoicePhrases.maneuver(state.maneuver, if (options.streetNames) state.streetName else null, state.exitNumber, d, false),
                60, speakableKey(AnnounceKind.MANEUVER_FAR, idx, "far"), out
            )
        }
        if (mid > 0 && longEnough && d in mid * 0.6..mid * 1.2) {
            emit(
                AnnounceKind.MANEUVER_MID,
                VoicePhrases.maneuver(state.maneuver, if (options.streetNames) state.streetName else null, state.exitNumber, d, false),
                70, speakableKey(AnnounceKind.MANEUVER_MID, idx, "mid"), out
            )
        }
        if (near > 0 && d in 0.0..near * 1.15) {
            emit(
                AnnounceKind.MANEUVER_NEAR,
                VoicePhrases.maneuver(state.maneuver, if (options.streetNames) state.streetName else null, state.exitNumber, d, false),
                80, speakableKey(AnnounceKind.MANEUVER_NEAR, idx, "near"), out
            )
        }
        if (d in 0.0..30.0) {
            emit(
                AnnounceKind.MANEUVER_NOW,
                VoicePhrases.maneuver(state.maneuver, if (options.streetNames) state.streetName else null, state.exitNumber, d, true),
                85, speakableKey(AnnounceKind.MANEUVER_NOW, idx, "now"), out
            )
        }

        if (options.summaryEveryMeters > 0 && options.verbosity == Verbosity.HIGH) {
            if (lastSummaryAtMeters == Double.MAX_VALUE) lastSummaryAtMeters = state.remainingMeters
            if (lastSummaryAtMeters - state.remainingMeters >= options.summaryEveryMeters && state.remainingMeters > 400) {
                lastSummaryAtMeters = state.remainingMeters
                val clock = PersianText.clockString(
                    System.currentTimeMillis() + state.remainingSeconds * 1000L
                )
                out.add(
                    Announcement(
                        AnnounceKind.SUMMARY,
                        VoicePhrases.remainingSummary(state.remainingMeters, state.remainingSeconds, clock),
                        40, "summary:${state.remainingMeters.toInt() / 1000}"
                    )
                )
            }
        }

        if (options.speedWarnings && options.speedWarningKmh in 1..400) {
            if (state.speedKmh > options.speedWarningKmh && state.nowMillis - lastSpeedWarningMillis > 90_000L) {
                lastSpeedWarningMillis = state.nowMillis
                out.add(
                    Announcement(
                        AnnounceKind.SPEED_WARNING,
                        VoicePhrases.speedWarning(state.speedKmh.toInt()),
                        50, "speed:${state.nowMillis / 90_000L}"
                    )
                )
            }
        }
        return out
    }

    fun onRerouted(distanceMeters: Double): List<Announcement> {
        spokenKeys.removeIf { it.startsWith(AnnounceKind.MANEUVER_FAR.name) || it.startsWith(AnnounceKind.MANEUVER_MID.name) || it.startsWith(AnnounceKind.MANEUVER_NEAR.name) || it.startsWith(AnnounceKind.MANEUVER_NOW.name) }
        val text = if (distanceMeters > 0) VoicePhrases.offRouteFar(distanceMeters) else VoicePhrases.rerouted()
        return listOf(Announcement(AnnounceKind.REROUTED, text, 95, "rerouted:${System.currentTimeMillis() / 1000}"))
    }

    fun onDestinationSet(name: String?): List<Announcement> =
        listOf(Announcement(AnnounceKind.DESTINATION_SET, VoicePhrases.destinationSet(name), 40, "destset"))

    /**
     * اعلام چراغ‌های راهنمایی جلوتر. نزدیک‌ترین چراغ در محدوده‌ی هشدار انتخاب می‌شود،
     * برای هر چراغ حداقل یک‌بار در هر دوره‌ی خنک‌شدن اعلام می‌شود.
     */
    fun onLights(ahead: List<LightAhead>, speedKmh: Double, nowMillis: Long, nowSecondsOfDay: Long): List<Announcement> {
        if (!options.lights || ahead.isEmpty()) return emptyList()
        val out = ArrayList<Announcement>()
        val target = ahead.filter { it.distanceMeters in 3.0..options.lightWarnDistanceMeters }.minByOrNull { it.distanceMeters }
            ?: return emptyList()
        val lastAt = lightSpokenAt[target.id] ?: -1_000_000L
        if (nowMillis - lastAt < options.lightCooldownMillis) return emptyList()

        val color = target.forecast.color
        val kind = when (color) {
            LightColor.RED -> AnnounceKind.LIGHT_RED
            LightColor.YELLOW -> AnnounceKind.LIGHT_YELLOW
            LightColor.GREEN -> AnnounceKind.LIGHT_GREEN
        }
        val text = if (target.distanceMeters < 60)
            VoicePhrases.trafficLightImminent(color, target.forecast.remainingSec)
        else VoicePhrases.trafficLight(color, target.forecast.remainingSec, target.distanceMeters)
        lightSpokenAt[target.id] = nowMillis
        out.add(Announcement(kind, text, 65, "light:${target.id}:$nowMillis"))

        if (options.traffic && ahead.size >= 3) {
            val timings = ahead.map { it.forecast.timing to it.distanceMeters }
            val ratio = TrafficLightModel.greenRatioAtSpeed(timings, nowSecondsOfDay, speedKmh)
            if (ratio >= 0.75 && speedKmh > 20) {
                out.add(
                    Announcement(
                        AnnounceKind.GREEN_WAVE,
                        VoicePhrases.greenWave(speedKmh.toInt(), ahead.size),
                        35, "greenwave:${speedKmh.toInt() / 10}:${ahead.size}"
                    )
                )
            }
        }
        return out
    }

    /**
     * اعلام دوربین سرعت و محدودیت مجاز.
     * برای هر دوربین یک‌بار در هر ۶۰ ثانیه و برای عبور از حد مجاز هر ۳۰ ثانیه یک هشدار.
     */
    fun onSpeedAndCameras(
        speedKmh: Double,
        speedLimitKmh: Int?,
        cameraId: Long?,
        cameraDistanceMeters: Double,
        cameraLimitKmh: Int?,
        nowMillis: Long
    ): List<Announcement> {
        if (!options.speedWarnings) return emptyList()
        val out = ArrayList<Announcement>()

        if (cameraId != null) {
            val last = cameraSpokenAt[cameraId] ?: -1_000_000L
            if (nowMillis - last > 60_000L) {
                cameraSpokenAt[cameraId] = nowMillis
                val limit = cameraLimitKmh ?: speedLimitKmh
                out.add(
                    Announcement(
                        AnnounceKind.SPEED_WARNING,
                        SpeedModel.cameraSpeech(cameraDistanceMeters, limit),
                        70, "camera:$cameraId"
                    )
                )
                return out
            }
        }

        val limit = speedLimitKmh ?: return out
        if (SpeedModel.isOverLimit(speedKmh, limit) && nowMillis - lastSpeedLimitWarnAt > 30_000L) {
            lastSpeedLimitWarnAt = nowMillis
            out.add(
                Announcement(
                    AnnounceKind.SPEED_WARNING,
                    VoicePhrases.overSpeedLimit(speedKmh.toInt(), limit),
                    60, "overspeed:${nowMillis / 30_000}"
                )
            )
        }
        return out
    }

    /** متن کارت زمان انتظار چراغ برای نمایش در رابط کاربری. */
    fun lightCardText(distanceMeters: Double, forecast: LightForecast): String =
        "${forecast.color.fa} • ${PersianText.toPersianDigits(forecast.remainingSec.toString())} ثانیه • " +
            PersianText.formatDistance(distanceMeters)
}
