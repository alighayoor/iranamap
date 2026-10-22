#!/usr/bin/env bash
#
# اجرای همه‌ی آزمون‌ها:
#   ۱) اعتبارسنجی ایستا (منابع، شناسه‌ها، ارجاع‌های کد) — بدون نیاز به اندروید
#   ۲) آزمون‌های واحد JUnit برای هسته‌ی مسیریابی، چراغ‌ها، صدا و آفلاین
#
# طرز استفاده:
#   ./scripts/test_all.sh            # اعتبارسنجی + آزمون‌های گرَیدل
#   ./scripts/test_all.sh --static   # فقط اعتبارسنجی ایستا (بدون Gradle، سریع)
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BLUE='\033[1;34m'; GREEN='\033[1;32m'; RED='\033[1;31m'; NC='\033[0m'
say() { printf "${BLUE}[IranAmap]${NC} %s\n" "$*"; }
ok()  { printf "${GREEN}✔${NC} %s\n" "$*"; }
die() { printf "${RED}✖${NC} %s\n" "$*" >&2; exit 1; }

# ------------------------------------------------------------------ ۱) ایستا
if command -v python3 >/dev/null 2>&1; then
  say "اعتبارسنجی ایستای منابع و ارجاع‌ها ..."
  python3 tools/validate_project.py || die "اعتبارسنجی ایستا خطا داشت."
  ok "اعتبارسنجی ایستا بدون خطا."
else
  say "python3 پیدا نشد؛ از اعتبارسنجی ایستا صرف‌نظر شد."
fi

if [[ "${1:-}" == "--static" ]]; then
  ok "پایان (حالت ایستا)."
  exit 0
fi

# ------------------------------------------------------------------ ۲) واحد
say "اجرای آزمون‌های واحد Gradle ..."
chmod +x ./gradlew
./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx2048m testDebugUnitTest

RESULT_DIR="app/build/test-results/testDebugUnitTest"
if [[ -d "$RESULT_DIR" ]]; then
  TOTAL=$(grep -ho 'tests="[0-9]*"' "$RESULT_DIR"/*.xml | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
  FAILS=$(grep -ho 'failures="[0-9]*"' "$RESULT_DIR"/*.xml | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
  ERRS=$(grep -ho 'errors="[0-9]*"' "$RESULT_DIR"/*.xml | grep -o '[0-9]*' | awk '{s+=$1} END {print s+0}')
  echo
  say "نتیجه: $TOTAL آزمون، $FAILS شکست، $ERRS خطا"
  [[ "$FAILS" -eq 0 && "$ERRS" -eq 0 ]] || die "برخی آزمون‌ها شکست خوردند. گزارش: $RESULT_DIR"
fi

echo
ok "همه‌ی آزمون‌ها با موفقیت اجرا شدند."
