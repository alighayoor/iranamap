# ادغام در اپ - چگونه مثل نشان حرفه‌ای‌تر شویم

## استفاده در MapActivity

```kotlin
// به جای TTS زنده برای خیابان‌ها:
val lexicon = LexiconRepository.get(context) // از docs/tts-lexicon/*.json لود می‌شود
val entry = lexicon.findByFullName("بلوار کشاورز")
if (entry != null) {
    // 100% غیرتکراری - فایل آماده آناهیتا/آرش
    val voiceFile = if (settings.voiceId == "anahita") entry.voiceFiles.anahita else entry.voiceFiles.arash
    announcer.playPreRendered(voiceFile) // بدون TTS، فقط پخش MP3
} else {
    // fallback: TTS زنده
    announcer.announce(planner.onStreetName(streetName))
}
```

## مزیت نسبت به نشان
- نشان: `TextToSpeech.speak("بلوار کشاورز")` → هر بار سنتز رباتیک
- ما: `MediaPlayer.create("tehran_blv_keshavarz_anahita.mp3")` → استودیویی، یک بار ضبط

## گام بعدی
1. تولید 48 فایل نمونه با Piper (فعلاً نمونه)
2. تست در اپ
3. گسترش به 25,000 نام با اسکریپت دسته‌ای
