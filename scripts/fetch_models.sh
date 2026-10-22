#!/usr/bin/env bash
#
# دریافت وابستگی‌های باینری که در مخزن پروژه نگهداری نمی‌شوند:
#   ۱) sherpa-onnx AAR  (موتور گفتار کاملاً آفلاین)
#   ۲) بررسی وجود مدل صدای همراه برنامه (assets)
#
# طرز استفاده:
#   ./scripts/fetch_models.sh              # فقط sherpa-onnx
#   ./scripts/fetch_models.sh --voices     # به‌همراه پیش‌دانلود صداهای اضافی
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS_DIR="$ROOT_DIR/app/libs"
ASSETS_TTS="$ROOT_DIR/app/src/main/assets/tts"
VERSION="1.13.8"
AAR="$LIBS_DIR/sherpa-onnx.aar"

BLUE='\033[1;34m'; GREEN='\033[1;32m'; YELLOW='\033[1;33m'; RED='\033[1;31m'; NC='\033[0m'
say()  { printf "${BLUE}[IranAmap]${NC} %s\n" "$*"; }
ok()   { printf "${GREEN}✔${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}!${NC} %s\n" "$*"; }
die()  { printf "${RED}✖${NC} %s\n" "$*" >&2; exit 1; }

command -v curl >/dev/null 2>&1 || command -v wget >/dev/null 2>&1 || die "curl یا wget لازم است."

download() { # url  dest
  local url="$1" dest="$2"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 -o "$dest" "$url"
  else
    wget -q --timeout=30 --tries=3 -O "$dest" "$url"
  fi
}

mkdir -p "$LIBS_DIR"

# ------------------------------------------------------------------ sherpa-onnx
if [[ -f "$AAR" ]] && [[ "$(stat -c%s "$AAR" 2>/dev/null || echo 0)" -gt 1000000 ]]; then
  ok "sherpa-onnx از قبل موجود است: $AAR ($(du -h "$AAR" | cut -f1))"
else
  say "در حال دانلود sherpa-onnx $VERSION ..."
  TMP="$AAR.part"
  URLS=(
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"
    "https://hub.fastgit.xyz/k2-fsa/sherpa-onnx/releases/download/v$VERSION/sherpa-onnx-$VERSION.aar"
    "https://sourceforge.net/projects/sherpa-onnx.mirror/files/v$VERSION/sherpa-onnx-$VERSION.aar/download"
  )
  SUCCESS=0
  for u in "${URLS[@]}"; do
    say "تلاش: $u"
    if download "$u" "$TMP"; then
      SIZE="$(stat -c%s "$TMP" 2>/dev/null || stat -f%z "$TMP")"
      if [[ "$SIZE" -gt 1000000 ]]; then
        mv "$TMP" "$AAR"; SUCCESS=1; break
      fi
      warn "فایل دریافتی کوچک است ($SIZE بایت) — تلاش بعدی"
    else
      warn "دانلود ناموفق بود — تلاش بعدی"
    fi
  done
  rm -f "$TMP"
  [[ "$SUCCESS" -eq 1 ]] || die "دریافت sherpa-onnx ناموفق بود. فایل را دستی دانلود و در app/libs/sherpa-onnx.aar بگذارید."
  ok "sherpa-onnx آماده شد: $(du -h "$AAR" | cut -f1)"
fi

# ------------------------------------------------------------------ مدل همراه
say "بررسی مدل صدای همراه برنامه (امیر) ..."
BUNDLED="$ASSETS_TTS/vits-piper-fa_IR-amir-medium-int8"
[[ -d "$BUNDLED" ]] || die "پوشه‌ی $BUNDLED پیدا نشد. مدل همراه برنامه بخشی از پروژه است؛ مخزن را کامل دریافت کنید."
[[ -f "$BUNDLED/tokens.txt" ]] || die "فایل tokens.txt در مدل همراه موجود نیست."
[[ -d "$BUNDLED/espeak-ng-data" ]] || die "پوشه‌ی espeak-ng-data در مدل همراه موجود نیست."
MODEL="$(find "$BUNDLED" -maxdepth 1 -name '*.onnx' -print -quit)"
[[ -n "$MODEL" ]] || die "فایل .onnx در مدل همراه موجود نیست."
ok "مدل همراه سالم است: $(basename "$MODEL") ($(du -h "$MODEL" | cut -f1)) — کل assets: $(du -sh "$ASSETS_TTS" | cut -f1)"

# ------------------------------------------------------------------ صداهای اضافی
if [[ "${1:-}" == "--voices" ]]; then
  say "پیش‌دانلود صداهای اضافی (برای بارگذاری در فضای ابری/آینه‌ی داخلی) ..."
  OUT="$ROOT_DIR/voice_packs"; mkdir -p "$OUT"
  BASE="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
  for v in "vits-piper-fa_IR-ganji-medium-int8" "vits-piper-fa_IR-reza_ibrahim-medium-int8"; do
    if [[ -f "$OUT/$v.tar.bz2" ]]; then ok "$v از قبل موجود است"; continue; fi
    say "دانلود $v ..."
    download "$BASE/$v.tar.bz2" "$OUT/$v.tar.bz2" || warn "دانلود $v ناموفق بود (اختیاری)"
  done
  ok "پوشه‌ی voice_packs آماده است (اختیاری — برنامه خودش هم می‌تواند از تنظیمات دانلود کند)"
fi

echo
ok "همه‌ی پیش‌نیازهای باینری آماده‌اند. حالا: ./scripts/build_apk.sh"
