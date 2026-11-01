# Rifter Log — Teknik Şartname

> `A_ANA_PROMPT.md` ile birlikte bağlayıcıdır. Mevcut Android Studio projesine (`com.asn.rifterlog`) uygulanır.
> Studio'nun ürettiği mevcut dosyalardaki (tema, ikon, `libs.versions.toml` kullanımı) yapıyı koru.

---

## 1. Dosya yapısı

```
app/src/main/java/com/asn/rifterlog/
├── RifterApp.kt                    # Application: DB, repository, TTS, ayarlar (tek örnek)
├── MainActivity.kt                 # Telefon arayüzü (Compose)
├── ui/                             # mevcut tema + HistoryScreen.kt
├── data/
│   ├── ExpenseCategory.kt          # enum + Türkçe ad + varsayılan tutarlar
│   ├── ExpenseRecord.kt            # Room entity
│   ├── ExpenseDao.kt
│   ├── AppDatabase.kt
│   ├── ExpenseRepository.kt
│   └── Settings.kt                 # SharedPreferences: notePromptEnabled
├── media/
│   ├── RifterMediaService.kt       # MediaBrowserServiceCompat + MediaSessionCompat
│   └── MediaIds.kt                 # ID şeması ve ağaç üretimi
├── notify/
│   ├── Notifier.kt                 # kanal, MessagingStyle bildirimleri
│   ├── NoteReplyReceiver.kt        # sesli not yanıtı
│   ├── AmountReplyReceiver.kt      # sesle tutar yanıtı (M5)
│   └── MarkReadReceiver.kt
├── voice/
│   ├── TtsSpeaker.kt
│   └── AmountParser.kt
└── util/
    ├── AmountFormat.kt
    └── CsvExporter.kt
app/src/main/res/xml/automotive_app_desc.xml
app/src/test/java/com/asn/rifterlog/voice/AmountParserTest.kt
```

---

## 2. Bağımlılıklar

Sürümleri Google Maven'dan doğrula, en güncel **kararlı** olanı kullan. Numara uydurma. Mevcut `gradle/libs.versions.toml` (sürüm kataloğu) varsa oraya ekle.

```kotlin
implementation("androidx.media:media:<en-guncel-kararli>")       // MediaBrowserServiceCompat, MediaSessionCompat, MediaConstants

implementation("androidx.room:room-runtime:<en-guncel-kararli>")
implementation("androidx.room:room-ktx:<en-guncel-kararli>")
ksp("androidx.room:room-compiler:<en-guncel-kararli>")

implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<en-guncel-kararli>")
implementation("androidx.core:core-ktx:<mevcut>")
```

KSP eklentisini proje ve modül `build.gradle.kts` dosyalarına ekle (Kotlin sürümüyle uyumlu KSP sürümü). `androidx.car.app` **ekleme**.

---

## 3. Manifest ve kaynaklar

