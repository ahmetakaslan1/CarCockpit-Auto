package com.asn.rifterlog.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.widget.Toast
import com.asn.rifterlog.R
import com.asn.rifterlog.RifterApp
import com.asn.rifterlog.data.PlaylistSong
import com.asn.rifterlog.data.RadioCategory
import com.asn.rifterlog.data.Settings
import com.asn.rifterlog.util.CockpitWeatherHelper
import com.asn.rifterlog.util.ParkingLocationHelper
import com.asn.rifterlog.util.ParkingNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NowPlayingUiState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isRadio: Boolean = false,
    val hasMedia: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatOneEnabled: Boolean = false,
    val errorMessage: String? = null
)

class RifterMediaService : MediaBrowserServiceCompat() {

    companion object {
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
        private const val NOTIF_CHANNEL_ID = "rifter_media"
        private const val NOTIF_ID = 1001
        private const val TAG = "RifterMedia"

        private const val ACTION_CMD = "com.asn.rifterlog.MEDIA_CMD"
        private const val EXTRA_CMD = "cmd"
        private const val EXTRA_MEDIA_ID = "media_id"

        @Volatile
        private var instance: RifterMediaService? = null

        private val _nowPlayingFlow = MutableStateFlow(NowPlayingUiState())
        val nowPlayingFlow: StateFlow<NowPlayingUiState> = _nowPlayingFlow.asStateFlow()

        fun ensureStarted(context: Context) {
            if (instance == null) {
                try {
                    context.startService(Intent(context, RifterMediaService::class.java))
                } catch (_: Exception) {
                }
            }
        }

        fun notifyDataChanged(playlistId: Long? = null) {
            val srv = instance ?: return
            srv.app.appScope.launch {
                val playlists = srv.app.repo.getPlaylists()
                srv.mainHandler.post {
                    srv.notifyChildrenChanged(MediaIds.ROOT)
                    srv.notifyChildrenChanged(MediaIds.TAB_MUSIC)
                    srv.notifyChildrenChanged(MediaIds.FOLDER_RADIOS)
                    srv.notifyChildrenChanged(MediaIds.FOLDER_RADIOS_TURKISH)
                    srv.notifyChildrenChanged(MediaIds.FOLDER_RADIOS_KURDISH)
                    srv.notifyChildrenChanged(MediaIds.TAB_WEATHER_TRIP)
                    srv.notifyChildrenChanged(MediaIds.TAB_COCKPIT)
                    srv.notifyChildrenChanged(MediaIds.FOLDER_PARKING_HISTORY)
                    if (playlistId != null) {
                        srv.notifyChildrenChanged(MediaIds.playlistId(playlistId))
                    }
                    playlists.forEach { pl ->
                        srv.notifyChildrenChanged(MediaIds.playlistId(pl.id))
                    }
                    srv.refreshMetadataAndState()
                }
            }
        }

        fun previewStreamUrlFromPhone(context: Context, title: String, subtitle: String, streamUrl: String) {
            ensureStarted(context)
            val srv = instance ?: return
            val cleanUrl = com.asn.rifterlog.data.RadioStations.sanitizeStreamUrl(streamUrl)
            srv.mainHandler.post {
                srv.currentPlaylist = emptyList()
                srv.currentPlaylistId = -1L
                srv.currentRadioId = null
                srv.currentRadioTitle = "🎧 Test: $title"
                srv.currentRadioArtist = subtitle
                srv.currentErrorMessage = null
                srv.currentTrackDurationMs = 0L
                srv.session.isActive = true
                srv.player.shuffleModeEnabled = false
                srv.player.repeatMode = Player.REPEAT_MODE_OFF
                srv.player.setMediaItem(MediaItem.fromUri(Uri.parse(cleanUrl)))
                srv.player.prepare()
                srv.player.play()
                srv.updateMetadataRaw("🎧 Test: $title", "⏳ Bağlanıyor... ($subtitle)", 0L)
                srv.updatePlaybackState()
            }
        }

        fun playFromPhone(context: Context, mediaId: String) {
            val srv = instance
            if (srv != null) {
                srv.mainHandler.post {
                    srv.sessionCallback.onPlayFromMediaId(mediaId, null)
                }
            } else {
                val intent = Intent(context, RifterMediaService::class.java).apply {
                    action = ACTION_CMD
                    putExtra(EXTRA_CMD, "play_media_id")
                    putExtra(EXTRA_MEDIA_ID, mediaId)
                }
                context.startService(intent)
            }
        }

        fun playPlaylistFromPhone(context: Context, playlistId: Long, shuffle: Boolean) {
            val srv = instance
            if (srv != null) {
                srv.app.appScope.launch {
                    val songs = srv.app.repo.getSongs(playlistId)
                    if (songs.isEmpty()) return@launch
                    srv.app.settings.shuffleEnabled = shuffle
                    val startIdx = if (shuffle) songs.indices.random() else 0
                    srv.startPlaylistPlayback(playlistId, songs, startIdx, 0L)
                }
            } else {
                val mediaId = if (shuffle) MediaIds.shufflePlaylistId(playlistId)
                else "play_first/$playlistId"
                playFromPhone(context, mediaId)
            }
        }

        fun togglePlayPauseFromPhone(context: Context) {
            val srv = instance
            if (srv != null) {
                srv.mainHandler.post {
                    if (srv.player.isPlaying) {
                        srv.sessionCallback.onPause()
                    } else if (srv.currentPlaylist.isNotEmpty() || srv.currentRadioTitle != null) {
                        srv.sessionCallback.onPlay()
                    } else {
                        srv.handleResumeLastPlayed()
                    }
                }
            } else {
                val intent = Intent(context, RifterMediaService::class.java).apply {
                    action = ACTION_CMD
                    putExtra(EXTRA_CMD, "toggle")
                }
                context.startService(intent)
            }
        }

        fun skipNextFromPhone() {
            val srv = instance ?: return
            srv.mainHandler.post { srv.sessionCallback.onSkipToNext() }
        }

        fun skipPreviousFromPhone() {
            val srv = instance ?: return
            srv.mainHandler.post { srv.sessionCallback.onSkipToPrevious() }
        }

        fun seekToFromPhone(positionMs: Long) {
            val srv = instance ?: return
            srv.mainHandler.post { srv.sessionCallback.onSeekTo(positionMs) }
        }

        fun toggleShuffleFromPhone() {
            val srv = instance ?: return
            srv.mainHandler.post {
                srv.sessionCallback.onCustomAction(MediaIds.CUSTOM_ACTION_SHUFFLE, null)
            }
        }

        fun toggleRepeatFromPhone() {
            val srv = instance ?: return
            srv.mainHandler.post {
                srv.sessionCallback.onCustomAction(MediaIds.CUSTOM_ACTION_REPEAT, null)
            }
        }

        fun rescheduleAutoUpdater() {
            val srv = instance ?: return
            srv.mainHandler.post {
                srv.scheduleWeatherAutoUpdate()
            }
        }
    }

