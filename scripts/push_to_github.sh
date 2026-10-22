#!/usr/bin/env bash
#
# آپلود پروژه در گیت‌هاب با یک دستور (روی رایانه‌ی خودتان)
#
# پیش‌نیاز:
#   ۱) git نصب باشد  (git --version)
#   ۲) یک توکن دسترسی شخصی (PAT) با دسترسی Contents: write بسازید:
#      github.com → Settings → Developer settings → Personal access tokens
#        → Fine-grained tokens → Generate new token
#          Repository access: Only select repositories → همان مخزن
#          Permissions: Contents = Read and write
#          Expiration: ۷ روز (یا کوتاه‌تر)
#   ۳) توکن را در متغیر محیطی بگذارید (هرگز داخل فایل یا چت نگذارید):
#      export GH_TOKEN=github_pat_xxxxxxxx
#
# طرز استفاده:
#   ./scripts/push_to_github.sh https://github.com/USERNAME/iranamap.git
#   ./scripts/push_to_github.sh git@github.com:USERNAME/iranamap.git   # با کلید SSH
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BLUE='\033[1;34m'; GREEN='\033[1;32m'; YELLOW='\033[1;33m'; RED='\033[1;31m'; NC='\033[0m'
say()  { printf "${BLUE}[IranAmap]${NC} %s\n" "$*"; }
ok()   { printf "${GREEN}✔${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}!${NC} %s\n" "$*"; }
die()  { printf "${RED}✖${NC} %s\n" "$*" >&2; exit 1; }

REMOTE="${1:-}"
[[ -n "$REMOTE" ]] || die "آدرس مخزن را بدهید. مثال: ./scripts/push_to_github.sh https://github.com/ali/iranamap.git"

command -v git >/dev/null || die "git نصب نیست. از git-scm.com نصب کنید."

# ---------------------------------------------------------------- شاخه و هویت
BRANCH="main"
if ! git rev-parse --git-dir >/dev/null 2>&1; then
  say "ساخت مخزن محلی ..."
  git init -q
  git checkout -q -b "$BRANCH" 2>/dev/null || git branch -M "$BRANCH"
fi

if [[ -z "$(git config user.email || true)" ]]; then
  git config user.email "dev@iranamap.local"
  git config user.name "IranAmap Developer"
  warn "هویت git محلی تنظیم شد (قابل تغییر با git config)."
fi

# ---------------------------------------------------------------- امنیت: فایل‌های حساس
cat > .gitignore <<'EOF'
# گیت نباید این‌ها را ببیند
local.properties
*.jks
*.keystore
keystore.b64
.gradle/
build/
app/build/
.idea/
captures/
.externalNativeBuild/
.cxx/
*.iml
.DS_Store
# کتابخانه‌ی باینری (اسکریپت fetch_models.sh خودش دانلود می‌کند)
app/libs/*.aar
EOF
ok ".gitignore حساس‌ها را مستثنی می‌کند (کلید امضا و local.properties آپلود نمی‌شوند)."

# ---------------------------------------------------------------- بررسی پوشه‌ی منشور
if [[ ! -f ".github/workflows/build-apk.yml" ]]; then
  die "فایل .github/workflows/build-apk.yml پیدا نشد؛ بدون آن ساخت ابری اجرا نمی‌شود."
fi
ok "فایل workflow ساخت APK موجود است."

# ---------------------------------------------------------------- کامیت
say "افزودن فایل‌ها ..."
git add -A
if git diff --cached --quiet; then
  warn "چیزی برای کامیت نیست (احتمالاً قبلاً کامیت شده)."
else
  git commit -q -m "نقشه ایران ۳.۱ — نسخه‌ی فارسی با مرجع‌گیری از آمپ" || true
  ok "کامیت ساخته شد."
fi

# ---------------------------------------------------------------- مخزن راه دور
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE"
else
  git remote add origin "$REMOTE"
fi

# توکن را فقط در همین لحظه و بدون ذخیره‌شدن به URL اضافه می‌کنیم
PUSH_URL="$REMOTE"
if [[ "$REMOTE" == https://* && -n "${GH_TOKEN:-}" ]]; then
  PUSH_URL="https://x-access-token:${GH_TOKEN}@${REMOTE#https://}"
  say "توکن از متغیر محیطی GH_TOKEN خوانده شد (در تاریخچه ذخیره نمی‌شود)."
elif [[ "$REMOTE" == https://* ]]; then
  warn "GH_TOKEN تنظیم نشده؛ git رمز عبور می‌خواهد. گیت‌هاب رمز عبور را قبول نمی‌کند → توکن لازم است."
fi

say "ارسال به $REMOTE ..."
if GIT_TERMINAL_PROMPT=0 git push -u "$PUSH_URL" "$BRANCH" 2>&1 | grep -v "x-access-token" ; then
  :
else
  die "ارسال ناموفق بود. بررسی کنید: توکن معتبر است؟ دسترسی Contents=write دارد؟ مخزن خالی است؟"
fi

# پاک‌کردن آدرس حاوی توکن از تنظیمات محلی
git remote set-url origin "$REMOTE"
ok "آدرس راه دور پاک‌سازی شد (توکن جایی ذخیره نشد)."

echo
ok "پروژه به گیت‌هاب ارسال شد."
echo "   گام بعد: در گیت‌هاب → تب Actions → workflow «ساخت APK» → Run workflow"
echo "   سپس Artifacts → IranAmap-APK را دانلود کنید."
