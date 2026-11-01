package com.asn.rifterlog.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class RadioCategory {
    TURKISH,
    KURDISH
}

@Entity(tableName = "radio_stations")
data class RadioStation(
    @PrimaryKey val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val category: String, // "TURKISH" veya "KURDISH"
    val sortOrder: Int = 0
) {
    val categoryEnum: RadioCategory
        get() = if (category == RadioCategory.KURDISH.name) RadioCategory.KURDISH else RadioCategory.TURKISH
}

data class OnlineRadioCandidate(
    val name: String,
    val subtitle: String,
    val streamUrl: String
)

object RadioStations {

    val defaultTurkishList: List<RadioStation> = listOf(
        RadioStation(
            id = "powerturk",
            name = "📻 PowerTürk FM",
            genre = "Türkçe Pop · Canlı Yayın",
            streamUrl = "https://live.powerapp.com.tr/powerturk/mpeg/icecast.audio",
            category = RadioCategory.TURKISH.name,
            sortOrder = 0
        ),
        RadioStation(
            id = "powerfm",
            name = "📻 Power FM",
            genre = "Yabancı Hit · Canlı Yayın",
            streamUrl = "https://live.powerapp.com.tr/powerfm/mpeg/icecast.audio",
            category = RadioCategory.TURKISH.name,
            sortOrder = 1
        ),
        RadioStation(
            id = "kralpop",
            name = "📻 Kral Pop Radyo",
            genre = "Türkçe Pop · Canlı Yayın",
            streamUrl = "http://46.20.3.201:80/",
            category = RadioCategory.TURKISH.name,
            sortOrder = 2
        ),
        RadioStation(
            id = "kralfm",
            name = "📻 Kral FM",
            genre = "Arabesk & Fantezi · Canlı Yayın",
            streamUrl = "http://46.20.3.204:80/",
            category = RadioCategory.TURKISH.name,
            sortOrder = 3
        ),
        RadioStation(
            id = "joyturk",
            name = "📻 JoyTürk",
            genre = "Türkçe Slow & Akustik · Canlı Yayın",
            streamUrl = "https://playerservices.streamtheworld.com/api/livestream-redirect/JOY_TURK.mp3",
            category = RadioCategory.TURKISH.name,
            sortOrder = 4
        ),
        RadioStation(
            id = "superfm",
            name = "📻 Süper FM",
            genre = "Türkçe Pop · Canlı Yayın",
            streamUrl = "https://playerservices.streamtheworld.com/api/livestream-redirect/SUPER_FM.mp3",
            category = RadioCategory.TURKISH.name,
            sortOrder = 5
        ),
        RadioStation(
            id = "metrofm",
            name = "📻 Metro FM",
            genre = "Yabancı Pop · Canlı Yayın",
            streamUrl = "https://playerservices.streamtheworld.com/api/livestream-redirect/METRO_FM.mp3",
            category = RadioCategory.TURKISH.name,
            sortOrder = 6
        ),
        RadioStation(
            id = "fenomen",
            name = "📻 Radyo Fenomen",
            genre = "Hit Müzik · Canlı Yayın",
            streamUrl = "https://live.radyofenomen.com/fenomen/128/icecast.audio",
            category = RadioCategory.TURKISH.name,
            sortOrder = 7
        ),
        RadioStation(
            id = "trtfm",
            name = "📻 TRT FM",
            genre = "Ulusal Radyo · Canlı Yayın",
            streamUrl = "https://nmicenotrt.mediatriple.net/trtfm.aac",
            category = RadioCategory.TURKISH.name,
            sortOrder = 8
        ),
        RadioStation(
            id = "trtturku",
            name = "📻 TRT Türkü",
            genre = "Türk Halk Müziği · Canlı Yayın",
            streamUrl = "https://nmicenotrt.mediatriple.net/trtturku.aac",
            category = RadioCategory.TURKISH.name,
            sortOrder = 9
        )
    )

