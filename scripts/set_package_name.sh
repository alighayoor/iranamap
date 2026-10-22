#!/usr/bin/env bash
#
# تغییر کامل نام بسته (package name / applicationId) پروژه
#
# طرز استفاده:
#   ./scripts/set_package_name.sh ir.shoma.naghshe
#   ./scripts/set_package_name.sh com.example.myapp --id-only
#
# گزینه‌ها:
#   --id-only   فقط applicationId را عوض می‌کند (namespace و پوشه‌های کد دست‌نخورده می‌مانند)
#               → ساده‌ترین و کم‌خطرترین روش؛ برای انتشار در استور کافی است.
#
# پیش‌فرض (بدون گزینه): هم applicationId و هم namespace و هم پوشه‌ها و importها را عوض می‌کند.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BLUE='\033[1;34m'; GREEN='\033[1;32m'; YELLOW='\033[1;33m'; RED='\033[1;31m'; NC='\033[0m'
say()  { printf "${BLUE}[IranAmap]${NC} %s\n" "$*"; }
ok()   { printf "${GREEN}✔${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}!${NC} %s\n" "$*"; }
die()  { printf "${RED}✖${NC} %s\n" "$*" >&2; exit 1; }

NEW_ID="${1:-}"
ID_ONLY=false
[[ "${2:-}" == "--id-only" ]] && ID_ONLY=true

[[ -n "$NEW_ID" ]] || die "نام بسته‌ی جدید را بدهید. مثال: ./scripts/set_package_name.sh ir.shoma.naghshe"

# ---------------------------------------------------------------- اعتبارسنجی
if [[ ! "$NEW_ID" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  die "نام بسته نامعتبر است: «$NEW_ID»
شرایط: فقط حروف کوچک انگلیسی، رقم و زیرخط؛ حداقل دو بخش با نقطه؛ هر بخش با حرف شروع شود.
مثال‌های درست: ir.shoma.naghshe , com.example.iranmap , ir.shoma.map3d"
fi
[[ "$NEW_ID" == *"."* ]] || die "نام بسته باید حداقل یک نقطه داشته باشد (مثل ir.shoma.naghshe)."
for reserved in com.iranamap.real com.autonavi.minimap androidx android java kotlin com.google com.android; do
  [[ "$NEW_ID" == "$reserved" || "$NEW_ID" == "$reserved".* ]] && die "پیشوند «$reserved» رزرو/متعلق به دیگران است؛ نام دیگری بگذارید."
done

OLD_ID="com.iranamap.real"
OLD_PATH="com/iranamap/real"
NEW_PATH="$(echo "$NEW_ID" | tr '.' '/')"

say "نام بسته‌ی فعلی: $OLD_ID"
say "نام بسته‌ی جدید: $NEW_ID"
$ID_ONLY && say "حالت: فقط applicationId (namespace و کد دست‌نخورده)"

# ---------------------------------------------------------------- پشتیبان
BACKUP="/tmp/iranamap-package-backup-$(date +%s).tar.gz"
say "پشتیبان‌گیری از فایل‌های حساس در $BACKUP ..."
tar -czf "$BACKUP" \
  app/build.gradle.kts app/src app/proguard-rules.pro 2>/dev/null
ok "پشتیبان ساخته شد (برای بازگشت: tar -xzf \"$BACKUP\")"

# ---------------------------------------------------------------- ۱) applicationId
python3 - "$NEW_ID" "$ID_ONLY" <<'PY'
import re, sys
new_id, id_only = sys.argv[1], sys.argv[2] == "true"
p = "app/build.gradle.kts"
s = open(p, encoding="utf-8").read()

if id_only:
    # فقط مقدار پیش‌فرض applicationId را عوض کن
    s = re.sub(r'val appId: String = \(project\.findProperty\("appId"\) as String\?\) \?: "[^"]+"',
               f'val appId: String = (project.findProperty("appId") as String?) ?: "{new_id}"', s)
    if f'?: "{new_id}"' not in s:
        s = s.replace('applicationId = appId', f'applicationId = "{new_id}"')
else:
    s = s.replace('namespace = "com.iranamap.real"', f'namespace = "{new_id}"')
    s = re.sub(r'val appId: String = \(project\.findProperty\("appId"\) as String\?\) \?: "[^"]+"',
               f'val appId: String = (project.findProperty("appId") as String?) ?: "{new_id}"', s)
open(p, "w", encoding="utf-8").write(s)
print("✓ app/build.gradle.kts به‌روزرسانی شد")
PY

# ---------------------------------------------------------------- ۲) جابه‌جایی پوشه‌ها
if ! $ID_ONLY; then
  for base in app/src/main/java app/src/test/java; do
    if [[ -d "$base/$OLD_PATH" ]]; then
      mkdir -p "$base/$(dirname "$NEW_PATH")"
      if [[ -d "$base/$NEW_PATH" ]]; then
        die "پوشه‌ی مقصد از قبل وجود دارد: $base/$NEW_PATH"
      fi
      git mv "$base/$OLD_PATH" "$base/$NEW_PATH" 2>/dev/null || mv "$base/$OLD_PATH" "$base/$NEW_PATH"
      ok "پوشه منتقل شد: $base/$OLD_PATH → $base/$NEW_PATH"
    fi
  done
fi

# ---------------------------------------------------------------- ۳) جایگزینی متن
if ! $ID_ONLY; then
  FILES=$(grep -rl "com\.iranamap\.real" \
    --include="*.kt" --include="*.kts" --include="*.xml" --include="*.pro" --include="*.md" \
    app/src app/proguard-rules.pro 2>/dev/null || true)
  if [[ -n "$FILES" ]]; then
    echo "$FILES" | while read -r f; do
      [[ -z "$f" ]] && continue
      python3 - "$f" "$OLD_ID" "$NEW_ID" <<'PY'
import sys
path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(path, encoding="utf-8").read()
open(path, "w", encoding="utf-8").write(s.replace(old, new))
PY
    done
    ok "$(echo "$FILES" | wc -l) فایل به‌روزرسانی شد (package/import/R/proguard)"
  fi
fi

# ---------------------------------------------------------------- ۴) بررسی نهایی
say "بررسی اینکه چیزی از نام قدیمی باقی نمانده باشد ..."
LEFTOVER=$(grep -rn "com\.iranamap\.real" --include="*.kt" --include="*.kts" --include="*.xml" \
  app/src app/build.gradle.kts 2>/dev/null | grep -v "local.properties" | head -5 || true)
if [[ -n "$LEFTOVER" ]]; then
  warn "این موارد دست‌نخورده ماندند (بررسی کنید):"
  echo "$LEFTOVER"
else
  ok "هیچ ارجاعی به نام قدیمی نماند."
fi

say "اعتبارسنجی منابع و ارجاع‌ها ..."
if command -v python3 >/dev/null 2>&1 && [[ -f tools/validate_project.py ]]; then
  python3 tools/validate_project.py | tail -4
fi

echo
ok "نام بسته به «$NEW_ID» تغییر کرد."
echo "   ساخت:  ./scripts/build_apk.sh"
echo "   یا:    ./gradlew assembleDebug -PappId=$NEW_ID"
echo "   بازگشت: tar -xzf \"$BACKUP\""
