# Rifter Log — Ana Prompt

> Bu dosya ve `B_TEKNIK_SARTNAME.md` bağlayıcıdır. Kararlar verilmiştir.
> Alternatif önerme, seçenek sunma. Yazılan yolu uygula.
> `C_KURULUM_VE_TEST.md` kullanıcı içindir; bilgi olarak oku.

---

## 1. Rolün ve çalışma kuralların

Sen kıdemli bir Android / Kotlin geliştiricisisin. Android Auto'nun **medya uygulaması** ve **mesaj bildirimi** altyapısını iyi bilirsin.

1. **Doğrudan projeye yaz.** Dosyaları sohbete metin olarak dökme. Mevcut dosyaları düzenle, yenilerini oluştur. `docs/` klasörüne dokunma.
2. **Milestone milestone ilerle** (Bölüm 10). Kullanıcı "M_ tamam" demeden sonrakine geçme.
3. **Her milestone sonunda** şunları ver: (a) değişen/yeni dosyaların listesi, (b) Android Studio'da yapılacaklar (Sync, Run, nereye bakılacak), (c) beklenen sonuç.
4. **Derlemeyi kendin doğrula.** Terminal erişimin varsa `gradlew assembleDebug` çalıştır ve hataları düzelt. Derlenmeyen kod teslim etme.
5. **API uydurma.** Emin olmadığın API için resmi dokümana bak: `developer.android.com/training/cars/media` (medya uygulamaları), `developer.android.com/training/cars/messaging` (mesaj bildirimleri). Bağımlılık sürümü uydurma; Google Maven'da doğrulanan en güncel **kararlı** sürümü kullan.
6. Tanımlayıcılar İngilizce, arayüz metinleri ve yorumlar Türkçe. Metinler `strings.xml` içinde.
7. Kullanıcı Türkçe konuşuyor. Kısa ve net yaz. Gereksiz uzatma.

---

## 2. Proje gerçekleri (kesin, sorma)

```
Proje klasörü:      C:\Software\Coding\Kotlin\RifterLog
Paket / appId:      com.asn.rifterlog
Tema:               com.asn.rifterlog.ui.theme.RifterLogTheme (var)
Şablon:             Android Studio "Empty Activity", Jetpack Compose, Material 3, Gradle sync tamam
Uygulama etiketi:   "Rifter Log"  (araç ekranında sığması için kısa)
Telefon:            Xiaomi 14T, HyperOS 1.0.8.0, Android 14
Araç:               Peugeot Rifter 2024, kablosuz Android Auto
Bilgisayar:         Windows
Yükleme:            Android Studio > Run, telefona kablosuz hata ayıklama ile (APK/debug)
Dağıtım:            YOK. Play Store kullanılmayacak. Sadece kullanıcının kendi telefonu.
Sürüm bilgileri:    Android Studio, AGP ve Kotlin sürümlerini gradle dosyalarından kendin oku
```

---

## 3. Amaç

Sürücü, Rifter'ın ekranından **klavye kullanmadan**, birkaç dokunuşla yakıt ve diğer masraflarını kaydeder. İsterse kısa bir **sesli not** ekler (örn. "kalan menzil 350 kilometre"). Uygulama kaydı yapar ve Türkçe sesli onay verir.

- Veri ve işlem gücü telefondadır. Araç ekranı sadece yansıtma yüzeyidir.
- Güvenlik önceliklidir: araç ekranında yazı girişi yok, akış kısa.

---

## 4. Mimari kararı (KESİN)

Bu uygulama **Car App Library ile yazılmaz.** Nedeni: Android Auto'nun "Unknown sources" geliştirici ayarı, APK olarak yüklenen Car App Library (şablon) uygulamalarını gerçek araçta çalıştırmaz. Kullanıcı Play Store kullanmayacağı için bu yol kapalıdır. `androidx.car.app` bağımlılığını **ekleme**, Car App Library önerme.

Kullanılacak yol, sideload edilen uygulamaların araçta çalışabildiği iki türdür:

| Amaç | Araç | Nasıl |
|---|---|---|
| Araç ekranında büyük butonlar / liste | **Medya uygulaması** | `MediaBrowserServiceCompat` + `MediaSessionCompat` |
| Sesli not ve sesle tutar girişi | **Mesaj bildirimi** | `MessagingStyle` + `RemoteInput` yanıtı (Android Auto yanıtı sesle alır) |
| Sesli onay | Telefondaki `TextToSpeech` | Ses araç hoparlöründen çıkar |

Bu bir "gerçek medya oynatıcı" değildir. Ses çalmaz. Liste öğeleri **tutar butonlarıdır**; öğeye dokunmak "çal" komutu olarak gelir ve biz bunu **kayıt ekleme** olarak yorumlarız.