`AndroidManifest.xml` (Studio'nun ürettiği `icon`, `theme`, `label` gibi mevcut öznitelikleri koru; `label` = `@string/app_name` = "Rifter Log"):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Android 11+ paket görünürlüğü: TTS motorunu bulabilmek için -->
    <queries>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
        android:name=".RifterApp"
        android:allowBackup="true"
        ... mevcut öznitelikler ...>

        <!-- Android Auto'ya bu uygulamanın medya + bildirim desteği olduğunu söyler -->
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />

        <service
            android:name=".media.RifterMediaService"
            android:exported="true">
            <intent-filter>
                <action android:name="android.media.browse.MediaBrowserService" />
            </intent-filter>
        </service>

        <receiver android:name=".notify.NoteReplyReceiver" android:exported="false" />
        <receiver android:name=".notify.AmountReplyReceiver" android:exported="false" />
        <receiver android:name=".notify.MarkReadReceiver" android:exported="false" />

        <activity android:name=".MainActivity" android:exported="true"> ... mevcut LAUNCHER filter ... </activity>
    </application>
</manifest>
```

`res/xml/automotive_app_desc.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="media" />
    <uses name="notification" />
</automotiveApp>
```

Notlar:
- `<uses name="notification" />` değerini `developer.android.com/training/cars/messaging` belgesine göre doğrula; belge farklı bir ad söylüyorsa belgeyi izle.
- `RECORD_AUDIO`, `FOREGROUND_SERVICE_*`, `MAP_TEMPLATES` gibi izinleri **ekleme**.

---

## 4. Medya öğe kimlik şeması

| ID | Tür | Anlam |
|---|---|---|
| `root` | gezilebilir | kök |
| `tab/fuel` | gezilebilir | Yakıt sekmesi |
| `tab/other` | gezilebilir | Diğer Masraf sekmesi |
| `tab/voice` | gezilebilir | Sesle Giriş sekmesi (M5) |
| `fuel/custom` | gezilebilir | Özel Tutar listesi |
| `cat/PARKING` `cat/TOLL` `cat/WASH` `cat/MAINTENANCE` `cat/OTHER` | gezilebilir | kategori klasörü |
| `add/FUEL/1000` | çalınabilir | 1000 TL yakıt kaydı ekle |
| `add/PARKING/100` | çalınabilir | 100 TL otopark kaydı ekle |
| `voice/FUEL` `voice/OTHER` | çalınabilir | tutar sorusu bildirimi gönder |

`MediaIds.kt`: `fun children(parentId: String): List<MediaBrowserCompat.MediaItem>` ve `fun parseAdd(id: String): Pair<ExpenseCategory, Long>?`.

Varsayılan tutarlar (`ExpenseCategory`):

| Kategori | Ad | Hazır tutarlar (TL) |
|---|---|---|
| FUEL | Yakıt | 500, 1000, 1500, 2000 |
| PARKING | Otopark | 50, 100, 150, 200, 300 |
| TOLL | Otoyol / HGS | 100, 200, 500, 1000 |
| WASH | Yıkama | 200, 300, 500 |
| MAINTENANCE | Bakım | 500, 1000, 2000, 5000 |
| OTHER | Diğer | 100, 250, 500, 1000, 2000 |

Özel Tutar: 100'den 3000'e 100'lük adımlar (30 öğe).

---

## 5. Servis ve oturum (`RifterMediaService`)

```kotlin
class RifterMediaService : MediaBrowserServiceCompat() {
    private lateinit var session: MediaSessionCompat
    private val main = Handler(Looper.getMainLooper())
    private var lastId: String? = null
    private var lastAt = 0L

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "RifterLog").apply {
            setCallback(callback)
            isActive = true
        }
        sessionToken = session.sessionToken
        publishIdle()
    }

    override fun onGetRoot(pkg: String, uid: Int, hints: Bundle?): BrowserRoot {
        Log.i("RifterMedia", "onGetRoot caller=$pkg")   // kişisel kullanım: hepsine izin
        val extras = Bundle().apply {
            putBoolean(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SUPPORTED, true)
            putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            putInt(MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        return BrowserRoot("root", extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(MediaIds.children(parentId))
    }
    // callback, publishIdle(), showConfirmation() aşağıda tarif edilir
}
```

Sabit adları (`MediaConstants`) `androidx.media.utils.MediaConstants` içinden doğrula; sürümde adı farklıysa dokümana göre düzelt.

### 5.1 Callback kuralları

- `onPlayFromMediaId(mediaId, extras)`:
  1. Aynı `mediaId` 3000 ms içinde tekrar geldiyse **çık**.
  2. `add/...` ise: `RifterApp.appScope.launch { id = repo.insert(...) }` → ardından `showConfirmation(...)`, `tts.speak(...)`, ayar açıksa `Notifier.postNotePrompt(recordId, ...)`.
  3. `voice/...` ise (M5): `Notifier.postAmountPrompt(category)`.
  4. Tanınmayan ID: logla, çık.
- `onPlay`, `onPause`, `onStop`, `onPrepare`, `onSkipToNext`, `onSkipToPrevious`, `onSeekTo`: **boş**. Kayıt eklemez.

### 5.2 Onay metadata'sı

```kotlin
private fun showConfirmation(title: String, subtitle: String) {
    session.setMetadata(
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)     // "Kaydedildi: 1.000 TL Yakıt"
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle) // "21:31"
            .build()
    )
    session.setPlaybackState(pausedState())
    main.removeCallbacksAndMessages(null)
    main.postDelayed({ publishIdle() }, 8000)
}

