# گزارش ساخت APK در فضای ابری (GitHub Actions)

این سند نتیجه‌ی ساخت واقعی بسته‌ی نصبی را ثبت می‌کند.

## ۱. مشخصات بسته‌ی ساخته‌شده

| مورد | مقدار |
|---|---|
| فایل | `IranAmap-3.3.0-debug.apk` |
| حجم | ۹۸ مگابایت (۱۰۲٬۵۴۲٬۶۷۹ بایت) |
| نام بسته | `com.iranamap.real` |
| نسخه | `3.3.0` (versionName) |
| نوع ساخت | `debug` — امضاشده با کلید دیباگ (قابل نصب روی دستگاه واقعی) |
| معماری‌ها | `arm64-v8a` و `armeabi-v7a` (شامل کتابخانه‌های بومی sherpa-onnx) |
| SHA-256 | `8291a03618debf5c45b228dbf9894de36baddeb1f30a483fb873c08ecbc8c3b3` |

محتوای کلیدی داخل APK:

- `classes*.dex` — ۱۲ فایل dex، شامل کل کد Kotlin برنامه و همه‌ی کتابخانه‌ها (MapLibre، Retrofit، OkHttp، Gson، Play Services Location، AndroidX، Material، sherpa-onnx). **هیچ کتابخانه‌ای بیرون از بسته لازم نیست.**
- `lib/arm64-v8a/*.so` و `lib/armeabi-v7a/*.so` — موتور گفتار آفلاین (onnxruntime + sherpa-onnx) برای هر دو معماری رایج.
- `assets/tts/vits-piper-fa_IR-amir-medium-int8/` — مدل صوتی فارسی (۱۸٫۵ مگابایت، `fa_IR-amir-medium.onnx`) + `tokens.txt` + داده‌های espeak-ng برای فارسی. صدا کاملاً روی دستگاه تولید می‌شود و به TTS اندروید وابسته نیست.
- `res/` — همه‌ی چیدمان‌ها، رشته‌های فارسی و فونت وزیرمتن.

## ۲. مشخصات اجرای ابری

| مورد | مقدار |
|---|---|
| مخزن | `alighayoor/iranamap` (خصوصی) |
| گردش‌کار | `.github/workflows/build-apk.yml` — «ساخت APK» |
| اجرا | `#39837278781` — **موفق** |
| مدت | حدود ۵٫۵ دقیقه |
| Artifact | `IranAmap-APK` |

مراحل موفق: دریافت کد → نصب JDK 17 → کش Gradle → آماده‌سازی Android SDK (نصب دستی با `sdkmanager`) → آماده‌سازی موتور صدا → اعتبارسنجی ایستا → آزمون‌های واحد (۱۱۴ آزمون) → ساخت APK → نام‌گذاری → بارگذاری Artifact.

## ۳. خطاهایی که در مسیر رفع شد

1. **`android-actions/setup-android@v3`** روی runnerهای فعلی با خطای `Failed to find package 'tools'` شکست می‌خورد → با نصب دستی `cmdline-tools` + `sdkmanager` جایگزین شد.
2. **`Unresolved reference: net`** در `app/build.gradle.kts` — در Kotlin DSL عبارت `java.net.URI(...)` به افزونه‌ی Gradle تفسیر می‌شد → `import java.net.URI` در بالای فایل.
3. **`onNewIntent(Intent?)` overrides nothing** در `MapActivity` — در API 34 امضا غیرnullable است → `override fun onNewIntent(intent: Intent)`.
4. **ناسازگاری nullability در callbackهای MapLibre 11.8** — هر رابط امضای خودش را دارد و کد قبلی همه را یکسان گرفته بود:

   | رابط | `onStatus`/`onList`/... | `onError` |
   |---|---|---|
   | `OfflineManager.ListOfflineRegionsCallback` | `onList(Array<OfflineRegion>?)` | `String` (غیرnullable) |
   | `OfflineManager.CreateOfflineRegionCallback` | `onCreate(OfflineRegion)` | `String` |
   | `OfflineRegion.OfflineRegionDeleteCallback` | `onDelete()` | `String` |
   | `OfflineRegion.OfflineRegionStatusCallback` | `onStatus(OfflineRegionStatus?)` | **`String?`** (nullable) |
   | `OfflineRegion.OfflineRegionObserver` | `onStatusChanged(OfflineRegionStatus)` | `OfflineRegionError` |

   این امضاها از خودِ `classes.jar` نسخه‌ی ۱۱.۸.۰ استخراج و سپس با کامپایل واقعی Kotlin روی همان AARها تأیید شد.

## ۴. روش نصب روی گوشی

1. فایل `IranAmap-3.3.0-debug.apk` را به گوشی منتقل کنید (کابل، بلوتوث یا فضای ابری).
2. در تنظیمات اندروید، «نصب از منابع ناشناس» را برای فایل‌منیجر یا مرورگری که استفاده می‌کنید فعال کنید.
3. روی فایل بزنید و «نصب» را تأیید کنید. اگر نسخه‌ی قدیمی نصب است، ابتدا حذفش کنید (کلید امضا یکی است، اما نسخه‌ها ممکن است تداخل کنند).
4. اولین اجرا: دادن دسترسی موقعیت مکانی و (اختیاری) اعلان‌ها. صدا بدون نیاز به دانلود اینترنتی کار می‌کند، چون مدل فارسی داخل بسته است.

### محدودیت‌ها

- این بسته **debug** است: برای تست و استفاده‌ی شخصی کاملاً کار می‌کند، اما برای انتشار در Google Play باید نسخه‌ی release با کلید خودتان امضا شود (`./scripts/build_apk.sh` با `KEYSTORE_*`).
- شبیه‌سازهای x86/x86_64 پشتیبانی نمی‌شوند (کتابخانه‌های بومی sherpa-onnx فقط ARM هستند)؛ روی دستگاه واقعی نصب کنید.
- نقشه، مسیریابی و جست‌وجو در اولین استفاده به اینترنت نیاز دارند؛ بسته‌های آفلاین شهرها از بخش «تنظیمات ← دانلود شهرها» گرفته می‌شوند.

## ۵. ساخت مجدد

هر بار که کد را push کنید، GitHub Actions خودکار APK را می‌سازد و در بخش Actions → آخرین اجرا → Artifacts قابل دریافت است. برای اجرای دستی از تب Actions دکمه‌ی «Run workflow» را بزنید (پارامترها: `app_id` و `build_type`).
