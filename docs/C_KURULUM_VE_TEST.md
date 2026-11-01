# Rifter Log — Kurulum ve Test Kılavuzu

> Bu dosya **senin** içindir. Testleri **araç park halindeyken** yap. Sürüşte ekranla ve telefonla uğraşma.

Kurulum yolu: Android Studio'dan telefona **kablosuz hata ayıklamayla** yüklenir (APK/debug). Play Store yok.

```
[Bilgisayar: Android Studio] --Wi-Fi hata ayıklama (evde)--> [Xiaomi 14T]
[Xiaomi 14T] --kablosuz Android Auto (araçta)--> [Rifter ekranı]
```

İki kural:
1. **Yüklemeyi evde yap.** Telefon araçtayken araç Wi-Fi'sine bağlıdır, bilgisayarla aynı ağda olamaz.
2. Geliştirmeyi **bilgisayardaki Desktop Head Unit (DHU)** ile test et. Araçta sadece son kontrolü yap.

---

## Bölüm A — Tek seferlik kurulum

### A1. Android Studio
- `Tools > SDK Manager > SDK Tools` sekmesinde **Android Auto Desktop Head Unit Emulator** kutusunu işaretle, `Apply`.
- Kurulunca dosya: `<SDK klasörü>\extras\google\auto\desktop-head-unit.exe`. SDK klasörünü `SDK Manager` üstündeki "Android SDK Location" satırından görürsün.

### A2. Xiaomi 14T (HyperOS) geliştirici ayarları
1. `Ayarlar > Telefon hakkında` içinde **OS sürümü** satırına 7 kez dokun.
2. `Ayarlar > Ek ayarlar > Geliştirici seçenekleri` içinde şunları aç:
   - **USB hata ayıklama**
   - **Kablosuz hata ayıklama**
   - **USB üzerinden yükle** ve **USB hata ayıklama (Güvenlik ayarları)** (Xiaomi bunları ister; Mi hesabı girişi isteyebilir)
3. Android Studio'da `Device Manager` (sağ kenar çubuğu) > **Pair Devices Using Wi-Fi** ile eşleştir. Telefon ve bilgisayar aynı Wi-Fi'de olmalı.

### A3. Android Auto geliştirici modu
1. Telefonda **Android Auto** ayarlarını aç (`Ayarlar > Bağlı cihazlar > Bağlantı tercihleri > Android Auto`).
2. En alttaki **Sürüm** satırına hızlıca **10 kez** dokun, çıkan onayda `Tamam` de.
3. Sağ üstteki **⋮ > Geliştirici ayarları** menüsüne gir.
4. **Bilinmeyen kaynaklar (Unknown sources)** seçeneğini **aç**. Bu ayar, sideload edilen medya uygulamalarını ve mesaj bildirimlerini Android Auto'da gösterir.
5. Aynı menüde kablosuz Android Auto seçeneğinin **açık** olduğunu kontrol et. Kablosuz bağlantı kopuyorsa ilk bakılacak yer burasıdır.

### A4. Xiaomi'ye özel: uygulamanın kapatılmaması
Uygulama yüklendikten sonra:
- `Ayarlar > Uygulamalar > Uygulamaları yönet > Rifter Log`:
  - **Pil tasarrufu** → `Kısıtlama yok`
  - **Otomatik başlat** → aç
  - **Bildirimler** → hepsi açık, **Kayan bildirimler** ve **Kilit ekranı** açık (bildirimler araçta görünmesi için)
- Xiaomi arka plandaki uygulamayı çabuk kapatır; bu ayarlar araç bağlantısında uygulamanın kaybolmasını önler.

### A5. Türkçe ses ayarları (sesli not ve onay için)
Telefonun arayüzü İngilizce, bu yüzden bunları elle kontrol et:
- **Sesli giriş dili:** Google uygulaması > profil > `Ayarlar > Google Asistan > Diller` içinde **Türkçe** ekle ve birincil yap. Android Auto sesli yanıtı bu dille anlar.
- **Metin okuma:** `Ayarlar > Ek ayarlar > Erişilebilirlik > Metin okuma çıktısı` içinde motor **Google Konuşma Servisi**, dil **Türkçe**. Türkçe ses verisi yüklü olmalı.

---

## Bölüm B — DHU ile bilgisayarda test

1. Android Studio'da telefonu seçip `Run` (▶) ile uygulamayı yükle. Telefonda bir kez aç ve **bildirim iznini ver**.
2. Telefonda Android Auto > **Geliştirici ayarları > ⋮ > Start head unit server** (Head unit sunucusunu başlat).
3. Bilgisayarda terminal aç (Android Studio altta `Terminal`) ve:
   ```
   adb forward tcp:5277 tcp:5277
   ```
4. DHU'yu çalıştır:
   ```
   "<SDK klasörü>\extras\google\auto\desktop-head-unit.exe"
   ```
5. DHU penceresinde medya uygulamalarına (müzik simgesi / uygulama çekmecesi) bak. **Rifter Log** görünmeli.

