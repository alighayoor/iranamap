#!/usr/bin/env python3
"""
اعتبارسنجی ایستا پروژه‌ی نقشه ایران:
- سلامت ساختار XML
- وجود همه‌ی منابع ارجاع‌داده‌شده (string/color/drawable/layout/id/font/style/xml)
- تطابق شناسه‌های استفاده‌شده در کاتلین با چیدمان‌ها
- تعادل پرانتز/آکولاد و وجود کلاس‌های ارجاع‌شده‌ی داخلی
"""
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main")
RES = os.path.join(SRC, "res")
JAVA = os.path.join(SRC, "java")
TEST = os.path.join(ROOT, "app", "src", "test", "java")

errors = []
warnings = []
info = defaultdict(int)


def add_error(msg):
    errors.append(msg)


def xml_files(base):
    for dirpath, _, files in os.walk(base):
        for f in files:
            if f.endswith(".xml"):
                yield os.path.join(dirpath, f)


# ---------------------------------------------------------------- 1. XML health
for path in list(xml_files(RES)) + [os.path.join(SRC, "AndroidManifest.xml")]:
    if not os.path.exists(path):
        continue
    try:
        ET.parse(path)
        info["xml_ok"] += 1
    except Exception as e:
        add_error(f"XML نامعتبر: {os.path.relpath(path, ROOT)} → {e}")

# ---------------------------------------------------------------- 2. resources
defined = defaultdict(set)

for path in xml_files(RES):
    rel = os.path.relpath(path, RES)
    folder = rel.split(os.sep)[0]
    name = os.path.splitext(os.path.basename(path))[0]
    if folder.startswith("values"):
        try:
            tree = ET.parse(path)
        except Exception:
            continue
        for el in tree.getroot():
            tag = el.tag
            n = el.get("name")
            if not n:
                continue
            if tag == "string":
                defined["string"].add(n)
            elif tag in ("color", "dimen", "bool", "integer", "style", "string-array",
                         "integer-array", "fraction", "plurals", "attr"):
                defined[{"color": "color", "dimen": "dimen", "bool": "bool",
                         "integer": "integer", "style": "style",
                         "string-array": "array", "integer-array": "array",
                         "fraction": "fraction", "plurals": "plurals",
                         "attr": "attr"}[tag]].add(n)
    elif folder.startswith("layout"):
        defined["layout"].add(name)
        # شناسه‌ها
        with open(path, encoding="utf-8") as f:
            text = f.read()
        for m in re.finditer(r'@\+id/([A-Za-z0-9_]+)', text):
            defined["id"].add(m.group(1))
    elif folder.startswith("drawable"):
        defined["drawable"].add(name)
    elif folder.startswith("mipmap"):
        defined["mipmap"].add(name)
    elif folder.startswith("font"):
        defined["font"].add(name)
    elif folder == "xml":
        defined["xml"].add(name)
    elif folder.startswith("menu"):
        defined["menu"].add(name)
        with open(path, encoding="utf-8") as f:
            mtext = f.read()
        for m in re.finditer(r'@\+id/([A-Za-z0-9_]+)', mtext):
            defined["id"].add(m.group(1))
    elif folder.startswith("anim"):
        defined["anim"].add(name)

# فونت‌های ttf
font_dir = os.path.join(RES, "font")
if os.path.isdir(font_dir):
    for f in os.listdir(font_dir):
        if f.endswith(".ttf") or f.endswith(".otf"):
            defined["font"].add(os.path.splitext(f)[0])

info["strings"] = len(defined["string"])
info["ids"] = len(defined["id"])
info["layouts"] = len(defined["layout"])
info["drawables"] = len(defined["drawable"])

# ---------------------------------------------------------------- 3. XML references
REF_RE = re.compile(r"@(string|color|drawable|layout|font|style|xml|mipmap|anim|dimen|bool|integer|array)/([A-Za-z0-9_.]+)")
for path in list(xml_files(RES)) + [os.path.join(SRC, "AndroidManifest.xml")]:
    if not os.path.exists(path):
        continue
    with open(path, encoding="utf-8") as f:
        text = f.read()
    for kind, name in REF_RE.findall(text):
        if kind in ("dimen", "bool", "integer", "array") and name in ("true", "false"):
            continue
        if kind == "style" and ("." in name or name.startswith(("Widget.", "Theme.", "TextAppearance.", "Base."))):
            continue  # استایل‌های کتابخانه‌ای Material/AndroidX
        if name not in defined[kind]:
            add_error(f"منبع تعریف‌نشده @{kind}/{name} در {os.path.relpath(path, ROOT)}")

# ---------------------------------------------------------------- 4. Kotlin references
kotlin_files = []
for base in (JAVA, TEST):
    for dirpath, _, files in os.walk(base):
        for f in files:
            if f.endswith(".kt"):
                kotlin_files.append(os.path.join(dirpath, f))