private fun pausedState() = PlaybackStateCompat.Builder()
    .setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
    .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
    .build()
```

`publishIdle()`: metadata başlığı `Rifter Log`, sanatçı `Masraf seç`, durum `pausedState()`.

Emoji veya özel simge kullanma (araç fontunda çıkmayabilir).

---

## 6. Bildirimler ve sesle giriş

### 6.1 Kanal
- Kimlik `rifter_prompts`, ad `Sürücü notu`, önem `IMPORTANCE_HIGH`. `Notifier.ensureChannel(context)`; ilk kullanımda ve `RifterApp.onCreate`'te çağrılır.

### 6.2 Not bildirimi (`Notifier.postNotePrompt(recordId, text)`)

```kotlin
val me = Person.Builder().setName("Ben").build()
val rifter = Person.Builder().setName("Rifter Log").setKey("rifter").build()

val replyPi = PendingIntent.getBroadcast(
    ctx, recordId.toInt(),
    Intent(ctx, NoteReplyReceiver::class.java).putExtra(EXTRA_RECORD_ID, recordId),   // AÇIK (explicit) intent
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE                   // RemoteInput için MUTABLE şart
)
val replyAction = NotificationCompat.Action.Builder(R.drawable.ic_reply, "Yanıtla", replyPi)
    .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Not").build())
    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
    .setShowsUserInterface(false)
    .setAllowGeneratedReplies(false)
    .build()

val readAction = NotificationCompat.Action.Builder(R.drawable.ic_check, "Okundu", markReadPi)
    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
    .setShowsUserInterface(false)
    .build()

val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_notification)
    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setStyle(NotificationCompat.MessagingStyle(me).addMessage(text, System.currentTimeMillis(), rifter))
    .addAction(replyAction)      // Android Auto için Yanıtla ve Okundu eylemleri ZORUNLU
    .addAction(readAction)
    .setAutoCancel(true)
    .build()
