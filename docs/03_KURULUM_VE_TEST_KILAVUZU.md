# Rifter Yol Arkadaşım — Kurulum ve Test Kılavuzu

> Bu dosya **senin** içindir (istersen AI'ya da ver). Uygulamanın kod dışında ihtiyaç duyduğu her şey burada.
> Testleri **araç park halindeyken** yap. Sürüş sırasında telefon veya ekranla uğraşma.

---

## Genel plan

```
[Bilgisayar: Android Studio]
        │  Wi-Fi hata ayıklama (evde)
        ▼
[Telefon: uygulama yüklü]
        │  Kablosuz Android Auto (araçta)
        ▼
[Rifter ekranı]
```

İki kural işini kolaylaştırır:
1. **Geliştirmeyi evde yap**, bilgisayarda Desktop Head Unit (DHU) ile test et. Araçta sadece son testi yap.
2. Telefon araçtaki kablosuz Android Auto'ya bağlıyken bilgisayarla aynı Wi-Fi ağında olamaz. Yani araçta ADB ile yükleme yapma; evde yükle, araca öyle git.

---

## Bölüm A — Tek seferlik kurulum

### A1. Android Studio
- Güncel kararlı Android Studio.
- `SDK Manager > SDK Tools` sekmesinde **Android Auto Desktop Head Unit Emulator** kutusunu işaretle ve kur. Kurulunca dosya `<SDK klasörü>/extras/google/auto/` içinde olur.

### A2. Telefonda geliştirici seçenekleri
1. `Ayarlar > Telefon hakkında > Yapı numarası`na 7 kez dokun.
2. `Ayarlar > Geliştirici seçenekleri > Kablosuz hata ayıklama`yı aç.
3. Android Studio'da `Device Manager > Pair Devices Using Wi-Fi` ile telefonu eşle (aynı Wi-Fi ağında olmanız gerekir).

### A3. Telefonda Android Auto geliştirici modu
1. Telefonda **Android Auto** ayarlarını aç (`Ayarlar > Bağlı cihazlar > Android Auto`, veya Android Auto uygulaması).
2. En alttaki **Sürüm** satırına hızlıca **10 kez** dokun. "Geliştirici modu" onay kutusu çıkar, `Tamam` de.
3. Sağ üstteki **⋮ > Developer settings** menüsüne gir.
4. **Unknown sources** seçeneğini aç.
5. Kablosuz Android Auto bağlantısı bu menüde bir anahtara bağlı olabilir. Araç bağlantısı geliştirici modundan sonra kopuyorsa bu menüde kablosuz Android Auto seçeneğinin açık olduğunu kontrol et.

### A4. Telefon pil ayarı
- `Ayarlar > Uygulamalar > Rifter Yol Arkadaşım > Pil` bölümünde **kısıtlama yok / sınırsız** seç.
- Samsung ise: "Kullanılmayan uygulamaları uyut" seçeneğini bu uygulama için kapat. Uyutulan uygulama Android Auto listesinden kaybolur.

### A5. Telefonda mikrofon izni
- Uygulama yüklenince telefonda aç ve **mikrofon iznini ver**. İzin telefonda verilmezse araç ekranından sesli not alınamaz.

---

## Bölüm B — Desktop Head Unit (DHU) ile bilgisayarda test

Araç gerekmez. Uygulamanın Android Auto'da görünüp görünmediğini ve akışın çalışıp çalışmadığını buradan görürsün.

1. Uygulamayı telefona Android Studio'dan `Run` ile yükle.
2. Telefonda Android Auto > **Developer settings** > **⋮** menüsünden **Start head unit server**'ı başlat.
3. Bilgisayarda terminal aç ve portu yönlendir:
   ```
   adb forward tcp:5277 tcp:5277
   ```
4. DHU'yu çalıştır (`<SDK klasörü>/extras/google/auto/` içinden):
   - macOS / Linux: `./desktop-head-unit`
   - Windows: `desktop-head-unit.exe`
5. DHU penceresinde uygulama listesini aç. **Rifter Yol Arkadaşım** görünmeli.

Görünmüyorsa loglara bak (telefon bağlıyken):
```
adb logcat | grep -iE "carapp|gearhead"
```
Yaygın sebepler: iki ayrı `intent-filter` yazılmış olması, `automotive_app_desc.xml` eksikliği, Samsung pil optimizasyonu.

---

## Bölüm C — Kapı 1: Gerçek araçta sideload testi

**Ne zaman yapılır:** M0 bittikten ve DHU'da uygulama göründükten hemen sonra. Tüm uygulamayı yazmadan bilmek istediğin şey bu.

1. Evde uygulamayı telefona yükle (Bölüm B, adım 1).
2. Telefonda Android Auto **Developer settings > Unknown sources** açık olsun (A3).
3. Aracına bin, telefonu kablosuz Android Auto'ya bağla.
4. Rifter ekranında Android Auto'nun uygulama listesini aç. Android Auto uygulamayı ilk gördüğünde "yeni uygulama" bildirimi gösterebilir.
5. Listede yoksa telefonda `Android Auto ayarları > Launcher'ı özelleştir` bölümünde uygulamayı işaretle, bağlantıyı kesip yeniden bağlan.

### Sonuç ve karar

| Sonuç | Yapılacak |
|---|---|
| **Uygulama araç ekranında görünüyor ve açılıyor** | Sideload yolu çalışıyor. **Bölüm D'yi atla.** M1'den devam et. |
| **Uygulama görünmüyor** | Google, Car App Library uygulamalarında "Unknown sources"ı dokümanında kapsam dışı bırakıyor. **Bölüm D'ye geç.** |

Kesin sonuç bu testten gelir; ikisi de normal ve planlıdır.

---

## Bölüm D — Kapı 2: Play iç testi (yayın değil)

Uygulamayı kimseye yayınlamıyorsun. Sadece Google Play'in **Internal Testing** kanalıyla kendi telefonuna yüklüyorsun. Google'ın araç testi belgesi bu kanalı gerçek araçta test için geçerli güvenilir kaynak olarak sayıyor ve iç test için normal Play inceleme sürecini beklemeni gerektirmediğini söylüyor.

### D1. Önce verini yedekle (ÖNEMLİ)
Play'den gelen sürüm, Android Studio'dan yüklenen sürümle **farklı imzalanır**. Telefon üzerine yüklemeye izin vermez, önce eski sürümü silmen gerekir ve **silince veri gider**.
- Uygulamada `CSV Dışa Aktar` ile tüm kayıtları dışa aktar ve güvenli bir yere kaydet.
- Bu adım M5'ten önce gerekiyorsa (çok az kayıt vardır) yine de yap.

### D2. Play Console hesabı
1. `play.google.com/console` adresinde geliştirici hesabı aç. Tek seferlik kayıt ücreti var (yaklaşık 25 dolar) ve kimlik doğrulaması istenir.
2. **Uygulama oluştur:** ad `Rifter Yol Arkadaşım`, dil Türkçe, uygulama türü Uygulama, ücretsiz.

### D3. Uygulama paketini üret
1. Android Studio: `Build > Generate Signed App Bundle / APK > Android App Bundle`.
2. Yeni bir **upload key** oluştur. `.jks` dosyasını ve şifreleri **güvenli yere yedekle**; kaybedersen güncelleme yapamazsın.
3. `app/build.gradle.kts`'te `versionCode` her yüklemede 1 artırılır.

### D4. İç test sürümü
1. Play Console'da `Test > Dahili test (Internal testing) > Yeni sürüm oluştur`.
2. `.aab` dosyasını yükle, Play Uygulama İmzalamayı kabul et.
3. `Test edenler` sekmesinde bir e-posta listesi oluştur, **kendi Google hesabını** ekle.
4. Sürümü kaydet ve yayınla (bu, "dahili test"e yayınlamaktır, herkese açık yayın değildir).
5. Test bağlantısını (opt-in URL) telefonda aç, test kullanıcısı ol, uygulamayı **Play Store'dan** yükle.
6. Aracı bağla ve Bölüm C, adım 3-5'i tekrarla.

### D5. Güncellemeler
- Her yeni sürüm için `versionCode`'u artırıp yeni `.aab` yükle. Play üzerinden güncelle. Android Studio'dan `Run` ile doğrudan yükleme bundan sonra farklı imza yüzünden çalışmaz; geliştirme için DHU'yu kullanmaya devam et (DHU testi için debug sürümü ayrı bir `applicationIdSuffix ".debug"` ile yüklenebilir).

---

## Bölüm E — Veriyi asla kaybetmemek

1. **Uygulamayı telefondan silme.** Silinirse veri gider.
2. **Aynı `applicationId` ve aynı imza anahtarı** ile güncelle. Android Studio `Run` ile üzerine yüklemek veriyi korur.
3. **Ayda bir `CSV Dışa Aktar`** yap, dosyayı Drive'a veya bilgisayara kaydet.
4. Yeni telefona geçmeden önce CSV al.
5. Upload key (`.jks`) ve şifrelerini kaydet (Bölüm D kullanılıyorsa).

---

## Bölüm F — Araçta son kontrol listesi

Aracı **park halinde** çalıştır ve şunları sırayla dene:

- [ ] Kablosuz Android Auto bağlandı, uygulama listede görünüyor
- [ ] Ana ekranda 6 buton açılıyor
- [ ] `1.000 TL` butonuna dokununca "kaydedildi" bilgisi çıkıyor
- [ ] Ardından "Dinleniyor…" ekranı geliyor
- [ ] "Kalan menzil 350 kilometre" dediğinde Türkçe sesli onay araç hoparlöründen duyuluyor
- [ ] Telefonu açınca kayıt tutar, tarih-saat ve notla listede
- [ ] `Özel Tutar` ekranında +50 / +100 / +500 / −50 / −100 çalışıyor, `KAYDET` kaydediyor
- [ ] `Diğer Masraf > Otopark` akışı çalışıyor
- [ ] Sessiz kalınca kayıt yine de durup onay veriliyor
- [ ] Bağlantıyı kesip yeniden bağlanınca uygulama yine çalışıyor
- [ ] Telefonu yeniden başlatınca kayıtlar duruyor

---

## Bölüm G — AI'ya ilk mesaj (kopyala yapıştır)

```
Ekte iki dosya var: 01_PROJE_PROMPTU.md ve 02_TEKNIK_SARTNAME.md. İkisini de oku, bağlayıcıdır.
Bölüm 2'deki bilgilerim şunlar:
- Telefon modeli: ...
- Telefon Android sürümü: ...
- Android Studio sürümü: ...
- İşletim sistemi: ...
Şimdi M0'ı ver.
```