    val defaultKurdishList: List<RadioStation> = listOf(
        RadioStation(
            id = "trtkurdi",
            name = "☀️ TRT Radyo Kurdî",
            genre = "7/24 Kürtçe Müzik, Dengbêj & Haber",
            streamUrl = "https://radio-trtradyo6.medya.trt.com.tr/master.m3u8",
            category = RadioCategory.KURDISH.name,
            sortOrder = 0
        ),
        RadioStation(
            id = "radyodelal",
            name = "☀️ Radyo Delal (İdil / Şırnak)",
            genre = "Kürtçe Stran & Botan Müziği · Canlı",
            streamUrl = "https://wowza1.radyotvonline.com/idilpr/radyodelal.stream/playlist.m3u8",
            category = RadioCategory.KURDISH.name,
            sortOrder = 1
        ),
        RadioStation(
            id = "rudawradio",
            name = "☀️ Rûdaw Radio",
            genre = "Kürtçe Canlı Yayın & Müzik",
            streamUrl = "https://svs.itworkscdn.net/rudawradiolive/rudawradio/playlist.m3u8",
            category = RadioCategory.KURDISH.name,
            sortOrder = 2
        ),
        RadioStation(
            id = "zergan47",
            name = "☀️ Radyo Zergan 47 (Mardin)",
            genre = "Kürtçe & Bölgesel Müzik · Mardin",
            streamUrl = "https://sslyayin.netyayin.net/radyozergan/;",
            category = RadioCategory.KURDISH.name,
            sortOrder = 3
        ),
        RadioStation(
            id = "amed_azadi",
            name = "☀️ Amed Azadi / Mori FM",
            genre = "Kürtçe Müzik · Diyarbakır",
            streamUrl = "https://amed.ozelip.com/8040/stream",
            category = RadioCategory.KURDISH.name,
            sortOrder = 4
        ),
        RadioStation(
            id = "radyomunzur",
            name = "☀️ Radyo Munzur 97.7 (Dersim)",
            genre = "Kurmancî, Kirmanckî (Zazakî) & Özgün",
            streamUrl = "http://yayin1.yayindakiler.com:3002/;stream.nsv",
            category = RadioCategory.KURDISH.name,
            sortOrder = 5
        ),
        RadioStation(
            id = "serhatfm",
            name = "☀️ Serhat FM",
            genre = "Kürtçe Stran, Dengbêj & Serhat Ezgileri",
            streamUrl = "http://yayin.serhatfm.com:9150/stream",
            category = RadioCategory.KURDISH.name,
            sortOrder = 6
        ),
        RadioStation(
            id = "mezopotamya",
            name = "☀️ Radyo Mezopotamya",
            genre = "Kürtçe Özgün & Halk Müziği",
            streamUrl = "https://anadolu.liderhost.com.tr/9404/stream",
            category = RadioCategory.KURDISH.name,
            sortOrder = 7
        ),
        RadioStation(
            id = "kurd1fm",
            name = "☀️ Kurd1 FM",
            genre = "Kürtçe Hit & Dengbêj · Canlı",
            streamUrl = "https://listen.radioking.com/radio/119251/stream/158701",
            category = RadioCategory.KURDISH.name,
            sortOrder = 8
        ),
        RadioStation(
            id = "k24radio",
            name = "☀️ Kurdistan 24 (K24 Radyo)",
            genre = "Kürtçe Haber & Müzik · Canlı",
            streamUrl = "https://audio-edge-3mayu.fra.h.radiomast.io/04b1ce4c-24f2-4172-b756-065832ef78bf",
            category = RadioCategory.KURDISH.name,
            sortOrder = 9
        ),
        RadioStation(
            id = "dengecudi",
            name = "☀️ Dengê Cûdî FM",
            genre = "Kürtçe Stran & Halay · Canlı",
            streamUrl = "https://denge-cudi.stream.laut.fm/denge-cudi",
            category = RadioCategory.KURDISH.name,
            sortOrder = 10
        ),
        RadioStation(
            id = "dengeqamishlo",
            name = "☀️ Dengê Qamişlo",
            genre = "Kürtçe Müzik · Canlı Yayın",
            streamUrl = "https://dengeqamishlo.stream.laut.fm/dengeqamishlo",
            category = RadioCategory.KURDISH.name,
            sortOrder = 11
        ),
        RadioStation(
            id = "gundifm",
            name = "☀️ Gundî FM",
            genre = "Kürtçe Dengbêj & Govend",
            streamUrl = "http://gundifm.ozelip.com:7830/;",
            category = RadioCategory.KURDISH.name,
            sortOrder = 12
        ),
        RadioStation(
            id = "havinfm",
            name = "☀️ Havin FM",
            genre = "Kürtçe Slow & Özgün Müzik",
            streamUrl = "http://yayin6.radyohizmeti.com:7050/;stream.mp3",
            category = RadioCategory.KURDISH.name,
            sortOrder = 13
        ),
        RadioStation(
            id = "gevassesi",
            name = "☀️ Gevaş Sesi Radyosu (Van)",
            genre = "Kürtçe & Van Bölgesel Müzik",
            streamUrl = "https://live.radyositesihazir.com:8056/stream",
            category = RadioCategory.KURDISH.name,
            sortOrder = 14
        ),
        RadioStation(
            id = "spedafm",
            name = "☀️ Speda FM",
            genre = "Kürtçe Radyo Yayını · Canlı",
            streamUrl = "https://radio.host247.net/spedafm",
            category = RadioCategory.KURDISH.name,
            sortOrder = 15
        ),
        RadioStation(
            id = "dogubayazit",
            name = "☀️ Radyo Doğubayazıt 104.4",
            genre = "Kürtçe & Doğu Müziği · Canlı",
            streamUrl = "https://radyo1.radyo-dinle.tc/8156/stream",
            category = RadioCategory.KURDISH.name,
            sortOrder = 16
        ),
        RadioStation(
            id = "bitlisfm",
            name = "☀️ Bitlis FM 98.8",
            genre = "Kürtçe & Bölgesel Müzik · Bitlis",
            streamUrl = "https://ssl22.radyotelekom.com:8172/;",
            category = RadioCategory.KURDISH.name,
            sortOrder = 17
        ),
        RadioStation(
            id = "radyo13bitlis",
            name = "☀️ Radyo 13 FM 96 (Bitlis)",
            genre = "Kürtçe & Yöresel Ezgiler",
            streamUrl = "https://radyo.yayindakiler.com:3050/stream",
            category = RadioCategory.KURDISH.name,
            sortOrder = 18
        ),
        RadioStation(
            id = "starfmkurd",
            name = "☀️ Star FM 95.5",
            genre = "Kürtçe & Özgün Müzik · Canlı",
            streamUrl = "https://anadolu.liderhost.com.tr/9078/stream",
            category = RadioCategory.KURDISH.name,
            sortOrder = 19
        ),
        RadioStation(
            id = "emekradyo",
            name = "☀️ Emek Radyo",
            genre = "Kürtçe & Halk Müziği · Canlı",
            streamUrl = "http://yayin1.radyohizmeti.com:8585/;",
            category = RadioCategory.KURDISH.name,
            sortOrder = 20
        ),
        RadioStation(
            id = "dengegundeme",
            name = "☀️ Dengê Gündeme",
            genre = "Kürtçe Ezgiler · Canlı Yayın",
            streamUrl = "http://kesintisizyayin.com:1656/",
            category = RadioCategory.KURDISH.name,
            sortOrder = 21
        )
    )

