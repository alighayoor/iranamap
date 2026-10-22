# ساخت APK در فضای ابری (بدون هیچ ابزار روی رایانه) — گام به گام

هدف: زیپ پروژه را بدهید، در ابر ساخته شود، APK کامل با همه‌ی کتابخانه‌ها را دانلود کنید. روی رایانه‌ی کم‌رم هم کار می‌کند، چون **همه‌ی پردازش در سرور** انجام می‌شود.

---

## ۰) یک واقعیت مهم که باید بدانید

سرویس‌های «آنلاین تبدیل به APK» که فقط یک فایل می‌گیرند و APK می‌دهند (مثل AppsGeyser یا ApkOnline) فقط **وب‌سایت/HTML** را بسته‌بندی می‌کنند. برنامه‌ی ما یک پروژه‌ی واقعی اندروید (Kotlin + Gradle + کتابخانه‌ی بومی `sherpa-onnx.aar` و مدل ۲۰ مگابایتی) است و به دانشگاه زیرساخت زیر نیاز دارد:

- Android SDK (Platform 34 + Build-Tools 34)
- JDK 17
- Gradle 8.7 + دسترسی به Google Maven / Maven Central
- دانلود `sherpa-onnx-1.13.8.aar` از GitHub

بنابراین روش درست، استفاده از یک **CI ابری** است. راه رایگان و مطمئن: **GitHub Actions** (فایل workflow از قبل داخل پروژه هست).
جایگزین‌ها: **Codemagic** (فایل `codemagic.yaml` هم موجود است)، Appcircle، Bitrise، Cirrus CI.

> خوبی‌اش این است که دیگر لازم نیست دستور `./gradlew` روی رایانه‌ی خودتان اجرا شود.

---

## ۱) راه‌حل اصلی: GitHub Actions (رایگان، حدود ۱۰ دقیقه)

### گام ۱ — حساب گیت‌هاب
اگر ندارید در `github.com` ثبت‌نام کنید (رایگان).

### گام ۲ — ساخت مخزن (Repository)
1. بالا سمت راست: `+` → `New repository`
2. نام: `iranamap`
3. نوع: **Private** یا Public (هر دو رایگان و مجاز)
4. `Add a README file` را **تیک نزنید** (چون فایل‌های خودمان را آپلود می‌کنیم)
5. `Create repository`

### گام ۳ — بالاگذاری پروژه
فایل `IranAmap-3.0-fa-source.zip` را روی رایانه باز کنید. سپس یکی از دو راه:

#### راه الف — آپلود با مرورگر (بدون نصب هیچ برنامه‌ای)
1. در صفحه‌ی مخزن: `Add file ▸ Upload files`
2. **همه‌ی** فایل‌ها و پوشه‌های داخل زیپ را بکشید و رها کنید (نه خودِ پوشه‌ی `IranAmap`، بلکه محتویاتش).
3. پایین صفحه `Commit changes`.

> ⚠️ **نکته‌ی حیاتی:** پوشه‌ی `.github` مخفی است و ویندوز/مک به‌طور پیش‌فرض نشانش نمی‌دهد. اگر آپلود نشد، دو راه دارید:
> - **ویندوز:** در پنجره‌ی پوشه `View ▸ Show ▸ Hidden items` را روشن کنید؛ **مک:** `Cmd+Shift+.`
> - **یا ساخت دستی:** در گیت‌هاب `Add file ▸ Create new file` → در کادر نام بنویسید
>   `.github/workflows/build-apk.yml` → محتوای فایل `build-apk.yml` را از پروژه کپی و پیست کنید → Commit.

فایل‌های ضروری برای ساخت:

```
build.gradle.kts · settings.gradle.kts · gradle.properties · gradlew · gradlew.bat
gradle/wrapper/…            ← بدون این‌ها Gradle اجرا نمی‌شود
app/build.gradle.kts · app/proguard-rules.pro
app/src/…                   ← کد، منابع، و مدل صوتی assets
scripts/…                   ← fetch_models.sh و validate_project.py لازم‌اند
tools/validate_project.py
.github/workflows/build-apk.yml
```

فایل‌هایی که اگر جا افتادند مهم نیست: `docs/`, `README.md`, `codemagic.yaml`.