NotificationManagerCompat.from(ctx).notify(NOTE_NOTIF_BASE + recordId.toInt(), n)   // izin kontrolü yap
```

`R.drawable.ic_reply`, `ic_check`, `ic_notification`: basit vektör ikonlar oluştur (küçük ikon tek renk beyaz siluet).

Bildirim izni yoksa (`areNotificationsEnabled()` false) sessizce atla ve logla; kayıt zaten yazılmıştır.

### 6.3 `NoteReplyReceiver`
1. `RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)` ile metni al. Boşsa çık.
2. `goAsync()` ile: `repo.updateNote(recordId, text)` (mevcut not varsa `"$eski; $yeni"`), sonra bildirimi **güncelle** (aynı ID, MessagingStyle'a kullanıcı yanıtını `me` göndericisiyle ekle; bu, bildirimdeki dönen göstergeyi kapatır).
3. `tts.speak("Not eklendi.")`, ardından bildirimi kapat (`cancel`).
4. `pendingResult.finish()`.

### 6.4 `MarkReadReceiver`
- Ekstradaki bildirim ID'sini `cancel` et. Başka işlem yok.

### 6.5 Sesle tutar sorusu (M5)
- `Notifier.postAmountPrompt(category)`: mesaj `Tutar kaç TL?`, aynı yapı, eylem `AmountReplyReceiver`'a gider (ekstra: kategori).
- `AmountReplyReceiver`: metni `AmountParser.parse()` ile çevir.
  - Sonuç 50..50000 ise: kayıt ekle → onay metadata'sı için servise bilgi verme gerekmez; sadece TTS `1500 lira yakıt kaydedildi.` + ayar açıksa not bildirimi.
  - Sonuç yoksa: TTS `Tutarı anlayamadım. Tekrar söyleyin.` ve soru bildirimini yeniden gönder.

---

## 7. `AmountParser` (`voice/AmountParser.kt`)

`fun parse(input: String): Long?`

Kurallar:
1. Küçük harfe çevir (`tr` yerel ayarıyla), `tl`, `₺`, `lira` kelimelerini at, boşlukları kırp.
2. **Rakam yolu:** metinde rakamlar varsa: `1.350` → 1350 (nokta binlik ayraç), `1,5` bin gibi ondalıklı ifadeleri **reddet** (null). Çıkan sayıyı döndür.
3. **Kelime yolu** (Türkçe): birler (bir…dokuz), onlar (on…doksan), `yüz`, `bin`, `milyon` kullanma. `yüz`: önündeki çarpan yoksa 1. `bin`: önündeki çarpan yoksa 1, çarpan 1..999.
4. Çözülemeyen kelime varsa `null`.

Birim testleri (`AmountParserTest`) en az şunları doğrular:

| Girdi | Sonuç |
|---|---|
| `1000` | 1000 |
| `1.350 lira` | 1350 |
| `bin` | 1000 |
| `bin beş yüz` | 1500 |
| `iki bin` | 2000 |
| `yüz elli` | 150 |
| `seksen` | 80 |
| `on bin` | 10000 |
| `iki bin üç yüz kırk beş` | 2345 |
| `merhaba` | null |

---

## 8. TTS (`TtsSpeaker`)

- `RifterApp.onCreate` içinde oluşturulur: `TextToSpeech(appContext, listener)`. Hazır olunca `Locale("tr", "TR")` ayarla; `isLanguageAvailable` başarısızsa `available = false` (sessizce çalışmaz).
- Hazır olmadan gelen metinler kuyruğa alınır, hazır olunca okunur.
- Ses özellikleri:

```kotlin
tts.setAudioAttributes(
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
)
```

- Konuşmadan önce `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` ile ses odağı iste; `UtteranceProgressListener.onDone` / `onError` içinde bırak.
- API: `fun speak(text: String, onDone: () -> Unit = {})`.

---

## 9. Veri katmanı

```kotlin
@Entity(tableName = "expense_records")
data class ExpenseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,            // ExpenseCategory.name
    val amountTl: Long,
    val createdAtMillis: Long,
    val driverNote: String? = null
)
```

DAO:
- `insert(record): Long`
- `updateNote(id: Long, note: String?)`
- `observeAll(): Flow<List<ExpenseRecord>>` (en yeni üstte)
- `getAll(): List<ExpenseRecord>` (CSV için)
- `sumBetween(startMillis: Long, endMillis: Long): Long?`

Veritabanı: `version = 1`, `exportSchema = true`, `fallbackToDestructiveMigration` **yok**. Şema değişirse yazılı `Migration`.

`RifterApp`:

```kotlin
class RifterApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)   // servis kapansa bile yazımlar tamamlanır
    lateinit var db: AppDatabase; lateinit var repo: ExpenseRepository
    lateinit var tts: TtsSpeaker; lateinit var settings: Settings
}
```

Servis ve receiver'lar `(applicationContext as RifterApp)` üzerinden erişir.

---

## 10. CSV (`CsvExporter`)

- `ActivityResultContracts.CreateDocument("text/csv")` ile kullanıcı yeri seçer.
- UTF-8, başta BOM (`\uFEFF`), ayraç `;`.
- Başlık: `id;tarih_saat;kategori;tutar_tl;surucu_notu`
- Tarih: `yyyy-MM-dd HH:mm:ss`, `Europe/Istanbul`. Kategori Türkçe adıyla.
- Not alanında `;`, tırnak veya satır sonu varsa alanı çift tırnağa al, içteki tırnakları ikile.

---

## 11. Sağlamlık kuralları

1. **Önce kaydet, sonra ses/bildirim.** Kayıt yazılmadan onay verilmez.
2. Hiçbir hata kaydı kaybettirmez. TTS, bildirim izni, parser hataları sadece ilgili adımı atlar.
3. `RifterApp.appScope` dışında uzun yaşayan coroutine bırakma; servisteki `Handler` callback'lerini `onDestroy`'da temizle.
4. `applicationId` (`com.asn.rifterlog`) ve imza anahtarı proje boyunca değişmez.
5. Servis `onGetRoot` içinde ve `onPlayFromMediaId` içinde her zaman `Log.i("RifterMedia", ...)` yazar (araç testinde `adb logcat` ile izlenecek).