    val allDefaults: List<RadioStation> = defaultTurkishList + defaultKurdishList

    private val duplicateProtocolRegex = Regex("^(https?://\\s*)+(https?://)", RegexOption.IGNORE_CASE)

    /**
     * Kullanıcının yanlışlıkla "https://https://..." veya "http://https://..." şeklinde
     * çift protokol yapıştırmasını otomatik temizler; ayrıca laut.fm gibi sitelerin
     * doğrudan ses yerine metin listesi indiren "?pl=m3u" gibi parametrelerini kaldırır.
     */
    fun sanitizeStreamUrl(rawUrl: String): String {
        var cleaned = rawUrl.trim()
        if (cleaned.isEmpty()) return ""
        while (duplicateProtocolRegex.containsMatchIn(cleaned)) {
            cleaned = cleaned.replaceFirst(duplicateProtocolRegex, "$2")
        }
        // Bazı sunucular (örn. laut.fm) URL sonuna ?pl=m3u veya ?pl=pls ekler.
        // Bu parametre doğrudan ses yayını yerine metin tabanlı çalma listesi indirdiği için ExoPlayer hata verir.
        // Parametreyi temizleyerek doğrudan ses akış adresine dönüştürüyoruz:
        cleaned = cleaned.replace(Regex("\\?pl=(m3u|pls|m3u8).*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\?listen=(pls|m3u).*", RegexOption.IGNORE_CASE), "")
        return cleaned
    }