#### راه ب — با Git (اگر نصب دارید)
```bash
cd IranAmap
git init && git add -A && git commit -m "نقشه ایران ۳.۰"
git branch -M main
git remote add origin https://github.com/<username>/iranamap.git
git push -u origin main
```
(در این روش پوشه‌ی مخفی `.github` هم قطعاً منتقل می‌شود.)

### گام ۴ — اجرای ساخت
1. تب **Actions** را باز کنید.
2. اگر پیام «Workflows aren’t being run on this repository» آمد، دکمه‌ی **I understand my workflows, go ahead and enable them** را بزنید.
3. از ستون چپ، workflow **«ساخت APK»** را انتخاب کنید.
4. `Run workflow ▸`:
   - **نام بسته (applicationId):** مثلاً `ir.shoma.naghshe` (یا خالی بگذارید)
   - **نوع ساخت:** `debug` (نصب‌شدنی روی گوشی خودتان) یا `release` (برای استور، بدون امضا)
5. `Run workflow` را بزنید.

### گام ۵ — پیگیری و دانلود
- صفحه‌ی اجرا را باز کنید؛ مراحل زنده نمایش داده می‌شوند:
  1. `دریافت کد`
  2. `نصب JDK 17` / `نصب Android SDK`
  3. `آماده‌سازی موتور صدا` ← دانلود ~۸ مگابایت sherpa-onnx
  4. `اعتبارسنجی ایستا` و `آزمون‌های واحد` (۱۱۴ آزمون)
  5. `ساخت APK`
  6. `بارگذاری APK به‌عنوان Artifact`
- زمان کل: **۵ تا ۱۰ دقیقه** (بار اول؛ بارهای بعد با کش Gradle حدود ۳ دقیقه).
- پس از پایان، پایین صفحه‌ی اجرا بخش **Artifacts** ظاهر می‌شود:
  **`IranAmap-APK`** → دانلود → یک فایل ZIP شامل `app-debug.apk`.
- ZIP را باز کنید و `app-debug.apk` را روی گوشی نصب کنید (اجازه‌ی «نصب از منابع ناشناس»).

### گام ۶ — اگر خواستید بدون تغییر کد، نام بسته را عوض کنید
همان فیلد `نام بسته` در `Run workflow` کافی است؛ همان APK با شناسه‌ی جدید ساخته می‌شود.

---

## ۲) نکته‌های مهم GitHub Actions

| موضوع | توضیح |
|---|---|
| سهمیه‌ی رایگان | مخزن عمومی: نامحدود · مخزن خصوصی: **۲٬۰۰۰ دقیقه در ماه** (هر ساخت ~۶ دقیقه → ~۳۰۰ ساخت) |
| محل APK | فقط در Artifact (۳۰ روز نگه داشته می‌شود)؛ در خود مخزن فایل APK کامیت نمی‌شود |
| آزمون‌ها | اگر آزمونی شکست بخورد، ساخت متوقف می‌شود و گزارش آزمون به‌عنوان Artifact جدا بارگذاری می‌شود |
| اجرای خودکار | با هر push روی `main` هم خودکار ساخته می‌شود (می‌توانید در فایل workflow حذفش کنید) |
| گیر کردن دانلود sherpa-onnx | در CI معمولاً بی‌مشکل است؛ اگر شبکه محدود بود، فایل `.aar` را دستی در مخزن `app/libs/` بگذارید (اسکریپت اگر فایل باشد دانلود نمی‌کند) |

---

## ۳) جایگزین: Codemagic (اگر گیت‌هاب نمی‌خواهید)

1. در `codemagic.io` با گیت‌هاب/گیت‌لب/بیت‌باکت وارد شوید (۵۰۰ دقیقه رایگان در ماه).
2. `Add project` → مخزن خود را انتخاب کنید.
3. فایل `codemagic.yaml` که در ریشه‌ی پروژه هست خودکار شناسایی می‌شود.
4. `Start new build` → `android-debug`.
5. APK از تب **Artifacts** همان ساخت قابل دانلود است.

سرویس‌های مشابه: **Appcircle** (رایگان برای پروژه‌ی متن‌باز)، **Bitrise** (پلن رایگان محدود)، **Cirrus CI** (رایگان برای مخزن عمومی).

---