    private lateinit var session: MediaSessionCompat
    private lateinit var player: ExoPlayer
    private val mainHandler = Handler(Looper.getMainLooper())
    private val app get() = applicationContext as RifterApp

    // Anlık yüklü playlist şarkıları veya radyo durumu
    private var currentPlaylist: List<PlaylistSong> = emptyList()
    private var currentPlaylistId: Long = -1L
    private var currentRadioId: String? = null
    private var currentRadioTitle: String? = null
    private var currentRadioArtist: String? = null
    private var currentErrorMessage: String? = null
    private var currentTrackDurationMs: Long = 0L
    private var sessionStartMillis: Long = System.currentTimeMillis()
    @Volatile
    private var cachedSnapshot: com.asn.rifterlog.util.CockpitSnapshot? = null

    // Telefondaki Ayarlar'dan seçilen dakikaya göre (1, 3, 5, 10, 15 dk) Hava & Yol verisini sessizce güncelleyen görev
    private val weatherAutoUpdateRunnable = object : Runnable {
        override fun run() {
            mainHandler.removeCallbacks(this)
            if (!app.settings.autoUpdateEnabled) return

            refreshWeatherSnapshotAsync()

            val intervalMs = (app.settings.autoUpdateIntervalMinutes.coerceIn(1, 60)) * 60_000L
            mainHandler.postDelayed(this, intervalMs)
        }
    }

