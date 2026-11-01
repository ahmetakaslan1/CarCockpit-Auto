# Rifter Yol Arkadaşım — Proje Promptu

> Bu dosyayı ve `02_TEKNIK_SARTNAME.md` dosyasını birlikte oku. İkisi de bağlayıcıdır.
> Kararlar verilmiştir. Alternatif önerme, seçenek sunma, "şöyle de olur" deme. Bu dosyadaki yolu uygula.

---

## 1. Rolün ve çalışma kuralların

Sen kıdemli bir Android / Kotlin geliştiricisisin. Android Auto için Car App Library ile uygulama yazma konusunda uzmansın.

Çalışma kuralların:

1. **Milestone milestone ilerle.** Bölüm 9'daki sırayı izle. Kullanıcı "M_ tamam" demeden bir sonraki milestone'a geçme.
2. **Dosyaları eksiksiz ver.** Diff, "buraya ekle", "geri kalanı aynı" yok. Değişen her dosyanın tamamını yaz. Her dosyanın üstüne tam yolunu yaz (örn. `app/src/main/java/com/rifter/yolarkadasim/car/HomeScreen.kt`).
3. **API uydurma.** Emin olmadığın bir Car App Library, Room, SpeechRecognizer veya TextToSpeech API'si varsa resmi dokümana (developer.android.com/training/cars) ve `androidx/car-app-library` örneklerine göre yaz. Bağımlılık sürüm numarası uydurma; Google Maven'dan doğrulanan en güncel **kararlı** sürümü kullan.
4. **Her milestone sonunda** şunları ver: (a) dosya listesi, (b) çalıştırma adımları, (c) beklenen sonuç. Kullanıcı Android Studio'da çalıştırıp sonucu sana bildirecek.
5. **Kısa açıklama yaz.** Kullanıcı Android geliştiricisi ama bu alanda (Android Auto) yeni. Kodun yanına Türkçe, kısa, "neden böyle" yorumları koy.
6. Kod tanımlayıcıları (sınıf, fonksiyon, değişken) İngilizce; arayüz metinleri ve yorumlar Türkçe. Tüm metinler `strings.xml` içinde olsun.

---

## 2. Kullanıcı ve ortam bilgileri

Kullanıcı bu alanları doldurup sana verecek. Boş kalan alan için varsayım yapma, sor.

```
Telefon modeli: <BURAYA YAZ>
Telefon Android sürümü: <BURAYA YAZ>
Android Studio sürümü: <BURAYA YAZ>
Bilgisayar işletim sistemi: <BURAYA YAZ>
Araç: Peugeot Rifter 2024, 10 inç ekran, kablosuz Android Auto
Bağlantı: Telefon-bilgisayar kablosuz hata ayıklama (Wi-Fi), telefon-araç kablosuz Android Auto
```

---

## 3. Amaç

Sürücü, aracın ekranından **klavyesiz**, birkaç büyük dokunuşla yakıt ve diğer masraflarını kaydeder. Ardından kısa bir **sesli sürücü notu** söyler (örn. "kalan menzil 350 kilometre"). Uygulama notu metne çevirip kayda ekler ve araç hoparlöründen **Türkçe sesli onay** verir.

- Tüm işlem gücü ve veri telefondadır. Araç ekranı sadece yansıtma yüzeyidir.
- Uygulama **Play Store'da yayınlanmayacak**. Sadece kullanıcının kendi telefonunda çalışacak.
- Güvenlik önceliklidir: araç ekranında metin girişi, karmaşık ekran ve uzun akış yoktur.

---

## 4. Kesin kararlar (tartışmaya açık değil)

| Konu | Karar |
|---|---|
| Dil | Kotlin |
| Telefon arayüzü | Jetpack Compose, Material 3 |
| Araç arayüzü | Android for Cars **Car App Library** şablonları (`androidx.car.app`) |
| Uygulama kimliği | `com.rifter.yolarkadasim` |
| minSdk | 26 |
| compile/target SDK | Android Studio'nun önerdiği güncel kararlı sürüm |
| Araç uygulama kategorisi | `POI` **ve** `IOT`, **tek bir `<intent-filter>` içinde** (ayrı iki filter yazma) |
| Araçta kullanılacak şablonlar | `GridTemplate`, `ListTemplate`, `MessageTemplate` |
| Yerel veritabanı | Room (KSP ile) |
| Para birimi | TL, tam sayı (kuruş yok) |
| Zaman | `System.currentTimeMillis()` (epoch ms) olarak sakla; gösterimde `Europe/Istanbul` |
| Kategori dışı bilgi | Litre bilgisi ayrı alan **değil**; sesli notta serbest metin olarak yer alır |
| Sesten metne (STT) | `SpeechRecognizer`, dil `tr-TR` |
| Metinden sese (TTS) | `TextToSpeech`, dil `tr-TR` |
| HostValidator | `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` (kişisel kullanım, Desktop Head Unit imzalı host değildir) |
| Akış derinliği | Şablon yığını en fazla 3 ekran; kayıttan sonra `popToRoot()` |

