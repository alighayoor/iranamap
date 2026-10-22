package com.iranamap.real.core

/** ساخت همه‌ی جملات صوتی مسیریابی به فارسی. کاملاً خالص و قابل تست. */
object VoicePhrases {

    fun routeStarted(destination: String?, totalMeters: Double, totalSeconds: Long, lightCount: Int): String {
        val dest = destination?.takeIf { it.isNotBlank() }?.let { " به سمت ${ManeuverText.withRoadType(it) ?: it}" } ?: ""
        val base = "مسیریابی آغاز شد$dest. مسافت مسیر ${PersianText.formatDistanceSpeech(totalMeters)} " +
            "و زمان تقریبی ${PersianText.formatDurationSpeech(totalSeconds)} است."
        val lights = if (lightCount > 0)
            " در این مسیر ${PersianText.numberToWords(lightCount.toLong())} چراغ راهنمایی قرار دارد." else ""
        return PersianText.normalize(base + lights)
    }

    fun maneuver(kind: ManeuverKind, streetName: String?, exitNumber: Int?, distanceMeters: Double, isNow: Boolean): String =
        PersianText.normalize(
            ManeuverText.fullSentence(kind, streetName, exitNumber, distanceMeters, isNow) + "."
        )

    fun straightContinue(streetName: String?, distanceMeters: Double): String {
        val s = ManeuverText.withRoadType(streetName)
        return if (s != null)
            "در ${PersianText.formatDistanceSpeech(distanceMeters)}، ${ManeuverText.shortPhrase(ManeuverKind.CONTINUE)} در $s."
        else "در ${PersianText.formatDistanceSpeech(distanceMeters)}، ${ManeuverText.shortPhrase(ManeuverKind.CONTINUE)}."
    }

    fun trafficLight(color: LightColor, remainingSec: Int, distanceMeters: Double): String {
        val d = PersianText.formatDistanceSpeech(distanceMeters)
        val rem = PersianText.numberToWords(remainingSec.toLong())
        return when (color) {
            LightColor.RED -> "چراغ راهنمایی در $d جلوتر قرمز است. حدود $rem ثانیه مانده تا سبز شود."
            LightColor.YELLOW -> "چراغ راهنمایی در $d جلوتر زرد شد. آماده‌ی توقف باشید."
            LightColor.GREEN -> "چراغ راهنمایی در $d جلوتر سبز است، می‌توانید عبور کنید."
        }
    }

    fun trafficLightImminent(color: LightColor, remainingSec: Int): String = when (color) {
        LightColor.RED -> "چراغ قرمز است. ${PersianText.numberToWords(remainingSec.toLong())} ثانیه مانده."
        LightColor.YELLOW -> "چراغ زرد شد، بایستید."
        LightColor.GREEN -> "چراغ سبز است، عبور کنید."
    }

    fun greenWave(speedKmh: Int, greenCount: Int): String =
        "با سرعت ${PersianText.numberToWords(speedKmh.toLong())} کیلومتر بر ساعت، " +
            "${PersianText.numberToWords(greenCount.toLong())} چراغ بعدی سبز است. موج سبز."

    fun arrival(destination: String?): String {
        val dest = destination?.takeIf { it.isNotBlank() }?.let { "$it " } ?: ""
        return "به مقصد ${dest}رسیدید. سفر خوبی داشته باشید."
    }

    fun offRoute(): String = "از مسیر خارج شدید. در حال محاسبه‌ی مسیر جدید هستم."

    fun rerouted(): String = "مسیر جدید محاسبه شد. مسیریابی ادامه دارد."

    fun offRouteFar(newRouteMeters: Double): String =
        "از مسیر فاصله گرفتید. مسیر جدید ${PersianText.formatDistanceSpeech(newRouteMeters)} است."

    fun speedWarning(speedKmh: Int): String =
        "سرعت شما ${PersianText.numberToWords(speedKmh.toLong())} کیلومتر بر ساعت است. لطفاً آرام‌تر برانید."

    fun destinationSet(name: String?): String =
        if (name.isNullOrBlank()) "مقصد انتخاب شد." else "مقصد شما ${ManeuverText.withRoadType(name)} تعیین شد."

    fun remainingSummary(meters: Double, seconds: Long, clock: String): String =
        "${PersianText.formatDistanceSpeech(meters)} تا مقصد باقی مانده. زمان رسیدن " +
            "${PersianText.formatDurationSpeech(seconds)} دیگر، ساعت $clock."

    /** هشدار عبور از محدودیت سرعت. */
    fun overSpeedLimit(speedKmh: Int, limitKmh: Int): String =
        "سرعت شما ${PersianText.numberToWords(speedKmh.toLong())} کیلومتر بر ساعت است؛ " +
            "محدودیت ${PersianText.numberToWords(limitKmh.toLong())} کیلومتر است."

    /** مسیر جایگزین سریع‌تر پیدا شد. */
    fun fasterRouteAvailable(savedSeconds: Long): String =
        "مسیر سریع‌تری پیدا شد که ${PersianText.formatDurationSpeech(savedSeconds)} زودتر می‌رساند."

    /** اعلام ترافیک تخمینی. */
    fun trafficAhead(delaySeconds: Long): String =
        "در مسیر پیش‌رو ترافیک تخمینی وجود دارد؛ حدود ${PersianText.formatDurationSpeech(delaySeconds)} تأخیر."

    fun navigationStopped(): String = "مسیریابی پایان یافت."

    fun gpsLost(): String = "سیگنال موقعیت‌یاب ضعیف است. لطفاً مسیر را با احتیاط ادامه دهید."

    fun arrivedAtLightWithCountdown(color: LightColor, seconds: Int): String = when (color) {
        LightColor.RED -> "چراغ قرمز. ${PersianText.numberToWords(seconds.toLong())} ثانیه صبر کنید."
        LightColor.YELLOW -> "چراغ زرد."
        LightColor.GREEN -> "چراغ سبز."
    }
}
