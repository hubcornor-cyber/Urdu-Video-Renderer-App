# Urdu Video Timeline Renderer (Native Android Jetpack Compose)

یک جدید اور اعلیٰ کارکردگی کا حامل اینڈرائیڈ سسٹم جو JSON منظر نامے (Scene Timeline) اور تصویری اثاثہ جات کو اصل وقت میں 30 فریم فی سیکنڈ کی رفتار سے MP4 ویڈیو میں رینڈر کرتا ہے۔

A native Android app built with **Kotlin + Jetpack Compose** that renders high-definition videos (1080x1920) from a JSON scene timeline and local image assets using hardware-accelerated **Canvas**, **MediaCodec (H.264)**, and **MediaMuxer**.

---

## 📱 اہم خصوصیات (Key Features)

1. **Home Screen (ہوم اسکرین)**:
   - **Load JSON (جے ایس او این لوڈ کریں)**: ڈیوائس اسٹوریج سے JSON ٹائم لائن منتخب کرنے کے لیے سسٹم فائل پکر۔
   - **Select Assets Folder (اثاثہ جات فولڈر منتخب کریں)**: پس منظر (Backgrounds) اور کرداروں (Characters) کی تصاویر کا فولڈر چننے کے لیے Storage Access Framework (SAF) ٹری پکر۔
   - **Duration & Scenes Counter**: کل دورانیہ اور مناظر کی تعداد کا خودکار حساب۔
   - **Preview Timeline (پیش نظارہ)**: 30 FPS انٹرایکٹو کینوس پلیئر کھولنے کا بٹن۔
   - **Render & Export MP4 (ویڈیو برآمد کریں)**: پس منظر تھریڈ پر مکمل ویڈیو رینڈر کر کے Downloads میں `rendered_video.mp4` محفوظ کرنے کا نظام بمع پروگریس بار۔

2. **Renderer Screen (کینوس رینڈرر اسکرین)**:
   - **Real-time 30 FPS Canvas**: فریم بہ فریم کیمرہ پین (Pan) اور زوم (Zoom) کی انٹرپولیشن (Interpolation)۔
   - **Character Transform**: کردار کی پوزیشن، اسکیل (Scale)، گردش (Rotation)، اور دھندلے پن سے واضح ہونے کا اثر (Fade-in entrance)۔
   - **Urdu Captions (RTL)**: دائیں سے بائیں خوبصورت نستعلیق اردو کیپشنز مع ایمبر/گولڈ گلو اور ڈارک بیک ڈراپ۔
   - **Overlay HUD**: رواں وقت اور فعال منظر نامے کا ٹائم اسٹیمپ۔

3. **MediaCodec Hardware Video Export**:
   - `MediaCodec` (H.264 / AVC) + `MediaMuxer`۔
   - JSON کی سیٹنگز کے مطابق ریزولیوشن (ڈیفالٹ: `1080x1920` عمودی / Vertical 9:16)۔
   - `MediaStore.Downloads` میں برآمد شدہ MP4 فائل کا خودکار اندراج۔

---

## 🚀 گٹ ہب ایکشنز سے APK بنانے کا طریقہ (Build APK with GitHub Actions)

یہ پروجیکٹ مکمل گریڈل ریپر (Gradle 8.9 Wrapper) اور گٹ ہب ایکشنز ورک فلو (`.github/workflows/android.yml`) کے ساتھ لیس ہے۔

### مرحلہ 1: پروجیکٹ زپ ڈاؤن لوڈ کریں (Download ZIP)
- AI Studio کے اوپر دائیں کونے یا ویب پریویو میں موجود **"Download Android Project (.ZIP)"** بٹن پر کلک کریں۔

### مرحلہ 2: نیا گٹ ہب ریپازٹری بنائیں (Push to GitHub)
اپنے ٹرمینل میں درج ذیل کمانڈز چلائیں:
```bash
git init
git add .
git commit -m "Initial commit: Urdu Video Renderer Android App"
git branch -M main
git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO_NAME>.git
git push -u origin main
```

### مرحلہ 3: GitHub Actions ورک فلو خودکار چلے گا (Run Workflow)
- اپنی گٹ ہب ریپازٹری کے **Actions** ٹیب پر جائیں۔
- **Build Android APK** ورک فلو خود بخود شروع ہو جائے گا۔ آپ چاہیں تو **Run workflow** بٹن پر کلک کر کے دستی طور پر بھی چلا سکتے ہیں۔
- ورک فلو JDK 17 سیٹ اپ کرے گا اور `./gradlew assembleDebug` چلا کر APK تیار کرے گا۔

### مرحلہ 4: تیار شدہ APK ڈاؤن لوڈ کریں (Download APK Artifact)
- ورک فلو مکمل ہونے پر سبز نشان (Success) ظاہر ہوگا۔
- صفحے کے نچلے حصے میں **Artifacts** سیکشن میں جائیں۔
- **`app-debug-apk`** پر کلک کر کے زپ فائل ڈاؤن لوڈ کریں۔ اس میں `app-debug.apk` موجود ہوگا۔

### مرحلہ 5: موبائل میں انسٹال کریں (Install on Android Device)
- ڈاؤن لوڈ کی گئی `app-debug.apk` کو اپنے اینڈرائیڈ فون میں بھیجیں۔
- APK فائل پر ٹیپ کر کے انسٹال کریں۔ (اگر فون پوچھے تو "Install Unknown Apps" کی اجازت دیں)۔
- ایپ کھولیں اور JSON ٹائم لائن لوڈ کر کے رینڈرنگ شروع کریں!

---

## 💻 مقامی طور پر چلانے کا طریقہ (Local Build in Android Studio / CLI)

اگر آپ اپنے کمپیوٹر پر چلانا چاہتے ہیں:
```bash
# قابل عمل اجازت دیں
chmod +x gradlew

# ڈیبگ APK بنائیں
./gradlew assembleDebug

# تیار شدہ APK یہاں ملے گی:
# app/build/outputs/apk/debug/app-debug.apk
```

یا **Android Studio** کھول کر **Open an existing project** منتخب کریں اور اس ڈائریکٹری کو کھولیں۔

---

## 📂 JSON ٹائم لائن اسکیما (JSON Schema)

```json
{
  "scenes": [
    {
      "start": 0,
      "end": 4,
      "label": "Scene 1: نئی امید",
      "background": "assets/bg-1.jpg",
      "character": "assets/character-1.png",
      "captionStyle": "hook",
      "strongWords": ["نئی", "امید"],
      "showText": true,
      "camera": {
        "effect": "custom",
        "startX": 0.5,
        "endX": 0.5,
        "startY": 0.62,
        "endY": 0.42,
        "startZoom": 1.0,
        "endZoom": 1.15
      },
      "characterTransform": {
        "startX": 0.52,
        "endX": 0.49,
        "startY": 0.78,
        "endY": 0.78,
        "startScale": 1.03,
        "endScale": 1.25,
        "startRotation": 0.0,
        "endRotation": 2.0,
        "entrance": "fade-in",
        "opacity": 1.0
      }
    }
  ],
  "settings": {
    "resolution": "1080x1920",
    "fps": "30",
    "quality": "8000000",
    "format": "mp4",
    "voiceVolume": "1.0",
    "musicVolume": "0.25"
  }
}
```