info["kotlin_files"] = len(kotlin_files)

R_RE = re.compile(r"\bR\.(string|color|drawable|layout|id|font|style|xml|mipmap|dimen|plurals|array)\.([A-Za-z0-9_]+)")
for path in kotlin_files:
    with open(path, encoding="utf-8") as f:
        text = f.read()
    rel = os.path.relpath(path, ROOT)
    for kind, name in R_RE.findall(text):
        if kind == "style" and ("." in name or name.startswith(("Widget.", "Theme.", "TextAppearance.", "Base."))):
            continue  # استایل‌های کتابخانه‌ای Material/AndroidX
        if name not in defined[kind]:
            add_error(f"منبع تعریف‌نشده R.{kind}.{name} در {rel}")
    # تعادل پرانتز و آکولاد (با نادیده‌گرفتن رشته‌ها و کامنت‌ها)
    stripped = re.sub(r'"""(.|\n)*?"""', '""', text)
    stripped = re.sub(r'"(\\.|[^"\\\n])*"', '""', stripped)
    # کاراکترهای تک‌کوتیشنی مانند '[' هم باید حذف شوند
    stripped = re.sub(r"'(?:\\.|[^'\\\n])'", "''", stripped)
    stripped = re.sub(r"//[^\n]*", "", stripped)
    stripped = re.sub(r"/\*(.|\n)*?\*/", "", stripped)
    for open_ch, close_ch, label in (("{", "}", "آکولاد"), ("(", ")", "پرانتز"), ("[", "]", "براکت")):
        if stripped.count(open_ch) != stripped.count(close_ch):
            add_error(
                f"عدم تعادل {label} در {rel}: {stripped.count(open_ch)} در برابر {stripped.count(close_ch)}"
            )

# ---------------------------------------------------------------- 5. internal imports
GENERATED = {"R", "BuildConfig"}
DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public |internal |private |abstract |open |sealed |data |enum |annotation |value |inline |suspend |operator |const )*"
    r"(class|interface|object|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?"
    r"(?:[A-Za-z_][A-Za-z0-9_.<>?]*\.)?([A-Za-z_][A-Za-z0-9_]*)",
    re.M,
)

symbols = defaultdict(set)
file_packages = {}
for path in kotlin_files:
    with open(path, encoding="utf-8") as f:
        text = f.read()
    rel = os.path.relpath(path, JAVA if path.startswith(JAVA) else TEST)
    pkg = os.path.dirname(rel).replace(os.sep, ".")
    file_packages[path] = pkg
    for kind, name in DECL_RE.findall(text):
        symbols[pkg].add(name)
    # فراخوانی‌های داخل بدنه هم تعریف محسوب می‌شوند (extension/property)
    for m in re.finditer(r"^(?:    |\t)?(?:fun|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*[\.(:<]", text, re.M):
        symbols[pkg].add(m.group(1))

for path in kotlin_files:
    with open(path, encoding="utf-8") as f:
        text = f.read()
    rel = os.path.relpath(path, ROOT)
    for m in re.finditer(r"^import\s+(com\.iranamap\.real[\w.]*)(\.\*)?", text, re.M):
        full = m.group(1)
        wildcard = m.group(2)
        pkg = ".".join(full.split(".")[:-1])
        name = full.split(".")[-1]
        if wildcard:
            if pkg not in symbols:
                add_error(f"بسته‌ی واردشده وجود ندارد: {pkg}.* در {rel}")
            continue
        if name in GENERATED:
            continue
        if pkg in symbols and name in symbols[pkg]:
            continue
        # ممکن است بسته‌ی تو‌در‌تو (sub-package) باشد
        if any(p.startswith(full + ".") for p in symbols):
            continue
        add_error(f"نماد واردشده وجود ندارد: {full} در {rel}")

# ---------------------------------------------------------------- 6. assets / tts
assets = os.path.join(SRC, "assets")
if not os.path.isdir(os.path.join(assets, "tts")):
    add_error("پوشه‌ی assets/tts وجود ندارد")

# ---------------------------------------------------------------- گزارش
print("=" * 72)
print("گزارش اعتبارسنجی پروژه‌ی نقشه ایران")
print("=" * 72)
print(f"فایل‌های XML سالم: {info['xml_ok']}")
print(f"فایل‌های کاتلین: {info['kotlin_files']}")
print(f"رشته‌ها: {info['strings']} | شناسه‌ها: {info['ids']} | چیدمان‌ها: {info['layouts']} | drawable: {info['drawables']}")
print("-" * 72)
if errors:
    print(f"خطاها ({len(errors)}):")
    for e in errors:
        print("  ✗", e)
else:
    print("✓ هیچ خطایی در منابع و ارجاع‌ها یافت نشد.")
if warnings:
    print(f"هشدارها ({len(warnings)}):")
    for w in warnings:
        print("  !", w)
print("=" * 72)
sys.exit(1 if errors else 0)
