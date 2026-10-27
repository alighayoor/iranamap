#!/usr/bin/env python3
"""
Fetch Lexicon - دریافت لیست کامل بلوارها، میدان‌ها، خیابان‌ها از OSM
برای 100% متن به گفتار غیرتکراری - حرفه‌ای‌تر از نشان

Usage:
    python scripts/fetch_lexicon.py --city tehran --limit 100
    python scripts/fetch_lexicon.py --all
"""
import requests, json, re, time, pathlib, argparse
from collections import defaultdict

CITIES = {
    "tehran": {"name": "تهران", "lat": 35.6892, "lng": 51.3890, "radius": 35000},
    "mashhad": {"name": "مشهد", "lat": 36.2970, "lng": 59.6062, "radius": 25000},
    "isfahan": {"name": "اصفهان", "lat": 32.6546, "lng": 51.6680, "radius": 24000},
    "shiraz": {"name": "شیراز", "lat": 29.5918, "lng": 52.5836, "radius": 20000},
    "tabriz": {"name": "تبریز", "lat": 38.0800, "lng": 46.2919, "radius": 20000},
    "karaj": {"name": "کرج", "lat": 35.8355, "lng": 50.9915, "radius": 20000},
}

TYPE_MAP = {
    "بلوار": ["بلوار", "boulevard"],
    "میدان": ["میدان", "square"],
    "خیابان": ["خیابان", "street"],
    "بزرگراه": ["بزرگراه", "highway", "آزادراه"],
    "کوچه": ["کوچه", "alley"],
}

def normalize_persian(text):
    # ساده‌سازی PersianText.normalize
    replacements = {"ي": "ی", "ك": "ک", "ة": "ه", "ۀ": "ه"}
    for k,v in replacements.items():
        text = text.replace(k,v)
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def fetch_city(city_key, limit=5000):
    cfg = CITIES[city_key]
    print(f"\n📍 دریافت {cfg['name']} (شعاع {cfg['radius']/1000:.0f}km)...")
    # Overpass Query: همه highway با name در شعاع شهر
    query = f"""
    [out:json][timeout:60];
    (
      way["highway"]["name"](around:{cfg['radius']},{cfg['lat']},{cfg['lng']});
      way["highway"]["name:fa"](around:{cfg['radius']},{cfg['lat']},{cfg['lng']});
      node["place"="square"]["name"](around:{cfg['radius']},{cfg['lat']},{cfg['lng']});
      way["junction"]["name"](around:{cfg['radius']},{cfg['lat']},{cfg['lng']});
    );
    out body;
    >;
    out skel qt;
    """
    try:
        r = requests.post("https://overpass-api.de/api/interpreter", data={"data": query}, timeout=70)
        r.raise_for_status()
        data = r.json()
        elements = data.get("elements", [])
        print(f"  دریافت خام: {len(elements)} المان")
        # استخراج نام یکتا
        names = {}
        for el in elements:
            tags = el.get("tags", {})
            name = tags.get("name:fa") or tags.get("name")
            if not name: continue
            name = normalize_persian(name)
            if len(name) < 3: continue
            highway = tags.get("highway", "")
            # تشخیص نوع
            typ = "خیابان"
            if "بلوار" in name or highway in ["primary","trunk"]:
                typ = "بلوار"
            elif "میدان" in name or tags.get("place")=="square" or "junction" in tags:
                typ = "میدان"
            elif "بزرگراه" in name or "آزادراه" in name:
                typ = "بزرگراه"
            elif "کوچه" in name:
                typ = "کوچه"
            if name not in names:
                names[name] = {
                    "id": f"{city_key}_{re.sub(r'[^\\w]', '_', name)[:40]}",
                    "city": cfg["name"],
                    "cityKey": city_key,
                    "type": typ,
                    "name": name.replace("بلوار ","").replace("میدان ","").replace("خیابان ","").strip(),
                    "fullName": name,
                    "normalized": normalize_persian(name),
                    "forSpeech": name.replace("خ.", "خیابان ").replace("بل.", "بلوار ").replace("مید.", "میدان "),
                    "osmId": el.get("id"),
                    "lat": el.get("lat", cfg["lat"]),
                    "lng": el.get("lon", cfg["lng"]),
                    "voiceFiles": {
                        "anahita": f"voices/anahita/{city_key}/{re.sub(r'[^\\w]', '_', name)[:40]}.mp3",
                        "arash": f"voices/arash/{city_key}/{re.sub(r'[^\\w]', '_', name)[:40]}.mp3"
                    }
                }
                if len(names) >= limit:
                    break
        print(f"  یکتاسازی: {len(names)} نام")
        return list(names.values())
    except Exception as e:
        print(f"  خطا: {e}")
        # fallback: لیست نمونه اگر Overpass فیلتر باشد
        return generate_sample(city_key, limit=50)

def generate_sample(city_key, limit=50):
    cfg = CITIES[city_key]
    samples = {
        "tehran": ["بلوار کشاورز","میدان آزادی","خیابان ولیعصر","بزرگراه همت","خیابان انقلاب","میدان ونک","بلوار فردوس","خیابان شریعتی","بزرگراه مدرس","میدان تجریش"],
        "shiraz": ["بلوار زند","میدان نماز","خیابان عفیف آباد","بلوار چمران","میدان گاز"],
        "isfahan": ["خیابان چهارباغ","میدان نقش جهان","بلوار کشاورز اصفهان"],
    }
    base = samples.get(city_key, samples["tehran"])
    result=[]
    for i,name in enumerate(base[:limit]):
        result.append({
            "id": f"{city_key}_{i:04d}",
            "city": cfg["name"],
            "cityKey": city_key,
            "type": "خیابان",
            "name": name,
            "fullName": name,
            "normalized": name,
            "forSpeech": name,
            "osmId": 100000+i,
            "lat": cfg["lat"]+i*0.001,
            "lng": cfg["lng"]+i*0.001,
            "voiceFiles": {"anahita": f"voices/anahita/{city_key}/{i:04d}.mp3", "arash": f"voices/arash/{city_key}/{i:04d}.mp3"}
        })
    return result

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--city", choices=CITIES.keys(), help="یک شهر")
    ap.add_argument("--all", action="store_true", help="همه شهرها")
    ap.add_argument("--limit", type=int, default=5000)
    ap.add_argument("--outdir", default="docs/tts-lexicon")
    args = ap.parse_args()
    targets = list(CITIES.keys()) if args.all else [args.city or "tehran"]
    outdir = pathlib.Path(args.outdir)
    outdir.mkdir(parents=True, exist_ok=True)
    total=0
    for ck in targets:
        items = fetch_city(ck, limit=args.limit)
        path = outdir / f"{ck}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(items, f, ensure_ascii=False, indent=2)
        print(f"  ✓ ذخیره: {path} ({len(items)} مورد)")
        total+=len(items)
        time.sleep(2)
    # ایندکس کلی
    index = {k: {"name": CITIES[k]["name"], "file": f"{k}.json", "count": len(json.load(open(outdir/f"{k}.json", encoding="utf-8")))} for k in targets}
    with open(outdir/"index.json","w",encoding="utf-8") as f:
        json.dump(index,f,ensure_ascii=False,indent=2)
    print(f"\n✅ جمع کل: {total} نام در {len(targets)} شهر - حرفه‌ای‌تر از نشان!")

if __name__ == "__main__":
    main()
