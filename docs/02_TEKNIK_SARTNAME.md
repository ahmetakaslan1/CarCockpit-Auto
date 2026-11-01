# Rifter Yol Arkadaşım — Teknik Şartname

> `01_PROJE_PROMPTU.md` ile birlikte bağlayıcıdır. Buradaki manifest ve yapı olduğu gibi uygulanır.

---

## 1. Proje yapısı

```
app/src/main/
├── AndroidManifest.xml
├── java/com/rifter/yolarkadasim/
│   ├── MainActivity.kt                 # Telefon arayüzü (Compose)
│   ├── data/
│   │   ├── ExpenseCategory.kt          # enum: FUEL, PARKING, TOLL, WASH, MAINTENANCE, OTHER
│   │   ├── ExpenseRecord.kt            # Room entity
│   │   ├── ExpenseDao.kt
│   │   ├── AppDatabase.kt
│   │   └── ExpenseRepository.kt
│   ├── car/
│   │   ├── RifterCarAppService.kt      # CarAppService
│   │   ├── RifterSession.kt            # Session, TTS yaşam döngüsü
│   │   ├── HomeScreen.kt               # GridTemplate
│   │   ├── CustomAmountScreen.kt       # GridTemplate (+/− butonları)
│   │   ├── CategoryScreen.kt           # ListTemplate
│   │   └── VoiceNoteScreen.kt          # MessageTemplate
│   ├── voice/
│   │   ├── TtsSpeaker.kt               # TextToSpeech sarmalayıcı
│   │   └── SpeechNoteRecognizer.kt     # SpeechRecognizer sarmalayıcı
│   └── util/
│       ├── AmountFormat.kt             # 1000 -> "1.000 TL"
│       └── CsvExporter.kt
└── res/
    ├── xml/automotive_app_desc.xml
    ├── drawable/                       # basit vektör ikonlar: ic_fuel, ic_edit, ic_more, ic_plus, ic_minus, ic_check
    └── values/strings.xml
```

---

## 2. Bağımlılıklar

Sürüm numaralarını Google Maven'dan doğrulayıp **en güncel kararlı** sürümü kullan. Numara uydurma.

```kotlin
// Car App Library — araç ekranı şablonları
implementation("androidx.car.app:app:<en-guncel-kararli>")

// Room + KSP
implementation("androidx.room:room-runtime:<en-guncel-kararli>")
implementation("androidx.room:room-ktx:<en-guncel-kararli>")
ksp("androidx.room:room-compiler:<en-guncel-kararli>")

// Coroutines ve lifecycle (Screen içinde lifecycleScope için)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<en-guncel-kararli>")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:<en-guncel-kararli>")
```

Compose için Android Studio "Empty Activity" şablonunun getirdiği BOM ve bağımlılıklar aynen kalır. KSP eklentisini proje ve modül `build.gradle.kts` dosyalarına ekle.

---