    /**
     * İki yayın adresinin aynı olup olmadığını güvenli şekilde karşılaştırmak için:
     * Protokol (http/https), sondaki eğik çizgi, noktalı virgül ve sorgu parametrelerini yok sayar.
     */
    fun normalizeStreamUrlForComparison(url: String): String {
        return sanitizeStreamUrl(url)
            .lowercase(java.util.Locale.US)
            .replaceFirst(Regex("^https?://"), "")
            .replace(Regex("/+$"), "")
            .replace(Regex(";+$"), "")
            .replace(Regex("\\?.*$"), "")
            .trim()
    }

    /**
     * Radyo adlarını karşılaştırırken emoji ve radyo/fm gibi ekleri soyutlayarak
     * aynı radyonun tekrar eklenmesini tespit eder.
     */
    fun normalizeRadioNameForComparison(name: String): String {
        return normalizeForMatch(name)
            .replace("☀️", "")
            .replace("📻", "")
            .replace("fm", "")
            .replace("radyo", "")
            .replace("radio", "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    /**
     * Yayın linkinin ve codec bilgisinin ExoPlayer tarafından desteklenen
     * gerçek bir ses akışı olup olmadığını doğrular.
     * Geçersiz HTML sayfalarını, video platformlarını ve bozuk biçimleri eler.
     */
    fun isStreamFormatSupported(rawUrl: String, rawCodec: String = ""): Boolean {
        val cleanUrl = sanitizeStreamUrl(rawUrl).lowercase(java.util.Locale.US)
        val cleanCodec = rawCodec.trim().uppercase(java.util.Locale.US)

        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) return false

        // Sosyal medya ve video siteleri radyo yayını değildir
        if (cleanUrl.contains("facebook.com") || cleanUrl.contains("twitter.com") ||
            cleanUrl.contains("instagram.com") || cleanUrl.contains("youtube.com") ||
            cleanUrl.contains("tiktok.com") || cleanUrl.contains("soundcloud.com") ||
            cleanUrl.contains("twitch.tv")) {
            return false
        }

        // Doğrudan web sayfası linkleri (HTML, PHP vb.) ses akışı değildir
        if (cleanUrl.endsWith(".html") || cleanUrl.endsWith(".htm") ||
            cleanUrl.endsWith(".aspx") || cleanUrl.endsWith(".jsp") || cleanUrl.endsWith(".php")) {
            return false
        }

        // Metin tabanlı çalma listesi dosyaları (HLS olan .m3u8 HARİÇ)
        if (cleanUrl.endsWith(".m3u") || cleanUrl.endsWith(".asx") || cleanUrl.endsWith(".ram")) {
            return false
        }

        // Codec kontrolü (eğer API'den geldiyse)
        if (cleanCodec.isNotBlank()) {
            val validCodecs = setOf("MP3", "AAC", "AAC+", "OGG", "OPUS", "FLAC", "HLS")
            if (cleanCodec in validCodecs) return true
            if (cleanCodec == "UNKNOWN" || cleanCodec == "") {
                return cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".aac") || cleanUrl.endsWith(".m3u8") ||
                       cleanUrl.endsWith("/stream") || cleanUrl.endsWith(";stream") || cleanUrl.endsWith(";") ||
                       cleanUrl.contains(":80") || cleanUrl.contains(":8000") || cleanUrl.contains(":8080") ||
                       cleanUrl.contains("icecast") || cleanUrl.contains("shoutcast") || cleanUrl.contains("streamtheworld") ||
                       cleanUrl.contains("zeno.fm")
            }
            return false
        }

        return true
    }

    /**
     * "http://" veya "https://" kısayol butonuna basıldığında:
     * - Kutuda zaten tam URL varsa (http:// veya https:// ile başlıyorsa) ikincisini eklemez, mevcut protokolü günceller.
     * - Kutuda protokol yoksa başına ekler.
     */
    fun applyProtocolPrefix(currentText: String, prefix: String): String {
        val sanitized = sanitizeStreamUrl(currentText)
        if (sanitized.isEmpty()) return prefix
        val withoutExistingScheme = sanitized
            .replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .trimStart()
        return "$prefix$withoutExistingScheme"
    }

