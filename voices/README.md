# صدای پایه (Base Phrases)

- **base_phrases.json**: 51 عبارت ثابت تکراری (مانورها، چراغ، سرعت، رسیدن)
- هر عبارت: `voices/{anahita,arash}/base/{id}.mp3`
- استاندارد Anahita-Cut: 128k mono 44.1kHz، وقفه 120-320ms
- تولید: `base_for_elevenlabs_with_pause.txt` → ElevenLabs → tts_process_anahita.py → اینجا

## موتور ترکیبی (Hybrid)
- اگر فایل لکسیکون موجود باشد: مستقیم پخش (کیفیت استودیویی)
- اگر نه: fallback به Sherpa TTS (Amir)
- کلاس: `HybridSpeechEngine` + `LexiconRepository` - در تمام App استفاده می‌شود

## وضعیت
- Anahita: شیراز پارت 1 خیابان‌ها (751s مات) ✅
- Arash: batvh v2/v3 (222-261s) ✅ - نیاز به split به 51 فایل با `scripts/split_base_voices.py`
- Base 51 عبارت: آماده برای تولید (2733 chars = $0.49 برای 1 صدا)