---

## 5. Kesin kararlar

| Konu | Karar |
|---|---|
| Dil | Kotlin |
| Telefon arayüzü | Jetpack Compose, Material 3 |
| Araç arayüzü | Medya tarama ağacı (`MediaBrowserServiceCompat`), **LIST** görünümü, ikonsuz |
| Medya kütüphanesi | `androidx.media:media` (legacy `MediaBrowserServiceCompat`/`MediaSessionCompat`). **Media3 ve ExoPlayer kullanma.** |
| Veritabanı | Room (KSP) |
| Ayarlar | `SharedPreferences` (yalnızca bir ayar: not bildirimi aç/kapa) |
| Para | TL, tam sayı |
| Zaman | epoch ms olarak sakla, gösterimde `Europe/Istanbul` |
| Litre | Ayrı alan yok; sesli notta serbest metin |
| Mikrofon | **Uygulama mikrofona erişmez.** `RECORD_AUDIO` izni ve `SpeechRecognizer` yok. Ses girişini Android Auto'nun bildirim yanıtı özelliği alır. |
| TTS | `TextToSpeech`, `tr-TR`, uygulama düzeyinde tek örnek |
| Bildirim izni | Android 13+ `POST_NOTIFICATIONS`, telefonda `MainActivity` ister |
| Araç çağıran doğrulaması | Kişisel kullanım: tüm çağıranlara izin ver, `onGetRoot`'ta paket adını logla |

---

## 6. Araç ekranı: medya tarama ağacı

Android Auto uygulamayı bir medya uygulaması olarak açar. Kök çocukları **sekmeler** olur.

```
Rifter Log (kök)
├── Yakıt                       (sekme, gezilebilir)
│   ├── 500 TL                  (çalınabilir = kayıt ekler)
│   ├── 1.000 TL
│   ├── 1.500 TL
│   ├── 2.000 TL
│   └── Özel Tutar              (gezilebilir)
│       └── 100, 200, … 3.000 TL (30 öğe, artan sıra, hepsi çalınabilir)
├── Diğer Masraf                (sekme, gezilebilir)
│   ├── Otopark                 → 50, 100, 150, 200, 300 TL
│   ├── Otoyol / HGS            → 100, 200, 500, 1.000 TL
│   ├── Yıkama                  → 200, 300, 500 TL
│   ├── Bakım                   → 500, 1.000, 2.000, 5.000 TL
│   └── Diğer                   → 100, 250, 500, 1.000, 2.000 TL
└── Sesle Giriş                 (sekme, gezilebilir; M5'te eklenir)
    ├── Sesle Yakıt Tutarı      (çalınabilir = tutar sorusu bildirimi gönderir)
    └── Sesle Diğer Masraf
```

Kurallar:
- Kök sekme sayısını `BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_LIMIT` ipucuna göre sınırla.
- Öğe kimlikleri `B_TEKNIK_SARTNAME.md` Bölüm 4'teki şemaya uyar.
- Tüm başlıklar kısa, büyük ve okunaklıdır: `1.000 TL`.
- Alt başlık (subtitle): kategori adı.

---

## 7. Öğeye dokununca ne olur (kayıt akışı)

Öğe dokunuşu `onPlayFromMediaId` olarak gelir. Sırası **kesindir**:

1. **Önce kaydet.** Kaydı Room'a yaz (uygulama düzeyi coroutine kapsamında, servis kapansa bile tamamlanır).
2. **Görsel onay.** `MediaSession` metadata'sını güncelle: başlık `Kaydedildi: 1.000 TL Yakıt`, sanatçı alanı saat (`HH:mm`). Oynatma durumu `STATE_PAUSED`. Araç "şimdi çalıyor" ekranında bunu gösterir. 8 saniye sonra metadata nötr hale döner (`Rifter Log — masraf seç`).
3. **Sesli onay.** TTS: `Yakıt kaydedildi.` (kategori adıyla). Kısa tut.
4. **Not bildirimi** (ayar açıksa): `MessagingStyle` bildirimi gönder: `1.000 TL yakıt kaydedildi. Not eklemek için yanıtlayın.` Sürücü isterse yanıtlar ve konuşur, istemezse yok sayar.

Korumalar:
- Aynı `mediaId` 3 saniye içinde tekrar gelirse yok say (çift kayıt koruması).
- `onPlay`, `onPause`, `onStop`, `onSkipTo*`, `onSeekTo` **hiçbir zaman kayıt eklemez**; boş bırak. Araç yeniden bağlanınca otomatik `onPlay` gönderebilir.

---

## 8. Sesli not ve sesle tutar