    // Ekstra doğrulanmış Bölgesel / Kürtçe / Türkçe radyo havuzu (Arama yapıldığında şehir, dil ve tür etiketleriyle eşleşir)
    private data class IndexedStation(
        val name: String,
        val subtitle: String,
        val streamUrl: String,
        val keywords: String
    )

    private val extraSearchableCatalog: List<IndexedStation> by lazy {
        val fromDefaults = allDefaults.map { st ->
            val extraTags = if (st.categoryEnum == RadioCategory.KURDISH) {
                "kürtçe kürt kurdi kurdish stran dengbej doğu güneydoğu mezopotamya"
            } else {
                "türkçe türk turkce ulusal radyo fm"
            }
            IndexedStation(
                name = st.name,
                subtitle = st.genre,
                streamUrl = st.streamUrl,
                keywords = "${st.name} ${st.genre} $extraTags"
            )
        }
        val additionalRegionalAndNational = listOf(
            IndexedStation(
                name = "☀️ Radyo Zergan 47 (Mardin / Kızıltepe)",
                subtitle = "Mardin · Kürtçe & Bölgesel Müzik",
                streamUrl = "https://sslyayin.netyayin.net/radyozergan/;",
                keywords = "mardin kızıltepe nusaybin midyat artuklu derik mazıdağı zergan kürtçe kürt kurdi"
            ),
            IndexedStation(
                name = "☀️ Radyo Ekin 94.3",
                subtitle = "Özgün Müzik, Türkü & Kürtçe Ezgiler",
                streamUrl = "http://yayin.turkhosted.com:6006/;",
                keywords = "ekin özgün türkü kürtçe kürt halk müziği mardin diyarbakır amed"
            ),
            IndexedStation(
                name = "☀️ Yön Radyo 96.6",
                subtitle = "Türkü, Özgün & Anadolu Ezgileri",
                streamUrl = "https://yonradyo.radyotvonline.net/yonradyo",
                keywords = "yön radyo türkü özgün halk müziği kürtçe türkçe"
            ),
            IndexedStation(
                name = "☀️ Waar Radio (Duhok / Behdînî)",
                subtitle = "Kürtçe (Kurmancî / Behdînî) Müzik & Kültür",
                streamUrl = "https://stream.zeno.fm/0r0xa792kwzuv",
                keywords = "waar duhok zaho şırnak cizre silopi mardin nusaybin kürtçe kürt kurdi kurdish"
            ),
            IndexedStation(
                name = "☀️ Dengê Welat / Mezopotamya Stran",
                subtitle = "Kürtçe Stran, Dengbêj & Klasikler",
                streamUrl = "https://listen.radioking.com/radio/119251/stream/158701",
                keywords = "welat mezopotamya mardin amed diyarbakır van şırnak kürtçe kürt kurdi dengbej stran"
            ),
            IndexedStation(
                name = "☀️ Vin Radio (Kurdî)",
                subtitle = "Kürtçe Pop & Güncel Hit Müzik",
                streamUrl = "https://stream.zeno.fm/f3wvbbqmdg8uv",
                keywords = "vin radio kürtçe kürt kurdi kurdish pop erbil hewler duhok mardin"
            ),
            IndexedStation(
                name = "☀️ Xendan Radio",
                subtitle = "Kürtçe Canlı Yayın & Müzik",
                streamUrl = "https://stream.zeno.fm/7992q8r5kwzuv",
                keywords = "xendan kürtçe kürt kurdi kurdish süleymaniye"
            ),
            IndexedStation(
                name = "📻 Best FM",
                subtitle = "Türkçe Pop & Canlı Şovlar",
                streamUrl = "http://officialbestfm.radyotvonline.net:80/bestfmofficial",
                keywords = "best fm türkçe pop ulusal"
            ),
            IndexedStation(
                name = "📻 Alem FM",
                subtitle = "Türkçe Pop & Hit Müzik",
                streamUrl = "https://turkmedya.radyotvonline.net/alemfmaac",
                keywords = "alem fm türkçe pop ulusal"
            ),
            IndexedStation(
                name = "📻 Show Radyo",
                subtitle = "Türkçe Pop · Canlı Yayın",
                streamUrl = "http://46.20.3.229:80/",
                keywords = "show radyo türkçe pop ulusal"
            ),
            IndexedStation(
                name = "📻 Radyo Viva",
                subtitle = "Türkçe Pop & Fantezi · Canlı Yayın",
                streamUrl = "http://46.20.3.231:80/",
                keywords = "radyo viva türkçe pop fantezi"
            ),
            IndexedStation(
                name = "📻 Baba Radyo 105.6",
                subtitle = "Arabesk, Fantezi & Taverna",
                streamUrl = "http://37.247.98.8/babaradyomp3",
                keywords = "baba radyo arabesk damar fantezi türkçe"
            ),
            IndexedStation(
                name = "📻 Radyo Seymen",
                subtitle = "Oyun Havaları, Halay & Halk Müziği",
                streamUrl = "https://yayin.radyoseymen.com.tr:1070/stream",
                keywords = "radyo seymen oyun havası ankara halay türkçe"
            ),
            IndexedStation(
                name = "📻 Radyo 7",
                subtitle = "Türkçe Müzik & Haber · Canlı",
                streamUrl = "https://radyo7.radyotvonline.net/radyo7",
                keywords = "radyo 7 türkçe müzik"
            ),
            IndexedStation(
                name = "📻 NTV Radyo",
                subtitle = "Haber & Kültür · Canlı Yayın",
                streamUrl = "http://ntvradyo.radyotvonline.com/",
                keywords = "ntv radyo haber konuşma türkçe"
            ),
            IndexedStation(
                name = "📻 A Spor Radyo",
                subtitle = "Canlı Spor & Futbol Yayını",
                streamUrl = "https://trkvz-radyolar.ercdn.net/asporradyo/playlist.m3u8",
                keywords = "a spor radyo futbol maç spor türkçe"
            ),
            IndexedStation(
                name = "📻 TRT Radyo 1",
                subtitle = "Haber, Kültür, Eğitim & Spor",
                streamUrl = "https://nmicenotrt.mediatriple.net/trtradyo1.aac",
                keywords = "trt radyo 1 haber spor kültür türkçe"
            ),
            IndexedStation(
                name = "📻 TRT Nağme",
                subtitle = "Türk Sanat Müziği · Canlı Yayın",
                streamUrl = "https://nmicenotrt.mediatriple.net/trtnagme.aac",
                keywords = "trt nağme sanat müziği klasik türkçe"
            )
        )
        fromDefaults + additionalRegionalAndNational
    }