## 3. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Android 11+ paket görünürlüğü: konuşma tanıma ve TTS motorunu bulabilmek için -->
    <queries>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.RifterYolArkadasim">

        <!-- Android Auto'ya "bu uygulama şablon uygulamasıdır" der -->
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />

        <meta-data
            android:name="androidx.car.app.minCarApiLevel"
            android:value="1" />

        <!-- KRİTİK: iki kategori TEK intent-filter içinde olmalı. Ayrı iki filter yazılırsa
             Android Auto uygulamayı araç listesinde göstermeyebilir. -->
        <service
            android:name=".car.RifterCarAppService"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.POI" />
                <category android:name="androidx.car.app.category.IOT" />
            </intent-filter>
        </service>

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/xml/automotive_app_desc.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="template" />
</automotiveApp>
```

Notlar:
- `NAVIGATION_TEMPLATES`, `MAP_TEMPLATES`, `ACCESS_SURFACE` izinlerini **ekleme**.
- Manifestteki `uses-feature` girdilerini Car App Library "Set up your project" belgesine göre kontrol et; belgede zorunlu olmayanı ekleme.

---

## 4. Sınıf sorumlulukları

### 4.1 `RifterCarAppService : CarAppService`
- `createHostValidator()` → `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` (yorum: kişisel kullanım).
- `onCreateSession()` → `RifterSession()`.

### 4.2 `RifterSession : Session`
- `onCreateScreen(intent)` → `HomeScreen`.
- Oturum başında `TtsSpeaker` oluşturur; `Lifecycle` `DESTROYED` olunca kapatır.
- Ekranlara `TtsSpeaker` ve `ExpenseRepository` bu sınıftan verilir (basit manuel bağımlılık, DI kütüphanesi ekleme).

### 4.3 Ekranlar (`Screen`)
- Tüm veritabanı işlemleri `lifecycleScope.launch` içinde, `Dispatchers.IO`.
- Durum değişince `invalidate()` çağır.
- Çift dokunmayı engelle: kayıt yazılırken `isSaving` bayrağı ile ikinci dokunuşu yok say.
- İkonlar: `CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_...)).build()`, her `GridItem` için ikon ver.
- Öğe sınırı: `carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)` (liste için `..._LIST`).

### 4.4 `ExpenseRecord` (Room entity)

```kotlin
@Entity(tableName = "expense_records")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,          // ExpenseCategory.name
    val amountTl: Long,
    val createdAtMillis: Long,
    val driverNote: String? = null
)
```

DAO en az şunları içerir:
- `insert(record): Long` (id döner)
- `updateNote(id: Long, note: String?)`
- `observeAll(): Flow<List<ExpenseRecord>>` (en yeni üstte)
- `getAll(): List<ExpenseRecord>` (CSV için)
- `sumBetween(startMillis, endMillis): Long?` (aylık toplam)

Veritabanı: `version = 1`, `exportSchema = true`, `fallbackToDestructiveMigration` **yok**.

### 4.5 `TtsSpeaker`
- `TextToSpeech(context)` başlatır, `Locale("tr", "TR")` ayarlar.
- `speak(text, onDone)`: `AudioAttributes` (navigasyon yönlendirme + konuşma), ses odağı iste → `UtteranceProgressListener.onDone` içinde odağı bırak ve `onDone()` çağır.
- Dil desteklenmiyorsa `onDone()`'ı hemen çağır (sessizce devam).
- `shutdown()`.

### 4.6 `SpeechNoteRecognizer`
- `SpeechRecognizer.isRecognitionAvailable(context)` kontrolü; yoksa hemen `onResult(null)`.
- `SpeechRecognizer` ana iş parçacığında oluşturulur ve kullanılır.
- Intent:

```kotlin
Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
}
```

- 10 saniyelik sert zaman aşımı: dolunca `cancel()` ve `onResult(null)`.
- `onResults` → ilk sonuç. Herhangi `onError` → `onResult(null)`.
- Sonuç bir kez teslim edilir (çift callback koruması). `destroy()` ile temizlenir.

### 4.6.1 İzin
- `RECORD_AUDIO` izni yoksa `VoiceNoteScreen` dinlemeyi başlatmaz, doğrudan TTS onayına geçer.
- İzin telefonda `MainActivity` açılışında istenir.

### 4.7 `CsvExporter`
- Başlık: `id;tarih_saat;kategori;tutar_tl;surucu_notu`
- UTF-8 BOM (`\uFEFF`) ile başla. Noktalı virgül veya satır sonu içeren notları çift tırnağa al, içteki tırnakları ikile.

### 4.8 `AmountFormat`
- `1000` → `1.000 TL` (Türkçe biçim, sabit bir fonksiyon, yerel ayardan bağımsız).

---

## 5. Kategori adları (arayüz metni)

| Kod | Metin |
|---|---|
| FUEL | Yakıt |
| PARKING | Otopark |
| TOLL | Otoyol / HGS |
| WASH | Yıkama |
| MAINTENANCE | Bakım |
| OTHER | Diğer |

---

## 6. Sağlamlık kuralları

1. **Önce kaydet, sonra ses.** Kayıt veritabanına yazılmadan ses adımına geçilmez.
2. Hiçbir hata kaydı kaybettirmez. STT, TTS, izin ve tanıma hatalarında kayıt durur, sadece not ve/veya onay eksik kalır.
3. `SpeechRecognizer` ve `TextToSpeech` her zaman serbest bırakılır (`onDestroy` / Lifecycle).
4. Uygulama hiçbir ağ isteği yapmaz (STT'nin Google servisine gitmesi sistem davranışıdır, uygulama kodu bağlantı açmaz).
5. Uygulama kimliği ve imza anahtarı proje boyunca değişmez (bkz. kılavuz).