## ۴) نسخه‌ی release با امضا (برای انتشار در بازار/مایکت)

ساخت debug برای خودتان کافی است؛ برای استور باید امضا شود.

### الف) ساخت کلید روی هر رایانه‌ای با JDK
```bash
keytool -genkeypair -v -keystore iranamap-release.jks -alias iranamap \
        -keyalg RSA -keysize 2048 -validity 10000
```

### ب) افزودن امضا به پروژه
در `app/build.gradle.kts`، داخل بلوک `android { }`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "../iranamap-release.jks")
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "iranamap"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

### ج) در گیت‌هاب
1. کلید را به Base64 تبدیل کنید:
   ```bash
   base64 -w0 iranamap-release.jks > keystore.b64     # لینوکس
   # مک: base64 -i iranamap-release.jks -o keystore.b64
   ```
2. `Settings ▸ Secrets and variables ▸ Actions ▸ New repository secret` و این‌ها را بسازید:
   `KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`
3. در فایل workflow، قبل از مرحله‌ی ساخت اضافه کنید:
   ```yaml
   - name: بازسازی کلید امضا
     run: |
       echo "${{ secrets.KEYSTORE_B64 }}" | base64 -d > "$RUNNER_TEMP/release.jks"
       echo "KEYSTORE_PATH=$RUNNER_TEMP/release.jks" >> "$GITHUB_ENV"
       echo "STORE_PASSWORD=${{ secrets.STORE_PASSWORD }}" >> "$GITHUB_ENV"
       echo "KEY_PASSWORD=${{ secrets.KEY_PASSWORD }}" >> "$GITHUB_ENV"
       echo "KEY_ALIAS=${{ secrets.KEY_ALIAS }}" >> "$GITHUB_ENV"
   ```
4. سپس `build_type=release` را انتخاب کنید.

> کلید `.jks` را جای امن نگه دارید؛ بدون آن نمی‌توانید آپدیت منتشر کنید.

---

## ۵) عیب‌یابی ساخت ابری

| نشانه | علت | راه‌حل |
|---|---|---|
| تب Actions خالی است | `.github/workflows/build-apk.yml` آپلود نشده (پوشه‌ی مخفی) | گام ۳، «نکته‌ی حیاتی» — فایل را دستی در گیت‌هاب بسازید |
| `Permission denied: ./gradlew` | مجوز اجرا از دست رفته | در workflow هست (`chmod +x`)؛ اگر دستی اجرا می‌کنید: `chmod +x gradlew` |
| `SDK location not found` | `android-actions/setup-android` حذف شده | همان step را برگردانید |
| `Could not resolve org.maplibre.gl:android-sdk` | قطعی موقت شبکه‌ی Maven | دوباره Run workflow بزنید |
| `sherpa-onnx.aar` دانلود نشد | محدودیت شبکه در runner | فایل `.aar` را در `app/libs/` مخزن بگذارید |
| `JavaScript heap out of memory` / `OutOfMemoryError` | حافظه‌ی Gradle کم | `-Dorg.gradle.jvmargs=-Xmx4096m` در دستور ساخت |
| Artifact خالی است | مسیر APK تغییر کرده | مرحله‌ی `نام‌گذاری خروجی` را ببینید: `app/build/outputs/apk/**` |
| ساخت debug موفق ولی نصب نمی‌شود | نسخه‌ی قبلی با امضای دیگر نصب است | `adb uninstall <نام بسته>` یا از تنظیمات حذف کنید |
| زمان ساخت بیش از ۴۵ دقیقه | `timeout-minutes` کم است یا کش سرد | `timeout-minutes: 90` |

---

## ۶) خلاصه‌ی مسیر شما

```
۱) ./scripts/set_package_name.sh ir.shoma.naghshe     ← نام بسته (روی رایانه یا در repo)
۲) زیپ را در گیت‌هاب آپلود کنید (همراه پوشه‌ی .github)
۳) Actions ▸ ساخت APK ▸ Run workflow
۴) دانلود IranAmap-APK از Artifacts
۵) نصب app-debug.apk روی گوشی
```

راهنمای نام بسته: [تغییر-نام-بسته.md](تغییر-نام-بسته.md) · راهنمای محلی: [راهنمای-ساخت-و-نصب.md](راهنمای-ساخت-و-نصب.md)