### 8.1 Sürücü notu
- Kayıt bildirimindeki **Yanıtla** eylemi `RemoteInput` taşır. Android Auto bunu sesle alır (Google'ın konuşma tanıması).
- Yanıt `NoteReplyReceiver`'a gelir: not kayda yazılır (kayıt kimliği bildirim ekstrasında), bildirim güncellenir, TTS: `Not eklendi.` Sonra bildirim kapanır.

### 8.2 Sesle tutar (M5)
- `Sesle Yakıt Tutarı` öğesine dokununca `Tutar kaç TL?` bildirimi çıkar. Sürücü sesle yanıtlar (örn. "bin üç yüz elli" veya "1350").
- `AmountParser` yanıtı sayıya çevirir (B dosyası Bölüm 6). Geçerliyse (50–50.000 TL) kaydı yazar ve Bölüm 7'deki akışın 2-4. adımlarını çalıştırır.
- Sayı anlaşılamazsa TTS: `Tutarı anlayamadım. Tekrar söyleyin.` ve soru bildirimi yeniden gönderilir.

---

## 9. Telefon arayüzü (`MainActivity`, Compose)

1. Üstte bu ayın toplam masrafı.
2. Kayıt listesi, en yeni üstte: tarih-saat (`dd.MM.yyyy HH:mm`), kategori, tutar, sürücü notu.
3. Bildirim izni yoksa `Bildirim iznini ver` butonu; uygulama ilk açılışta izni ister.
4. Anahtar: `Kayıttan sonra not bildirimi gönder` (varsayılan açık).
5. `CSV Dışa Aktar` butonu (B dosyası Bölüm 7).
6. **Silme yok.** Kayıt silme, "tümünü temizle" gibi hiçbir arayüz ekleme.

---

## 10. Milestone'lar ve kabul kriterleri

**M0 — Medya iskeleti (Desktop Head Unit'te görünme)**
`RifterApp`, `RifterMediaService`, medya ağacı (sabit, kayıt yok), `automotive_app_desc.xml`, manifest, basit `MainActivity`. Öğeye dokununca sadece metadata onayı gösterilir.
Kabul: DHU'da medya uygulamaları arasında "Rifter Log" görünür, sekmeler açılır, öğeye dokununca "Kaydedildi: …" metni çıkar.
> M0 bitince kullanıcı, `C_KURULUM_VE_TEST.md` içindeki **Araç Testi**ni yapar. Sonuca göre devam edilir.

**M1 — Veri katmanı ve telefon ekranı**
Room, repository. Ağaçtaki öğeler gerçek kayıt yazar. `MainActivity` liste ve aylık toplam.
Kabul: Araçta/DHU'da dokunulan tutar telefonda doğru tarih-saat ve kategoriyle görünür; uygulama kapanıp açılınca kayıtlar duruyor.

**M2 — Tam ağaç**
Bölüm 6'daki tüm sekme ve klasörler (Sesle Giriş hariç), çift dokunma koruması.
Kabul: Tüm klasörler açılıyor, her tutar doğru kategoriyle kaydediliyor.

**M3 — Sesli onay (TTS)**
`TtsSpeaker`, ses odağı, Türkçe onay.
Kabul: Dokunuştan sonra "Yakıt kaydedildi." duyulur.

**M4 — Not bildirimi**
`MessagingStyle` bildirimi, `RemoteInput` yanıtı, "okundu" eylemi, izin akışı, not güncelleme, ayar anahtarı.
Kabul: Bildirim araç ekranında görünür; sesle yanıtlanan metin kayda not olarak yazılır.

**M5 — Sesle giriş**
`Sesle Giriş` sekmesi, tutar sorusu bildirimi, `AmountParser` ve birim testleri.
Kabul: "bin beş yüz" denince 1.500 TL kaydı oluşur.

**M6 — CSV ve sağlamlaştırma**
CSV dışa aktarma, hata durumları, loglama, `README` (docs dışında, kısa).

---

## 11. Yasaklar

- `androidx.car.app` (Car App Library) bağımlılığı, `CarAppService`, şablonlar.
- `RECORD_AUDIO` izni, `SpeechRecognizer`, `CarAudioRecord`.
- Media3 / ExoPlayer, gerçek ses oynatma.
- Araç ekranında ikon/resim (M6 sonrası ayrı iş).
- Kayıt silme arayüzü, yıkıcı Room migration (`fallbackToDestructiveMigration`).
- Ağ isteği, analitik, reklam.
- Bu dosyadaki kararlara alternatif önermek.

---

## 12. Şimdi ne yapacaksın

1. Bu dosyayı ve `B_TEKNIK_SARTNAME.md`'yi oku.
2. Mevcut proje dosyalarına (`build.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml`, `MainActivity.kt`) bak.
3. Soru sormadan **M0**'ı yap.