    private fun normalizeForMatch(input: String): String {
        return input.lowercase( java.util.Locale("tr", "TR"))
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ı', 'i')
            .replace('i', 'i')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace('ê', 'e')
            .replace('î', 'i')
            .replace('û', 'u')
            .trim()
    }

    /**
     * Gelişmiş Çoklu Kaynaklı Radyo Arama Motoru:
     * 1) Türkçe/Kürtçe karakterleri ve kelimeleri ("kürtçe", "kürt", "mardin", "amed", "türkçe" vb.) anlar.
     * 2) Önce uygulama içi doğrulanmış bölgesel/ulusal katalogda (şehir, dil, tür etiketleriyle) arar.
     * 3) Ardından uluslararası Radio-Browser API'sinde hem İSİM (byname), hem DİL (bylanguage), hem ETİKET (bytag) olarak paralel arar.
     */
    suspend fun searchOnlineStations(query: String): List<OnlineRadioCandidate> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val normQuery = normalizeForMatch(trimmed)
        val result = mutableListOf<OnlineRadioCandidate>()
        val seenUrls = mutableSetOf<String>()

        // 1. Adım: Yerel zengin katalogda (isim, şehir, bölge ve dil etiketleriyle) eşleşenleri en üste ekle
        extraSearchableCatalog.forEach { item ->
            val cleanUrl = sanitizeStreamUrl(item.streamUrl)
            val haystack = normalizeForMatch("${item.name} ${item.subtitle} ${item.keywords}")
            val normKey = normalizeStreamUrlForComparison(cleanUrl)
            if (haystack.contains(normQuery) && seenUrls.add(normKey) && isStreamFormatSupported(cleanUrl)) {
                result.add(
                    OnlineRadioCandidate(
                        name = item.name,
                        subtitle = "${item.subtitle} · Doğrulanmış Yayın",
                        streamUrl = cleanUrl
                    )
                )
            }
        }