---

## 5. Araç ekranı akışı

### 5.1 HomeScreen (kök ekran) — `GridTemplate`

Başlık: `Yakıt Kaydı`. Tam 6 öğe, büyük ve ikonlu:

1. `500 TL`
2. `1.000 TL`
3. `1.500 TL`
4. `2.000 TL`
5. `Özel Tutar`
6. `Diğer Masraf`

Davranış:
- Tutar butonuna dokunma → kategori `FUEL` ile kaydı **hemen** veritabanına yaz → `CarToast` ile "1.000 TL kaydedildi" göster → `popToRoot()` sonra `VoiceNoteScreen(recordId)` ekranını aç.
- `Özel Tutar` → `CustomAmountScreen(FUEL)`.
- `Diğer Masraf` → `CategoryScreen`.

### 5.2 CustomAmountScreen — `GridTemplate` (kaydırıcı yerine artır-azalt)

Şablonlarda gerçek slider yoktur. Bu yüzden:

- Başlık her zaman güncel tutarı gösterir: `Tutar: 1.350 TL` (`invalidate()` ile yenilenir).
- 6 öğe: `+50`, `+100`, `+500`, `−50`, `−100`, `KAYDET`.
- Başlangıç değeri kategoriye göre sabit: FUEL 1000, PARKING 100, TOLL 100, WASH 300, MAINTENANCE 1000, OTHER 100. (Bir sabit dosyasında dursun.)
- Alt sınır 50 TL, üst sınır 20.000 TL.
- `KAYDET` → kaydı yaz → toast → `popToRoot()` → `VoiceNoteScreen(recordId)`.

### 5.3 CategoryScreen — `ListTemplate`

Başlık `Diğer Masraf`. Satırlar: `Otopark`, `Otoyol / HGS`, `Yıkama`, `Bakım`, `Diğer`.
Seçim → `CustomAmountScreen(seçilenKategori)`.

### 5.4 VoiceNoteScreen — `MessageTemplate`

- Kayıt **bu ekrana gelmeden önce** zaten kaydedilmiştir. Ses notu başarısız olsa bile kayıt kaybolmaz.
- Ekran açılınca otomatik dinleme başlar. Mesaj: `Kaydedildi. Ekrandaki bilgiyi söyleyin (örn. kalan menzil 350 kilometre).` Dinleme sırasında mesaj `Dinleniyor…` olur.
- Tek eylem: `Atla`.
- En fazla 10 saniye dinle. Sonuç gelirse `driverNote` alanını güncelle.
- Sonra TTS onayı (Bölüm 7.2) → onay bitince `popToRoot()`.
- Hata / izin yok / tanıma yok / sessizlik durumlarında da TTS onayı verilir (nota dair kısım hariç) ve ekran kapanır.

### 5.5 Sınırlar

- Öğe sayısı sınırlarını sabit yazma. `ConstraintManager.getContentLimit(...)` ile oku ve öğe listesini bu sınıra göre kırp.
- Araç ekranında `TextField`, klavye, `WebView`, kendi `Activity`'n gösterilmez.

---

## 6. Telefon arayüzü (`MainActivity`, Compose)

Sade olacak:

1. Üstte bu ayın toplam masrafı.
2. Altında kayıt listesi, en yeni üstte: tarih-saat (`dd.MM.yyyy HH:mm`), kategori, tutar, sürücü notu.
3. `Mikrofon iznini ver` butonu (izin yoksa görünür). Uygulama ilk açılışta `RECORD_AUDIO` iznini isteyecek.
4. `CSV Dışa Aktar` butonu (Bölüm 8).
5. **Silme yok.** Kayıt silme, "tümünü temizle" gibi hiçbir arayüz ekleme.

---

## 7. Ses

### 7.1 Sesli not (STT)

- Telefonun varsayılan mikrofonuyla `SpeechRecognizer` (M4). Dil `tr-TR`.
- Araç mikrofonundan `CarAudioRecord` ile alma ayrı bir iyileştirme işidir (M6), ilk sürüme dahil değildir.
- `RECORD_AUDIO` izni yoksa araç ekranında izin istemek yerine ses notu adımını atla. İzin telefonda `MainActivity` üzerinden önceden verilir.

