#!/usr/bin/env bash
#
# ساخت APK پروژه‌ی «نقشه ایران»
#
# طرز استفاده:
#   ./scripts/build_apk.sh              # نسخه‌ی آزمایشی (debug) — سریع‌ترین راه نصب روی گوشی
#   ./scripts/build_apk.sh release      # نسخه‌ی انتشار (unsigned؛ نیازمند کلید امضا)
#   ./scripts/build_apk.sh debug clean  # پاک‌سازی و ساخت مجدد
#
# پیش‌نیازها: JDK 17، Android SDK (platform 34 + build-tools)، و اجرای ./scripts/fetch_models.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BLUE='\033[1;34m'; GREEN='\033[1;32m'; YELLOW='\033[1;33m'; RED='\033[1;31m'; NC='\033[0m'
say()  { printf "${BLUE}[IranAmap]${NC} %s\n" "$*"; }
ok()   { printf "${GREEN}✔${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}!${NC} %s\n" "$*"; }
die()  { printf "${RED}✖${NC} %s\n" "$*" >&2; exit 1; }

TASK="${1:-debug}"
CLEAN="${2:-}"

# ------------------------------------------------------------------ ۱) JAVA
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  :
elif command -v java >/dev/null 2>&1; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  export JAVA_HOME
else
  die "JDK پیدا نشد. JDK 17 نصب کنید (مثلاً Temurin 17) و JAVA_HOME را تنظیم کنید."
fi

JAVA_VER="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
say "Java: $JAVA_VER"
case "$JAVA_VER" in
  *\"1.8*|*\"9*|*\"10*|*\"11*|*\"12*|*\"13*|*\"14*|*\"15*|*\"16*)
    warn "برای این پروژه JDK 17 لازم است؛ نسخه‌ی فعلی ممکن است خطا بدهد." ;;
esac

# ------------------------------------------------------------------ ۲) SDK
if [[ -f "$ROOT_DIR/local.properties" ]] && grep -q '^sdk.dir=' "$ROOT_DIR/local.properties"; then
  SDK_DIR="$(grep '^sdk.dir=' "$ROOT_DIR/local.properties" | cut -d'=' -f2- | sed 's|\\:|:|g; s|\\\\|/|g')"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  SDK_DIR="$ANDROID_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK_DIR="$ANDROID_SDK_ROOT"
else
  die "مسیر Android SDK پیدا نشد. فایل local.properties بسازید:  sdk.dir=/path/to/Android/Sdk  (نمونه: local.properties.example)"
fi
[[ -d "$SDK_DIR" ]] || die "پوشه‌ی SDK وجود ندارد: $SDK_DIR"
say "Android SDK: $SDK_DIR"
[[ -d "$SDK_DIR/platforms/android-34" ]] || warn "platform 34 نصب نیست؛ از SDK Manager نصب کنید."
[[ -d "$SDK_DIR/build-tools" ]] || warn "build-tools نصب نیست؛ از SDK Manager نصب کنید."

# ------------------------------------------------------------------ ۳) sherpa-onnx
if [[ ! -f "$ROOT_DIR/app/libs/sherpa-onnx.aar" ]]; then
  warn "sherpa-onnx.aar موجود نیست؛ اجرای scripts/fetch_models.sh ..."
  "$ROOT_DIR/scripts/fetch_models.sh"
fi
ok "sherpa-onnx.aar آماده است."

# ------------------------------------------------------------------ ۴) ساخت
chmod +x "$ROOT_DIR/gradlew"
GRADLE_ARGS=(--no-daemon --stacktrace -Dorg.gradle.jvmargs=-Xmx2048m)

# نام بسته‌ی دلخواه:  APP_ID=ir.shoma.naghshe ./scripts/build_apk.sh
if [[ -n "${APP_ID:-}" ]]; then
  say "نام بسته: $APP_ID"
  GRADLE_ARGS+=("-PappId=$APP_ID")
fi

if [[ "$CLEAN" == "clean" ]]; then
  say "پاک‌سازی خروجی‌های قبلی ..."
  ./gradlew "${GRADLE_ARGS[@]}" clean || warn "clean ناموفق بود؛ ادامه می‌دهیم."
fi

if [[ "$TASK" == "release" ]]; then
  say "ساخت نسخه‌ی release (unsigned) ..."
  ./gradlew "${GRADLE_ARGS[@]}" assembleRelease
  APK_DIR="app/build/outputs/apk/release"
else
  say "ساخت نسخه‌ی debug ..."
  ./gradlew "${GRADLE_ARGS[@]}" assembleDebug
  APK_DIR="app/build/outputs/apk/debug"
fi

echo
if compgen -G "$APK_DIR/*.apk" > /dev/null; then
  ok "ساخت با موفقیت انجام شد:"
  for f in "$APK_DIR"/*.apk; do
    printf "   %s  (%s)\n" "$f" "$(du -h "$f" | cut -f1)"
  done
  echo
  say "نصب روی گوشی متصل (USB debugging فعال):"
  echo "   adb install -r \"$(ls "$APK_DIR"/*.apk | head -1)\""
else
  die "فایل APK تولید نشد. متن خطاهای بالا را بررسی کنید."
fi