        // 2. Adım: Uluslararası Radio-Browser API uç noktalarını akıllıca oluştur
        // Çünkü global veritabanında "kürtçe" veya "kürt" kelimesi "kurdish" / "kurd" / "kurdi" olarak kayıtlıdır!
        val endpoints = mutableListOf<String>()
        val encodedRaw = URLEncoder.encode(trimmed, "UTF-8")
        val encodedAscii = URLEncoder.encode(normQuery, "UTF-8")

        val isKurdishQuery = normQuery in listOf("kurtce", "kurt", "kurdi", "kurdish", "kurdistan", "stran", "dengbej", "deng") ||
            normQuery.contains("kurt") || normQuery.contains("kurd")
        val isTurkishQuery = normQuery in listOf("turkce", "turk", "turkish", "turkiye")

        if (isKurdishQuery) {
            endpoints.add("https://de1.api.radio-browser.info/json/stations/bylanguage/kurdish?limit=30&hidebroken=true")
            endpoints.add("https://de1.api.radio-browser.info/json/stations/bytag/kurdish?limit=30&hidebroken=true")
            endpoints.add("https://de1.api.radio-browser.info/json/stations/byname/kurd?limit=30&hidebroken=true")
            endpoints.add("https://de1.api.radio-browser.info/json/stations/byname/deng?limit=25&hidebroken=true")
        } else if (isTurkishQuery) {
            endpoints.add("https://de1.api.radio-browser.info/json/stations/bylanguage/turkish?limit=35&hidebroken=true")
            endpoints.add("https://de1.api.radio-browser.info/json/stations/byname/turk?limit=30&hidebroken=true")
        } else {
            endpoints.add("https://de1.api.radio-browser.info/json/stations/byname/$encodedRaw?limit=25&hidebroken=true")
            if (encodedAscii != encodedRaw) {
                endpoints.add("https://de1.api.radio-browser.info/json/stations/byname/$encodedAscii?limit=25&hidebroken=true")
            }
            endpoints.add("https://de1.api.radio-browser.info/json/stations/bytag/$encodedAscii?limit=25&hidebroken=true")
            endpoints.add("https://de1.api.radio-browser.info/json/stations/bystate/$encodedRaw?limit=20&hidebroken=true")
        }

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "RifterLog/1.0")
                }
                if (conn.responseCode != 200) continue
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("name").trim()
                    var resolvedUrl = obj.optString("url_resolved").trim().ifBlank { obj.optString("url").trim() }
                    resolvedUrl = sanitizeStreamUrl(resolvedUrl)

                    if (name.isBlank() || resolvedUrl.isBlank()) continue

                    // 1) Sunucu kontrolü: Eğer lastcheckok == 0 ise yayın kesinlikle kapalı / bozuktur
                    val lastCheckOk = obj.optInt("lastcheckok", 1)
                    if (lastCheckOk == 0) continue

                    // 2) SSL hatası veren HTTPS yayınları ele
                    val sslError = obj.optInt("ssl_error", 0)
                    if (sslError == 1 && resolvedUrl.startsWith("https://", ignoreCase = true)) continue

                    // 3) Ses codec'i ve format kontrolü (geçersiz HTML web sayfaları, .pls, .m3u metin listeleri vb. elenir)
                    val codec = obj.optString("codec").trim()
                    if (!isStreamFormatSupported(resolvedUrl, codec)) continue

                    val normKey = normalizeStreamUrlForComparison(resolvedUrl)
                    if (!seenUrls.add(normKey)) continue

                    val country = obj.optString("country").trim()
                    val state = obj.optString("state").trim()
                    val lang = obj.optString("language").trim()
                    val tags = obj.optString("tags").trim()
                    val sub = listOf(state, country, lang, tags)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Canlı Radyo Yayını" }
                    result.add(OnlineRadioCandidate(name = name, subtitle = sub, streamUrl = resolvedUrl))
                }
            } catch (_: Exception) {
                // Diğer uç noktalarla devam et
            }
        }
        result
    }
}