### 7.2 Sesli onay (TTS)

- `TextToSpeech` `tr-TR`. Dil yoksa hata verme; sadece `CarToast` göster.
- `AudioAttributes`: `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` + `CONTENT_TYPE_SPEECH`. Konuşmadan önce `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` ile ses odağı iste, bitince bırak.
- Not varsa: `1000 lira yakıt kaydedildi. Not: kalan menzil 350 kilometre.`
- Not yoksa: `Kayıt tamamlandı. 1000 lira yakıt kaydedildi.`
- TTS motoru oturum başında hazırlanır (ilk konuşmada gecikme olmasın), oturum bitince kapatılır.

---

## 8. Veri güvenliği ve kalıcılık

- Kayıtlar Room veritabanında. `SharedPreferences` kayıt için kullanılmaz.
- `fallbackToDestructiveMigration()` **yasak**. Şema değişirse yazılı Migration yazılır. `exportSchema = true`.
- Uygulama silinirse veri gider. Bu yüzden `CSV Dışa Aktar` zorunludur: `ActivityResultContracts.CreateDocument("text/csv")` ile kullanıcı dosyayı kendi seçtiği yere kaydeder.
- CSV: UTF-8 BOM'lu, ayraç `;`, başlık satırı: `id;tarih_saat;kategori;tutar_tl;surucu_notu`. Tarih `yyyy-MM-dd HH:mm:ss` (Europe/Istanbul).

---

## 9. Milestone'lar ve kabul kriterleri

**M0 — İskelet ve Desktop Head Unit'te görünme**
`build.gradle`, `AndroidManifest.xml`, `automotive_app_desc.xml`, `RifterCarAppService`, `RifterSession`, `HomeScreen` (butonlar şimdilik sadece `CarToast`). Basit bir `MainActivity`.
Kabul: Desktop Head Unit'in uygulama listesinde "Rifter Yol Arkadaşım" görünür, açınca 6 butonlu grid gelir.
> M0 bitince kullanıcı, `03_KURULUM_VE_TEST_KILAVUZU.md` içindeki **Kapı 1** testini araçta yapar. Sonuca göre devam edilir.

**M1 — Veri katmanı ve telefon ekranı**
Room (entity, DAO, database), repository, `MainActivity` liste ve aylık toplam.
Kabul: Telefonda uygulama kapatılıp açılınca kayıtlar duruyor.

**M2 — Tutar akışı**
Home butonları kayıt yazar. `CustomAmountScreen`, `CategoryScreen`.
Kabul: Araçta/DHU'da bir butona dokununca telefondaki listede o tutar, doğru tarih-saat ve kategoriyle görünür.

**M3 — Sesli onay (TTS)**
`TtsSpeaker` sınıfı, ses odağı, kayıt sonrası onay cümlesi.
Kabul: Kayıttan sonra Türkçe onay duyulur.

**M4 — Sesli not (STT)**
`VoiceNoteScreen`, `SpeechRecognizer`, izin akışı, not güncelleme, hata yolları.
Kabul: "Kalan menzil 350 kilometre" denince not kayda yazılır ve onay cümlesinde okunur. Sessizlikte kayıt yine de durur.

**M5 — CSV dışa aktarma**
Bölüm 8'deki CSV.
Kabul: Dosya Excel'de Türkçe karakterlerle açılır.

**M6 — Sağlamlaştırma**
`ConstraintManager` limitleri, kaynak temizliği (recognizer, TTS), çift dokunma koruması, `CarAudioRecord` ile araç mikrofonundan alma denemesi (API 33+ için `RecognizerIntent.EXTRA_AUDIO_SOURCE`).

---

## 10. Yasaklar

- Araç ekranında metin girişi, `WebView`, kendi arayüzünü çizmek.
- Navigasyon şablonları veya `NAVIGATION_TEMPLATES` / `MAP_TEMPLATES` izinleri.
- İki ayrı `<intent-filter>` ile kategori bildirmek.
- Yıkıcı Room migration'ı, kayıt silme arayüzü.
- Analitik, reklam, ağ isteği. Uygulama internete kayıt göndermez.
- Bu dosyadaki kararlara alternatif önermek.

---

## 11. Şimdi ne yapacaksın

1. Bu dosyayı ve `02_TEKNIK_SARTNAME.md`'yi oku.
2. Bölüm 2'deki boş alanları kullanıcıdan iste (yalnızca boş olanları).
3. **M0**'ı ver.
