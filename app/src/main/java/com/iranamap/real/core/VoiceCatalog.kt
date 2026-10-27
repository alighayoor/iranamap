package com.iranamap.real.core

/**
 * صدای فارسی آفلاین (sherpa-onnx + مدل‌های Piper VITS).
 * همه‌ی مدل‌ها روی دستگاه اجرا می‌شوند و هیچ درخواستی به سرورهای TTS ارسال نمی‌شود.
 */
data class VoiceModel(
    val id: String,
    val displayName: String,
    val gender: String,
    val description: String,
    val folder: String,
    val modelFile: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val bundled: Boolean,
    val sampleRate: Int = 22050,
    val quality: String = "متوسط"
) {
    val sizeText: String get() = PersianText.fileSize(sizeBytes)
    val genderFa: String get() = if (gender == "male") "مرد" else "زن"
}

object VoiceCatalog {

    private const val BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    val all: List<VoiceModel> = listOf(
        VoiceModel(
            id = "amir",
            displayName = "امیر",
            gender = "male",
            description = "صدای مردانه‌ی رسمی و شفاف؛ مناسب مسیریابی شهری",
            folder = "vits-piper-fa_IR-amir-medium-int8",
            modelFile = "fa_IR-amir-medium.onnx",
            downloadUrl = "$BASE/vits-piper-fa_IR-amir-medium-int8.tar.bz2",
            sizeBytes = 20_666_906L,
            bundled = true
        ),
        VoiceModel(
            id = "ganji",
            displayName = "گنجی",
            gender = "male",
            description = "صدای مردانه‌ی گرم و طبیعی",
            folder = "vits-piper-fa_IR-ganji-medium-int8",
            modelFile = "fa_IR-ganji-medium.onnx",
            downloadUrl = "$BASE/vits-piper-fa_IR-ganji-medium-int8.tar.bz2",
            sizeBytes = 20_645_796L,
            bundled = false
        ),
        VoiceModel(
            id = "ganji_adabi",
            displayName = "گنجی ادبی",
            gender = "male",
            description = "لحن آرام و کتابی؛ مناسب پادکست و توضیح طولانی",
            folder = "vits-piper-fa_IR-ganji_adabi-medium-int8",
            modelFile = "fa_IR-ganji_adabi-medium.onnx",
            downloadUrl = "$BASE/vits-piper-fa_IR-ganji_adabi-medium-int8.tar.bz2",
            sizeBytes = 20_746_706L,
            bundled = false
        ),
        VoiceModel(
            id = "gyro",
            displayName = "ژیرو",
            gender = "male",
            description = "صدای مردانه‌ی پرانرژی با بیان سریع",
            folder = "vits-piper-fa_IR-gyro-medium-int8",
            modelFile = "fa_IR-gyro-medium.onnx",
            downloadUrl = "$BASE/vits-piper-fa_IR-gyro-medium-int8.tar.bz2",
            sizeBytes = 20_964_438L,
            bundled = false
        ),
        VoiceModel(
            id = "reza",
            displayName = "رضا",
            gender = "male",
            description = "صدای مردانه‌ی خبری با تلفظ دقیق نام‌های خاص",
            folder = "vits-piper-fa_IR-reza_ibrahim-medium-int8",
            modelFile = "fa_IR-reza_ibrahim-medium.onnx",
            downloadUrl = "$BASE/vits-piper-fa_IR-reza_ibrahim-medium-int8.tar.bz2",
            sizeBytes = 21_052_889L,
            bundled = false
        ),
        VoiceModel(
            id = "amir_hq",
            displayName = "امیر (کیفیت بالا)",
            gender = "male",
            description = "نسخه‌ی fp32 با کیفیت بالاتر و حجم بیشتر",
            folder = "vits-piper-fa_IR-amir-medium",
            modelFile = "fa_IR-amir-medium.onnx",
            downloadUrl = "$BASE/vits-piper-fa_IR-amir-medium.tar.bz2",
            sizeBytes = 67_180_373L,
            bundled = false,
            quality = "بالا"
        ),
        // صدای پایه استودیویی - آرش و آناهیتا (از پیش ضبط‌شده + fallback شیرپا)
        VoiceModel(
            id = "arash",
            displayName = "آرش",
            gender = "male",
            description = "صدای مردانه‌ی آرش - استودیویی، 51 عبارت پایه + نام خیابان‌ها",
            folder = "voices/arash/base",
            modelFile = "lexicon",
            downloadUrl = "",
            sizeBytes = 8_000_000L,
            bundled = false,
            quality = "استودیویی"
        ),
        VoiceModel(
            id = "anahita",
            displayName = "آناهیتا",
            gender = "female",
            description = "صدای زنانه‌ی آناهیتا - استودیویی، 51 عبارت پایه + 14k خیابان شیراز",
            folder = "voices/anahita/base",
            modelFile = "lexicon",
            downloadUrl = "",
            sizeBytes = 12_000_000L,
            bundled = false,
            quality = "استودیویی"
        )
    )

    val bundled: VoiceModel get() = all.first { it.bundled }

    fun find(id: String?): VoiceModel? = all.firstOrNull { it.id == id }

    fun downloadUrlFor(model: VoiceModel): String = model.downloadUrl
}