Görünmüyorsa telefon bağlıyken terminalde:
```
adb logcat | findstr /i "RifterMedia gearhead"
```
`onGetRoot caller=...` satırı görünüyorsa Android Auto uygulamayı buldu demektir.

---

## Bölüm C — Araç Testi (M0 sonrası, en önemli test)

Amaç: Tüm uygulamayı yazmadan önce, Rifter'ın Android Auto'sunda uygulamanın görünüp görünmediğini öğrenmek.

1. Evde M0'ı telefona yükle (Bölüm B, adım 1). Bildirim iznini ver. A3 ve A4 tamam olsun.
2. Aracı çalıştır (park halinde), telefonu **kablosuz Android Auto** ile bağla.
3. Araç ekranında uygulama çekmecesini (alt köşedeki ızgara simgesi) aç, **Rifter Log**'u ara.
4. Yoksa: telefonda `Android Auto ayarları > Launcher'ı özelleştir` içinde **Rifter Log**'u işaretle, bağlantıyı kesip yeniden bağlan. Android Auto ilk gördüğünde "yeni uygulama" bildirimi de gösterebilir.
5. Aç: sekmeler görünmeli, bir tutara dokununca "Kaydedildi: …" metni çıkmalı.

| Sonuç | Yapılacak |
|---|---|
| Uygulama araçta görünüyor ve açılıyor | Devam: M1'den itibaren normal ilerle. |
| Araçta görünmüyor ama DHU'da görünüyor | Bana logcat çıktısını ve ekran görüntüsünü at. Bu durumda araç ekranı kısmı Play Store olmadan çalışmıyor demektir; telefon tarafı (veri, CSV) yine çalışır, birlikte karar veririz. |

---

## Bölüm D — Uygulamayı güncelleme ve veriyi koruma

1. **Uygulamayı telefondan silme.** Silinirse tüm kayıtlar gider.
2. Güncelleme için Android Studio'dan yine `Run` de. Aynı imza ile üstüne yüklenir, veri kalır.
3. Android Studio debug imza anahtarı bilgisayarındadır: `C:\Users\<KullanıcıAdı>\.android\debug.keystore`. **Bu dosyayı bir yere yedekle.** Windows'u yeniden kurarsan ya da başka bilgisayara geçersen anahtar değişir; yeni sürüm eskinin üstüne yüklenmez ve silmek zorunda kalırsın.
4. **Ayda bir** uygulamadan `CSV Dışa Aktar` yap, dosyayı Drive'a veya bilgisayara kaydet.
5. Yeni telefona geçmeden önce CSV al.

---

## Bölüm E — Araçta son kontrol listesi (M4 sonrası)

Aracı **park halinde** çalıştır:

- [ ] Kablosuz Android Auto bağlandı, Rifter Log medya uygulamaları arasında
- [ ] Yakıt sekmesinde 500 / 1.000 / 1.500 / 2.000 TL ve Özel Tutar görünüyor
- [ ] `1.000 TL` dokununca ekranda "Kaydedildi: 1.000 TL Yakıt" çıkıyor
- [ ] Araç hoparlöründen Türkçe "Yakıt kaydedildi." duyuluyor
- [ ] Araç ekranında not bildirimi geliyor, `Yanıtla` ile sesle "kalan menzil 350 kilometre" diyebiliyorum
- [ ] "Not eklendi." duyuluyor
- [ ] Telefonu açınca kayıt tutar, tarih-saat ve notla listede
- [ ] Özel Tutar listesi kaydırılıyor, seçilen tutar doğru kaydediliyor
- [ ] Diğer Masraf > Otopark akışı çalışıyor
- [ ] Bağlantıyı kesip yeniden bağlayınca çift kayıt oluşmuyor
- [ ] Telefonu yeniden başlatınca kayıtlar duruyor

---

## Bölüm F — Antigravity'de ilk mesaj (kopyala yapıştır)

Önce `File > Open Folder` ile `C:\Software\Coding\Kotlin\RifterLog` klasörünü aç. Eski `docs` dosyalarını sil, bu üç dosyayı (`A_ANA_PROMPT.md`, `B_TEKNIK_SARTNAME.md`, `C_KURULUM_VE_TEST.md`) `docs` klasörüne koy. Sonra:

```
docs klasöründe 3 dosya var. Şunları oku, bağlayıcıdır:
- docs/A_ANA_PROMPT.md
- docs/B_TEKNIK_SARTNAME.md
(docs/C_KURULUM_VE_TEST.md benim içindir, sadece bilgi olarak oku.)

Dosyalar dışında bir bilgiye ihtiyacın yok, proje gerçekleri A dosyası Bölüm 2'de yazıyor.
Dosyaları projeye doğrudan yaz, docs klasörüne dokunma.
Milestone'ları sırayla yap. Ben "M_ tamam" demeden sonrakine geçme.

Şimdi sadece M0'ı yap.
```
