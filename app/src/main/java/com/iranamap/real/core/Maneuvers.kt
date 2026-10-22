package com.iranamap.real.core

/** نوع مانور مسیریابی، مستقل از موتور مسیریابی. */
enum class ManeuverKind {
    DEPART,
    ARRIVE,
    CONTINUE,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT,
    MERGE,
    RAMP,
    FORK_LEFT,
    FORK_RIGHT,
    EXIT,
    STRAIGHT,
    UNKNOWN
}

data class ManeuverInfo(
    val kind: ManeuverKind,
    val streetName: String?,
    val exitNumber: Int?,
    val distanceMeters: Double
)

object ManeuverText {

    private val ROAD_PREFIXES = listOf(
        "خیابان", "بلوار", "بزرگراه", "آزادراه", "کوچه", "میدان", "میدانگاه", "جاده",
        "پل", "تونل", "تقاطع", "بازار", "بزرگ‌راه", "بلوار ", "کوی", "شهرک", "پارک", "مجتمع"
    )

    /** تبدیل نوع و modifier خروجی OSRM به مانور معنایی. */
    fun kindOf(type: String?, modifier: String?): ManeuverKind {
        val t = type?.lowercase()?.trim() ?: return ManeuverKind.UNKNOWN
        val m = modifier?.lowercase()?.trim()
        return when (t) {
            "depart" -> ManeuverKind.DEPART
            "arrive" -> ManeuverKind.ARRIVE
            "continue" -> when (m) {
                "uturn" -> ManeuverKind.UTURN
                "left" -> ManeuverKind.TURN_LEFT
                "right" -> ManeuverKind.TURN_RIGHT
                "slight left" -> ManeuverKind.SLIGHT_LEFT
                "slight right" -> ManeuverKind.SLIGHT_RIGHT
                "sharp left" -> ManeuverKind.SHARP_LEFT
                "sharp right" -> ManeuverKind.SHARP_RIGHT
                else -> ManeuverKind.CONTINUE
            }
            "turn", "end of road", "new name", "use lane" -> when (m) {
                "uturn" -> ManeuverKind.UTURN
                "left" -> ManeuverKind.TURN_LEFT
                "right" -> ManeuverKind.TURN_RIGHT
                "slight left" -> ManeuverKind.SLIGHT_LEFT
                "slight right" -> ManeuverKind.SLIGHT_RIGHT
                "sharp left" -> ManeuverKind.SHARP_LEFT
                "sharp right" -> ManeuverKind.SHARP_RIGHT
                "straight" -> ManeuverKind.STRAIGHT
                else -> ManeuverKind.CONTINUE
            }
            "roundabout", "rotary", "roundabout turn" -> ManeuverKind.ROUNDABOUT
            "merge" -> ManeuverKind.MERGE
            "on ramp", "off ramp", "ramp" -> if (m?.contains("left") == true) ManeuverKind.FORK_LEFT else ManeuverKind.RAMP
            "fork" -> if (m == "left") ManeuverKind.FORK_LEFT else ManeuverKind.FORK_RIGHT
            "exit roundabout", "exit rotary" -> ManeuverKind.EXIT
            else -> ManeuverKind.UNKNOWN
        }
    }

    /** اگر نام خیابان نوع راه نداشته باشد، «خیابان» به آن اضافه می‌شود. */
    fun withRoadType(rawName: String?): String? {
        val name = rawName?.let { PersianText.normalize(it) }?.takeIf { it.isNotBlank() } ?: return null
        if (name == "-" || name.equals("null", true)) return null
        val hasPrefix = ROAD_PREFIXES.any { name.startsWith(it) }
        return if (hasPrefix) name else "خیابان $name"
    }

    /** عبارت کوتاه دستور. */
    fun shortPhrase(kind: ManeuverKind, exitNumber: Int? = null): String = when (kind) {
        ManeuverKind.DEPART -> "حرکت کنید"
        ManeuverKind.ARRIVE -> "به مقصد می‌رسید"
        ManeuverKind.CONTINUE -> "به مسیر ادامه دهید"
        ManeuverKind.STRAIGHT -> "مستقیم ادامه دهید"
        ManeuverKind.TURN_LEFT -> "به چپ بپیچید"
        ManeuverKind.TURN_RIGHT -> "به راست بپیچید"
        ManeuverKind.SLIGHT_LEFT -> "کمی به چپ متمایل شوید"
        ManeuverKind.SLIGHT_RIGHT -> "کمی به راست متمایل شوید"
        ManeuverKind.SHARP_LEFT -> "به‌شدت به چپ بپیچید"
        ManeuverKind.SHARP_RIGHT -> "به‌شدت به راست بپیچید"
        ManeuverKind.UTURN -> "دور بزنید و برگردید"
        ManeuverKind.ROUNDABOUT -> if (exitNumber != null && exitNumber > 0)
            "از میدان به خروجی ${PersianText.ordinalWords(exitNumber)} بروید"
        else "وارد میدان شوید"
        ManeuverKind.MERGE -> "با مسیر اصلی ادغام شوید"
        ManeuverKind.RAMP -> "وارد بزرگراه شوید"
        ManeuverKind.EXIT -> "از بزرگراه خارج شوید"
        ManeuverKind.FORK_LEFT -> "از شاخه‌ی چپ ادامه دهید"
        ManeuverKind.FORK_RIGHT -> "از شاخه‌ی راست ادامه دهید"
        ManeuverKind.UNKNOWN -> "به مسیر ادامه دهید"
    }

    /** جمله‌ی کامل: «در ۳۰۰ متر به راست بپیچید و وارد خیابان ولیعصر شوید.» */
    fun fullSentence(kind: ManeuverKind, streetName: String?, exitNumber: Int?, distanceMeters: Double?, isNow: Boolean): String {
        val action = shortPhrase(kind, exitNumber)
        val target = if (kind == ManeuverKind.ARRIVE) null else withRoadType(streetName)
        val lead = when {
            distanceMeters == null -> ""
            isNow || distanceMeters < 30 -> "همین حالا، "
            else -> "در ${PersianText.formatDistanceSpeech(distanceMeters)}، "
        }
        val tail = if (target != null && kind != ManeuverKind.CONTINUE && kind != ManeuverKind.STRAIGHT &&
            kind != ManeuverKind.DEPART
        ) " و وارد $target شوید" else ""
        return (lead + action + tail).trim().ifBlank { action }
    }

    /** نشانه‌ی تصویری (فلش) برای نمایش در کارت راهنما. */
    fun glyph(kind: ManeuverKind): String = when (kind) {
        ManeuverKind.TURN_LEFT -> "⬅"
        ManeuverKind.TURN_RIGHT -> "➡"
        ManeuverKind.SLIGHT_LEFT -> "↖"
        ManeuverKind.SLIGHT_RIGHT -> "↗"
        ManeuverKind.SHARP_LEFT -> "⤴"
        ManeuverKind.SHARP_RIGHT -> "⤵"
        ManeuverKind.UTURN -> "⤶"
        ManeuverKind.ROUNDABOUT -> "⟳"
        ManeuverKind.MERGE, ManeuverKind.RAMP -> "⤴"
        ManeuverKind.EXIT -> "⤵"
        ManeuverKind.FORK_LEFT -> "↖"
        ManeuverKind.FORK_RIGHT -> "↗"
        ManeuverKind.ARRIVE -> "🏁"
        ManeuverKind.DEPART -> "🚗"
        else -> "⬆"
    }

    /** متن کوتاه برای کارت راهنمای مسیر. */
    fun cardTitle(info: ManeuverInfo): String = shortPhrase(info.kind, info.exitNumber)
}