    // Çalarken her 5 saniyede bir pozisyonu kaydeden görev
    private val positionSaveRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying && currentRadioTitle == null) {
                saveCurrentPosition()
                mainHandler.postDelayed(this, 5000L)
            }
        }
    }

    // Telefondaki ilerleme çubuğunu (Slider) her 1 saniyede bir güncelleyen görev
    private val uiProgressRunnable = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                syncUiState()
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionStartMillis = System.currentTimeMillis()
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { p ->
                p.shuffleModeEnabled = app.settings.shuffleEnabled
                p.repeatMode = if (app.settings.repeatOneEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                p.addListener(playerListener)
            }

        session = MediaSessionCompat(this, "RifterLog").apply {
            setCallback(sessionCallback)
            isActive = true
        }
        sessionToken = session.sessionToken
        publishIdle()

        // Varsayılan radyoların veritabanında yüklü olduğundan emin ol
        app.appScope.launch {
            app.repo.ensureDefaultRadiosSeeded()
        }

        // Son çalınanı geri yükle
        restoreLastPlayed()

        // Arka planda ilk hava durumu ve rakım verisini hazırla (araç ekranında 0 saniyede görünsün)
        refreshWeatherSnapshotAsync()
        scheduleWeatherAutoUpdate()
    }

    override fun onDestroy() {
        instance = null
        saveCurrentPosition()
        mainHandler.removeCallbacksAndMessages(null)
        player.release()
        session.release()
        super.onDestroy()
    }

    private fun scheduleWeatherAutoUpdate() {
        mainHandler.removeCallbacks(weatherAutoUpdateRunnable)
        if (app.settings.autoUpdateEnabled) {
            val intervalMs = (app.settings.autoUpdateIntervalMinutes.coerceIn(1, 60)) * 60_000L
            mainHandler.postDelayed(weatherAutoUpdateRunnable, intervalMs)
        }
    }

    private fun refreshWeatherSnapshotAsync() {
        app.appScope.launch {
            if (ParkingLocationHelper.hasLocationPermission(this@RifterMediaService)) {
                val fresh = CockpitWeatherHelper.fetchCockpitSnapshot(this@RifterMediaService)
                if (fresh != null) {
                    cachedSnapshot = fresh
                    mainHandler.post {
                        notifyChildrenChanged(MediaIds.TAB_WEATHER_TRIP)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CMD) {
            when (intent.getStringExtra(EXTRA_CMD)) {
                "play_media_id" -> {
                    val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)
                    if (!mediaId.isNullOrBlank()) {
                        mainHandler.post { sessionCallback.onPlayFromMediaId(mediaId, null) }
                    }
                }
                "toggle" -> {
                    mainHandler.post {
                        if (player.isPlaying) sessionCallback.onPause()
                        else if (currentPlaylist.isNotEmpty() || currentRadioTitle != null) sessionCallback.onPlay()
                        else handleResumeLastPlayed()
                    }
                }
            }
        } else {
            MediaButtonReceiver.handleIntent(session, intent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        Log.i(TAG, "onGetRoot caller=$clientPackageName")
        val extras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
        }
        return BrowserRoot(MediaIds.ROOT, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        app.appScope.launch {
            val items = when {
                // ROOT → Araç Ekranının Tepesinde 3 Doğrudan Sekme:
                // 1) 🎵 Müziklerim   2) 🌤️ Hava & Yol   3) 📍 Park Yeri
                parentId == MediaIds.ROOT -> {
                    listOf(
                        createBrowsableItem(MediaIds.TAB_MUSIC, "🎵 Müziklerim", "Çalma listeleri ve canlı radyolar"),
                        createBrowsableItem(MediaIds.TAB_WEATHER_TRIP, "🌤️ Hava & Yol", "Anlık Hava Durumu, GPS Hız, Rakım ve Süre"),
                        createBrowsableItem(MediaIds.TAB_COCKPIT, "📍 Park Yeri", "Park konumu kaydet, son park ve geçmiş")
                    )
                }

                // SEKME 1: Müziklerim (Sadece Müzik: Devam Et + Listeler + Canlı Radyolar)
                parentId == MediaIds.TAB_MUSIC -> {
                    val list = mutableListOf<MediaBrowserCompat.MediaItem>()

                    val lastPlId = app.settings.lastPlaylistId
                    val lastTitle = app.settings.lastSongTitle
                    val lastPos = app.settings.lastPositionMs
                    if (lastPlId >= 0 && lastTitle.isNotBlank()) {
                        list.add(
                            createPlayableItem(
                                MediaIds.ACTION_RESUME,
                                "▶️ Devam Et: $lastTitle",
                                "Kaldığı yer: ${formatDurationShort(lastPos)}"
                            )
                        )
                    }

                    val playlists = app.repo.getPlaylists()
                    if (playlists.isEmpty()) {
                        list.add(
                            createPlayableItem(
                                MediaIds.ACTION_NOOP,
                                "Henüz MP3 listesi yok",
                                "Telefon uygulamasından + ile liste oluşturun"
                            )
                        )
                    } else {
                        playlists.forEach { pl ->
                            val count = app.repo.songCount(pl.id)
                            list.add(
                                createBrowsableItem(
                                    MediaIds.playlistId(pl.id),
                                    "📂 ${pl.name}",
                                    "$count şarkı"
                                )
                            )
                        }
                    }

                    // En altta tek bir klasör olarak Canlı Radyolar (Türkçe & Kürtçe kategorili)
                    val trCount = app.repo.getRadioStations(RadioCategory.TURKISH).size
                    val kuCount = app.repo.getRadioStations(RadioCategory.KURDISH).size
                    list.add(
                        createBrowsableItem(
                            MediaIds.FOLDER_RADIOS,
                            "📻 Canlı İnternet Radyoları",
                            "Türkçe ($trCount) ve ☀️ Kürtçe ($kuCount) Radyolar"
                        )
                    )
                    list
                }

                // Canlı Radyolar Ana Klasörü → Türkçe ve Kürtçe Kategorileri
                parentId == MediaIds.FOLDER_RADIOS -> {
                    val trCount = app.repo.getRadioStations(RadioCategory.TURKISH).size
                    val kuCount = app.repo.getRadioStations(RadioCategory.KURDISH).size
                    listOf(
                        createBrowsableItem(
                            MediaIds.FOLDER_RADIOS_TURKISH,
                            "Türkçe FM Radyoları",
                            "$trCount ulusal ve özel radyo kanalı"
                        ),
                        createBrowsableItem(
                            MediaIds.FOLDER_RADIOS_KURDISH,
                            "☀️ Kürtçe FM Radyoları (Kurdî)",
                            "$kuCount Kürtçe ve bölgesel radyo kanalı"
                        )
                    )
                }

                parentId == MediaIds.FOLDER_RADIOS_TURKISH -> {
                    app.repo.getRadioStations(RadioCategory.TURKISH).mapIndexed { idx, st ->
                        createPlayableItem(MediaIds.radioId(st.id), "${idx + 1}. ${st.name}", st.genre)
                    }
                }

                parentId == MediaIds.FOLDER_RADIOS_KURDISH -> {
                    app.repo.getRadioStations(RadioCategory.KURDISH).mapIndexed { idx, st ->
                        createPlayableItem(MediaIds.radioId(st.id), "${idx + 1}. ${st.name}", st.genre)
                    }
                }

                // playlist/{id} → O listenin şarkıları
                parentId.startsWith("playlist/") -> {
                    val plId = MediaIds.parsePlaylistId(parentId) ?: return@launch
                    val songs = app.repo.getSongs(plId)
                    val list = mutableListOf<MediaBrowserCompat.MediaItem>()
                    if (songs.isEmpty()) {
                        list.add(
                            createPlayableItem(
                                MediaIds.ACTION_NOOP,
                                "Bu liste boş",
                                "Telefon uygulamasından + ile şarkı ekleyin"
                            )
                        )
                    } else {
                        if (songs.size >= 2) {
                            list.add(
                                createPlayableItem(
                                    MediaIds.shufflePlaylistId(plId),
                                    "🔀 Bu Listeyi Karışık Çal",
                                    "${songs.size} şarkıyı karıştırarak başlatır"
                                )
                            )
                        }
                        songs.forEach { song ->
                            list.add(
                                createPlayableItem(
                                    MediaIds.songId(plId, song.id),
                                    song.title,
                                    song.artist
                                )
                            )
                        }
                    }
                    list
                }

                // SEKME 2: 🌤️ Hava & Yol
                parentId == MediaIds.TAB_WEATHER_TRIP || parentId == MediaIds.FOLDER_TRIP_WEATHER -> {
                    // Sekmeye her girildiğinde arka planda veriyi tazele
                    refreshWeatherSnapshotAsync()
                    buildTripAndWeatherItems(forceRefresh = false)
                }

                // Bilgi amaçlı satırlara dokunulduğunda iç içe derin menü oluşturulmaz,
                // sadece arka planda veri yenilenir ve tek bir geri tuşuyla dönülecek şekilde boş liste döner ("No items")
                parentId == MediaIds.DETAIL_WEATHER ||
                    parentId == MediaIds.DETAIL_SPEED_ALT ||
                    parentId == MediaIds.DETAIL_DURATION ||
                    parentId == MediaIds.ACTION_REFRESH_WEATHER -> {
                    refreshWeatherSnapshotAsync()
                    emptyList()
                }

                // SEKME 3: 📍 Park Yeri
                parentId == MediaIds.TAB_COCKPIT -> {
                    val parked = app.settings.getParkedLocation()
                    val parkedTitle = if (parked != null) {
                        val headline = parked.title.ifBlank { parked.address }
                        "📌 Son Park: $headline"
                    } else {
                        "📌 Son Park: Henüz Kayıt Yok"
                    }
                    val parkedSub = if (parked != null) {
                        "${ParkingLocationHelper.formatTimestampWithDay(parked.timestampMillis)} · ${parked.address}"
                    } else {
                        "Kaydetmek için üstteki seçeneğe dokunun"
                    }

                    listOf(
                        createBrowsableItem(
                            MediaIds.ACTION_SAVE_PARKING,
                            "📍 Aracı Şimdi Park Ettim (Kaydet)",
                            "Dokununca mevcut GPS konumunu kaydeder ve telefona bildirim atar"
                        ),
                        createInfoItem(
                            MediaIds.ACTION_INFO_PARKING,
                            parkedTitle,
                            parkedSub
                        ),
                        createBrowsableItem(
                            MediaIds.FOLDER_PARKING_HISTORY,
                            "🕓 Eski Park Geçmişim",
                            "Daha önce park ettiğiniz son 10 konum (gün ve saat)"
                        )
                    )
                }

                // "📌 Son Park" satırına dokunulursa "No items" yerine tam park detayını göster
                parentId == MediaIds.ACTION_INFO_PARKING -> {
                    val parked = app.settings.getParkedLocation()
                    if (parked == null) {
                        listOf(
                            createInfoItem(
                                "info_no_park",
                                "⚠️ Kayıtlı Park Konumu Yok",
                                "Sol üstteki geri (←) tuşuyla dönüp 'Aracı Şimdi Park Ettim'e dokunun"
                            )
                        )
                    } else {
                        val timeStr = ParkingLocationHelper.formatTimestampWithDay(parked.timestampMillis)
                        listOf(
                            createInfoItem(
                                "info_park_addr",
                                "📍 ${parked.address}",
                                "🗓️ $timeStr"
                            ),
                            createInfoItem(
                                "info_park_gps",
                                "🌐 GPS: %.6f, %.6f".format(Locale.US, parked.latitude, parked.longitude),
                                if (parked.title.isNotBlank() || parked.note.isNotBlank()) "🏷️ ${parked.title} · 📝 ${parked.note}" else "Tam uydu koordinatı"
                            )
                        )
                    }
                }

                // "📍 Aracı Şimdi Park Ettim (Kaydet)" (İçindeki satırlar salt okunurdur, iç içe sayfa açmaz)
                parentId == MediaIds.ACTION_SAVE_PARKING -> {
                    executeSaveParkingAndBuildItems()
                }

                // "🕓 Eski Park Geçmişim" (İçindeki kayıtlar salt okunurdur, tıklanınca başka yere gitmez)
                parentId == MediaIds.FOLDER_PARKING_HISTORY -> {
                    val history = app.repo.getRecentParkingHistory()
                    if (history.isEmpty()) {
                        listOf(
                            createInfoItem(
                                "empty_parking_history",
                                "Henüz park geçmişi yok",
                                "Park ettikçe gün ve saat olarak burada listelenir"
                            )
                        )
                    } else {
                        history.mapIndexed { idx, rec ->
                            val dayTime = ParkingLocationHelper.formatTimestampWithDay(rec.timestampMillis)
                            val titleLine = if (rec.title.isNotBlank()) {
                                "${idx + 1}. 🏷️ ${rec.title} — ${rec.address}"
                            } else {
                                "${idx + 1}. ${rec.address}"
                            }
                            val subLine = if (rec.note.isNotBlank()) {
                                "📝 ${rec.note} · 🗓️ $dayTime"
                            } else {
                                "🗓️ $dayTime"
                            }
                            createInfoItem(
                                MediaIds.parkingHistoryItemId(rec.id),
                                titleLine,
                                subLine
                            )
                        }
                    }
                }

                else -> emptyList()
            }
            mainHandler.post { result.sendResult(items) }
        }
    }

    private suspend fun executeSaveParkingAndBuildItems(): List<MediaBrowserCompat.MediaItem> {
        if (!ParkingLocationHelper.hasLocationPermission(this)) {
            return listOf(
                createInfoItem(
                    "info_parking_perm_needed",
                    "⚠️ Konum İzni Gerekli",
                    "Telefonunuzdan Rifter Log uygulamasını açıp konum izni verin"
                )
            )
        }

        val locPair = ParkingLocationHelper.fetchCurrentLocation(this)
        return if (locPair != null) {
            val (loc, addr) = locPair
            val saved = app.settings.saveParkedLocation(loc.latitude, loc.longitude, addr)
            app.repo.addParkingRecord(loc.latitude, loc.longitude, addr, saved.timestampMillis)

            val timeStr = ParkingLocationHelper.formatTimestampWithDay(saved.timestampMillis)
            ParkingNotifier.showParkedNotification(this, saved)

            mainHandler.post {
                notifyChildrenChanged(MediaIds.TAB_COCKPIT)
                notifyChildrenChanged(MediaIds.FOLDER_PARKING_HISTORY)
            }

            listOf(
                createInfoItem(
                    "info_parking_saved_ok",
                    "✅ Park Konumu Kaydedildi!",
                    "$timeStr · Telefona bildirim gönderildi"
                ),
                createInfoItem(
                    "info_parking_saved_addr",
                    "📍 $addr",
                    "Koordinat: %.5f, %.5f".format(Locale.US, loc.latitude, loc.longitude)
                )
            )
        } else {
            listOf(
                createInfoItem(
                    "info_parking_gps_error",
                    "⚠️ GPS Konumu Alınamadı",
                    "Telefonunuzun Konum (GPS) özelliğinin açık olduğundan emin olun"
                )
            )
        }
    }

    private suspend fun buildTripAndWeatherItems(forceRefresh: Boolean = false): List<MediaBrowserCompat.MediaItem> {
        val elapsedMinutes = ((System.currentTimeMillis() - sessionStartMillis) / 60000L).coerceAtLeast(1L)
        val hours = elapsedMinutes / 60
        val mins = elapsedMinutes % 60
        val durationText = if (hours > 0) "${hours} sa ${mins} dk" else "${mins} dk"
        val autoInfo = if (app.settings.autoUpdateEnabled) {
            "Otomatik güncelleme: Her ${app.settings.autoUpdateIntervalMinutes} dk"
        } else {
            "Aktif sürüş oturumu"
        }
        val restHint = if (elapsedMinutes >= 120) "⚠️ 2 saati geçti, mola önerilir · $autoInfo" else autoInfo

        val list = mutableListOf<MediaBrowserCompat.MediaItem>()

        if (!ParkingLocationHelper.hasLocationPermission(this)) {
            list.add(
                createInfoItem(
                    "info_weather_perm",
                    "⚠️ Hava, Hız ve Rakım İçin Konum İzni Gerekli",
                    "Telefon uygulamasından konum izni verin"
                )
            )
            return list
        }

        val snap = if (forceRefresh) {
            CockpitWeatherHelper.fetchCockpitSnapshot(this)?.also { cachedSnapshot = it } ?: cachedSnapshot
        } else {
            cachedSnapshot ?: CockpitWeatherHelper.fetchCockpitSnapshot(this)?.also { cachedSnapshot = it }
        }

        if (snap != null) {
            val timeStr = SimpleDateFormat("HH:mm", Locale("tr", "TR")).format(Date(snap.updatedAtMillis))
            if (snap.temperatureC != null && snap.weatherDesc != null) {
                list.add(
                    createInfoItem(
                        MediaIds.DETAIL_WEATHER,
                        "${snap.weatherDesc} · ${snap.temperatureC}°C",
                        "💨 Rüzgar: ${snap.windSpeedKmh ?: 0.0} km/s · 📍 ${snap.address}"
                    )
                )
            } else {
                list.add(
                    createInfoItem(
                        MediaIds.DETAIL_WEATHER,
                        "📍 ${snap.address}",
                        "Hava durumu için internet bağlantısı bekleniyor"
                    )
                )
            }
            list.add(
                createInfoItem(
                    MediaIds.DETAIL_SPEED_ALT,
                    "🚀 Hız: ${snap.speedKmh} km/s · 🏔️ Rakım: ${snap.altitudeMeters} m",
                    "Son güncelleme: $timeStr"
                )
            )
        } else {
            list.add(
                createInfoItem(
                    "info_weather_waiting",
                    "⚠️ GPS Verisi Bekleniyor",
                    "Telefonunuzun konum (GPS) servisinin açık olduğundan emin olun"
                )
            )
        }

        list.add(
            createInfoItem(
                MediaIds.DETAIL_DURATION,
                "⏱️ Sürüş Süresi: $durationText",
                restHint
            )
        )
        return list
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            Log.i(TAG, "onPlayFromMediaId: $mediaId")

            if (mediaId == MediaIds.ACTION_NOOP) return

            if (mediaId == MediaIds.ACTION_REFRESH_WEATHER) {
                refreshWeatherSnapshotAsync()
                return
            }

            if (mediaId == MediaIds.ACTION_RESUME) {
                handleResumeLastPlayed()
                return
            }

            if (mediaId.startsWith("play_first/")) {
                val plId = mediaId.removePrefix("play_first/").toLongOrNull() ?: return
                app.appScope.launch {
                    val songs = app.repo.getSongs(plId)
                    if (songs.isEmpty()) return@launch
                    app.settings.shuffleEnabled = false
                    startPlaylistPlayback(plId, songs, 0, 0L)
                }
                return
            }

            val shufflePlId = MediaIds.parseShufflePlaylistId(mediaId)
            if (shufflePlId != null) {
                handleShufflePlaylist(shufflePlId)
                return
            }

            val radioId = MediaIds.parseRadioId(mediaId)
            if (radioId != null) {
                handlePlayRadio(radioId)
                return
            }

            val (plId, songId) = MediaIds.parseSongId(mediaId) ?: return

            app.appScope.launch {
                val songs = app.repo.getSongs(plId)
                if (songs.isEmpty()) return@launch
                val startIndex = songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
                startPlaylistPlayback(plId, songs, startIndex, 0L)
            }
        }

        override fun onPlay() {
            session.isActive = true
            player.shuffleModeEnabled = app.settings.shuffleEnabled
            if (player.playbackState == Player.STATE_IDLE && (currentPlaylist.isNotEmpty() || currentRadioTitle != null)) {
                player.prepare()
            }
            player.play()
            updatePlaybackState()
        }

        override fun onPause() {
            saveCurrentPosition()
            player.pause()
            updatePlaybackState()
        }

        override fun onStop() {
            saveCurrentPosition()
            player.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            updatePlaybackState()
        }

        override fun onSkipToNext() {
            if (currentRadioId != null) {
                skipRadioStation(+1)
                return
            }
            if (player.mediaItemCount > 0) {
                player.seekToNext()
                player.play()
            }
        }

        override fun onSkipToPrevious() {
            if (currentRadioId != null) {
                skipRadioStation(-1)
                return
            }
            if (player.mediaItemCount > 0) {
                player.seekToPrevious()
                player.play()
            }
        }

        override fun onSeekTo(pos: Long) {
            if (currentRadioTitle != null) return
            player.seekTo(pos)
            saveCurrentPosition()
            updatePlaybackState()
        }

        override fun onSetShuffleMode(shuffleMode: Int) {
            val enabled = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
            app.settings.shuffleEnabled = enabled
            player.shuffleModeEnabled = enabled
            updatePlaybackState()
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            val repeatOne = repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE
            app.settings.repeatOneEnabled = repeatOne
            player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
            updatePlaybackState()
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                MediaIds.CUSTOM_ACTION_SHUFFLE -> {
                    val newShuffle = !player.shuffleModeEnabled
                    app.settings.shuffleEnabled = newShuffle
                    player.shuffleModeEnabled = newShuffle
                    updatePlaybackState()
                }
                MediaIds.CUSTOM_ACTION_REPEAT -> {
                    val newRepeatOne = player.repeatMode != Player.REPEAT_MODE_ONE
                    app.settings.repeatOneEnabled = newRepeatOne
                    player.repeatMode = if (newRepeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                    updatePlaybackState()
                }
            }
        }
    }

    private fun skipRadioStation(delta: Int) {
        val currentId = currentRadioId ?: return
        app.appScope.launch {
            val currentStation = app.repo.getRadioStationById(currentId) ?: return@launch
            val categoryList = app.repo.getRadioStations(currentStation.categoryEnum)
            if (categoryList.isEmpty()) return@launch
            val curIdx = categoryList.indexOfFirst { it.id == currentStation.id }.coerceAtLeast(0)
            val nextIdx = (curIdx + delta + categoryList.size) % categoryList.size
            handlePlayRadio(categoryList[nextIdx].id)
        }
    }

    private fun handlePlayRadio(stationId: String) {
        app.appScope.launch {
            val station = app.repo.getRadioStationById(stationId) ?: return@launch
            val cleanStreamUrl = com.asn.rifterlog.data.RadioStations.sanitizeStreamUrl(station.streamUrl)
            mainHandler.post {
                currentPlaylist = emptyList()
                currentPlaylistId = -1L
                currentRadioId = station.id
                currentRadioTitle = station.name
                currentRadioArtist = station.genre
                currentErrorMessage = null
                currentTrackDurationMs = 0L

                session.isActive = true
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.setMediaItem(MediaItem.fromUri(Uri.parse(cleanStreamUrl)))
                player.prepare()
                player.play()

                updateMetadataRaw(station.name, station.genre, 0L)
                updatePlaybackState()
            }
        }
    }

    private fun startPlaylistPlayback(plId: Long, songs: List<PlaylistSong>, startIndex: Int, startPosMs: Long) {
        currentRadioId = null
        currentRadioTitle = null
        currentRadioArtist = null
        currentErrorMessage = null
        currentPlaylist = songs
        currentPlaylistId = plId
        val mediaItems = songs.map { MediaItem.fromUri(Uri.parse(it.uri)) }
        val targetSong = songs[startIndex]
        currentTrackDurationMs = targetSong.durationMs

        mainHandler.post {
            session.isActive = true
            player.shuffleModeEnabled = app.settings.shuffleEnabled
            player.repeatMode = if (app.settings.repeatOneEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
            player.setMediaItems(mediaItems, startIndex, startPosMs)
            player.prepare()
            player.play()

            updateMetadata(targetSong)
            updatePlaybackState()

            app.settings.lastPlaylistId = plId
            app.settings.lastSongIndex = startIndex
            app.settings.lastPositionMs = startPosMs
            app.settings.lastSongTitle = targetSong.title
            app.settings.lastSongArtist = targetSong.artist
            notifyChildrenChanged(MediaIds.TAB_MUSIC)
        }
    }

    private fun handleResumeLastPlayed() {
        val plId = app.settings.lastPlaylistId
        val idx = app.settings.lastSongIndex
        val posMs = app.settings.lastPositionMs
        app.appScope.launch {
            val targetPlId = if (plId >= 0) plId else app.repo.getPlaylists().firstOrNull()?.id ?: return@launch
            val songs = app.repo.getSongs(targetPlId)
            if (songs.isEmpty()) return@launch
            val safeIdx = idx.coerceIn(0, songs.size - 1)
            startPlaylistPlayback(targetPlId, songs, safeIdx, posMs)
        }
    }

    private fun handleShufflePlaylist(plId: Long) {
        app.appScope.launch {
            val songs = app.repo.getSongs(plId)
            if (songs.isEmpty()) return@launch
            app.settings.shuffleEnabled = true
            val randomIdx = songs.indices.random()
            startPlaylistPlayback(plId, songs, randomIdx, 0L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                currentErrorMessage = null
            }
            updatePlaybackState()
            mainHandler.removeCallbacks(positionSaveRunnable)
            mainHandler.removeCallbacks(uiProgressRunnable)
            if (isPlaying) {
                scheduleWeatherAutoUpdate()
                mainHandler.postDelayed(uiProgressRunnable, 1000L)
                if (currentRadioTitle == null) {
                    mainHandler.postDelayed(positionSaveRunnable, 5000L)
                }
            } else if (currentRadioTitle == null) {
                saveCurrentPosition()
                notifyChildrenChanged(MediaIds.TAB_MUSIC)
            }

            val rTitle = currentRadioTitle
            val rArtist = currentRadioArtist
            if (rTitle != null && rArtist != null) {
                val displaySub = currentErrorMessage?.let { "❌ $it" } ?: rArtist
                updateMetadataRaw(rTitle, displaySub, 0L)
                if (isPlaying) promoteToForeground(rTitle, rArtist)
                else getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(rTitle, displaySub))
                return
            }

            val idx = player.currentMediaItemIndex
            val song = currentPlaylist.getOrNull(idx) ?: return
            updateMetadata(song)
            if (isPlaying) {
                promoteToForeground(song.title, song.artist)
            } else {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIF_ID, buildNotification(song.title, song.artist))
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && player.isPlaying) {
                currentErrorMessage = null
            }
            updatePlaybackState()
        }

        override fun onPlayerError(error: PlaybackException) {
            val friendlyMsg = formatPlaybackError(error)
            currentErrorMessage = friendlyMsg
            val targetName = currentRadioTitle ?: "Medya"
            Log.w(TAG, "Yayın hatası ($targetName): ${error.errorCodeName} - $friendlyMsg", error)

            // Hem araç ekranında (Android Auto Metadata) hem telefonda (Toast + Durum Çubuğu) hatayı açıkça göster
            updateMetadataRaw("⚠️ Açılamadı: $targetName", "❌ $friendlyMsg", 0L)
            updatePlaybackState()
            Toast.makeText(
                this@RifterMediaService,
                "❌ $targetName açılamadı:\n$friendlyMsg",
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updatePlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (currentRadioTitle != null) return
            val idx = player.currentMediaItemIndex
            val song = currentPlaylist.getOrNull(idx) ?: return
            currentTrackDurationMs = song.durationMs
            updateMetadata(song)
            updatePlaybackState()
            app.settings.lastSongIndex = idx
            app.settings.lastPositionMs = 0L
            app.settings.lastSongTitle = song.title
            app.settings.lastSongArtist = song.artist
            notifyChildrenChanged(MediaIds.TAB_MUSIC)
            if (player.isPlaying) {
                promoteToForeground(song.title, song.artist)
            }
        }
    }

    private fun promoteToForeground(title: String, artist: String) {
        val notif = buildNotification(title, artist)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun restoreLastPlayed() {
        val plId = app.settings.lastPlaylistId
        if (plId < 0) return
        val idx = app.settings.lastSongIndex
        val posMs = app.settings.lastPositionMs

        app.appScope.launch {
            val songs = app.repo.getSongs(plId)
            if (songs.isEmpty()) return@launch
            currentPlaylist = songs
            currentPlaylistId = plId
            val safeIdx = idx.coerceIn(0, songs.size - 1)
            val song = songs[safeIdx]
            currentTrackDurationMs = song.durationMs
            app.settings.lastSongTitle = song.title
            app.settings.lastSongArtist = song.artist
            val mediaItems = songs.map { MediaItem.fromUri(Uri.parse(it.uri)) }
            mainHandler.post {
                player.shuffleModeEnabled = app.settings.shuffleEnabled
                player.repeatMode = if (app.settings.repeatOneEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
                player.setMediaItems(mediaItems, safeIdx, posMs)
                player.prepare()
                updateMetadata(song)
                updatePlaybackState()
                Log.i(TAG, "Son çalınan geri yüklendi: ${song.title} @ ${posMs}ms")
            }
        }
    }

    private fun saveCurrentPosition() {
        if (currentPlaylist.isEmpty() || !::player.isInitialized || currentRadioTitle != null) return
        val idx = player.currentMediaItemIndex
        val song = currentPlaylist.getOrNull(idx)
        app.settings.lastSongIndex = idx
        app.settings.lastPositionMs = player.currentPosition.coerceAtLeast(0L)
        if (song != null) {
            app.settings.lastSongTitle = song.title
            app.settings.lastSongArtist = song.artist
        }
    }

    private fun refreshMetadataAndState() {
        val rTitle = currentRadioTitle
        val rArtist = currentRadioArtist
        if (rTitle != null && rArtist != null) {
            updateMetadataRaw(rTitle, rArtist, 0L)
        } else {
            val idx = if (::player.isInitialized) player.currentMediaItemIndex else 0
            val song = currentPlaylist.getOrNull(idx)
            if (song != null) {
                updateMetadata(song)
            }
        }
        if (::player.isInitialized) {
            updatePlaybackState()
        }
    }

    private fun updateMetadata(song: PlaylistSong) {
        currentTrackDurationMs = song.durationMs
        updateMetadataRaw(song.title, song.artist, song.durationMs)
    }

    private fun updateMetadataRaw(title: String, artist: String, durationMs: Long) {
        val isDark = app.settings.themeMode != Settings.THEME_LIGHT
        val artBitmap = buildCockpitArtwork(title, isRadio = currentRadioTitle != null, isDark = isDark)

        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artBitmap)
        if (durationMs > 0) {
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        }
        session.setMetadata(builder.build())

        syncUiState(titleOverride = title, artistOverride = artist, durationOverride = durationMs)
    }

    private fun syncUiState(
        titleOverride: String? = null,
        artistOverride: String? = null,
        durationOverride: Long? = null
    ) {
        val cur = _nowPlayingFlow.value
        val title = titleOverride ?: cur.title
        val artist = artistOverride ?: cur.artist
        if (title.isBlank()) return

        val pos = if (::player.isInitialized && currentRadioTitle == null) {
            player.currentPosition.coerceAtLeast(0L)
        } else 0L
        val dur = durationOverride ?: if (::player.isInitialized && player.duration > 0) {
            player.duration
        } else currentTrackDurationMs

        _nowPlayingFlow.value = NowPlayingUiState(
            title = title,
            artist = artist,
            isPlaying = ::player.isInitialized && player.isPlaying,
            isBuffering = ::player.isInitialized && player.playbackState == Player.STATE_BUFFERING,
            isRadio = currentRadioTitle != null,
            hasMedia = true,
            positionMs = pos,
            durationMs = dur.coerceAtLeast(0L),
            shuffleEnabled = if (::player.isInitialized) player.shuffleModeEnabled else app.settings.shuffleEnabled,
            repeatOneEnabled = if (::player.isInitialized) player.repeatMode == Player.REPEAT_MODE_ONE else app.settings.repeatOneEnabled,
            errorMessage = currentErrorMessage
        )
    }

    private fun formatPlaybackError(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "Geçersiz yayın formatı! Normal web sitesi yerine doğrudan ses akış linki (.mp3, .m3u8, /stream) girilmelidir."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Yayın linki kapanmış veya sunucu hata verdi (HTTP Bağlantı Hatası)."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Radyo sunucusuna bağlanılamadı (Yayın şu an kapalı veya internet yok)."
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "Güvenli olmayan (HTTP) bağlantı engellendi."
            else ->
                "Bu radyo yayını şu an çalışmıyor (${error.errorCodeName})."
        }
    }

    /**
     * Araç ekranındaki boş gri kutuyu dolduran ve Android Auto arka plan rengini
     * Gece (göz almayan koyu karbon/lacivert) veya Gündüz temasına göre ayarlayan özel Rifter Kokpit görseli.
     */
    private fun buildCockpitArtwork(title: String, isRadio: Boolean, isDark: Boolean): Bitmap {
        val size = 320
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgStart = if (isDark) Color.parseColor("#0B0F17") else Color.parseColor("#0284C7")
        val bgEnd = if (isDark) Color.parseColor("#162436") else Color.parseColor("#38BDF8")
        val accentColor = if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#FFFFFF")

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), bgStart, bgEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Kokpit halka çerçevesi
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = accentColor
            alpha = if (isDark) 140 else 200
        }
        canvas.drawRoundRect(RectF(20f, 20f, size - 20f, size - 20f), 36f, 36f, ringPaint)

        // Üst başlık: RIFTER LOG
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 26f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("PEUGEOT RIFTER", size / 2f, 76f, headerPaint)

        // Orta ikon/yazı
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val symbol = if (isRadio) "📻" else "🎵"
        canvas.drawText(symbol, size / 2f, 180f, centerPaint)

        // Alt durum: Gece / Gündüz etiketi
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#E0F2FE")
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val modeLabel = if (isDark) "🌙 Gece Kokpit" else "☀️ Gündüz Kokpit"
        canvas.drawText(modeLabel, size / 2f, 265f, subPaint)

        return bmp
    }

    private fun updatePlaybackState() {
        val state = when {
            player.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val speed = if (player.isPlaying) player.playbackParameters.speed else 0f
        val shuffleEnabled = player.shuffleModeEnabled
        val repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE

        val shuffleLabel = if (shuffleEnabled) "Karıştır: Açık" else "Karıştır: Kapalı"
        val repeatLabel = if (repeatOne) "Tekrar: Tek Şarkı" else "Tekrar: Liste"
        val repeatIcon = if (repeatOne) R.drawable.ic_repeat_one else R.drawable.ic_repeat

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE
            )
            .setState(state, if (currentRadioTitle != null) 0L else player.currentPosition, speed)
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    MediaIds.CUSTOM_ACTION_SHUFFLE,
                    shuffleLabel,
                    R.drawable.ic_shuffle
                ).build()
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    MediaIds.CUSTOM_ACTION_REPEAT,
                    repeatLabel,
                    repeatIcon
                ).build()
            )

        session.setPlaybackState(stateBuilder.build())
        session.setShuffleMode(
            if (shuffleEnabled) PlaybackStateCompat.SHUFFLE_MODE_ALL
            else PlaybackStateCompat.SHUFFLE_MODE_NONE
        )
        session.setRepeatMode(
            if (repeatOne) PlaybackStateCompat.REPEAT_MODE_ONE
            else PlaybackStateCompat.REPEAT_MODE_ALL
        )

        syncUiState()
    }

    private fun publishIdle() {
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Rifter Log")
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Müzik & Kokpit Asistanı")
                .build()
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0f)
                .build()
        )
    }

    private fun buildNotification(title: String, artist: String): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(player.isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(android.R.drawable.ic_media_previous, "Önceki",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Duraklat" else "Oynat",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, "Sonraki",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIF_CHANNEL_ID, "Rifter Log Müzik",
            NotificationManager.IMPORTANCE_LOW).apply { description = "Müzik çalma bildirimi" }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun createBrowsableItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createPlayableItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build()
        return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    /**
     * Tıklanınca ne alt klasöre giren ne de müzik çalar ekranına atan salt-okunur (flags = 0) bilgi satırı.
     */
    private fun createInfoItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val desc = MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build()
        return MediaBrowserCompat.MediaItem(desc, 0)
    }

    private fun formatDurationShort(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }
}
