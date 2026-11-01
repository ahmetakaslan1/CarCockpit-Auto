package com.asn.rifterlog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.asn.rifterlog.data.DeviceSong
import com.asn.rifterlog.data.ParkedLocation
import com.asn.rifterlog.data.ParkingRecord
import com.asn.rifterlog.data.Playlist
import com.asn.rifterlog.data.PlaylistRepository
import com.asn.rifterlog.data.PlaylistSong
import com.asn.rifterlog.data.OnlineRadioCandidate
import com.asn.rifterlog.data.RadioCategory
import com.asn.rifterlog.data.RadioStation
import com.asn.rifterlog.data.RadioStations
import com.asn.rifterlog.data.Settings
import com.asn.rifterlog.media.MediaIds
import com.asn.rifterlog.media.NowPlayingUiState
import com.asn.rifterlog.media.RifterMediaService
import com.asn.rifterlog.ui.theme.RifterLogTheme
import com.asn.rifterlog.util.CockpitSnapshot
import com.asn.rifterlog.util.CockpitWeatherHelper
import com.asn.rifterlog.util.ParkingLocationHelper
import com.asn.rifterlog.util.ParkingNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RifterApp
        RifterMediaService.ensureStarted(this)
        setContent {
            val themeMode by app.settings.themeModeFlow.collectAsState()
            RifterLogTheme(themeMode = themeMode) {
                MainScreen(repo = app.repo, settings = app.settings, themeMode = themeMode)
            }
        }
    }
}

sealed class Screen {
    object Playlists : Screen()
    data class Songs(val playlist: Playlist) : Screen()
    data class Picker(val playlist: Playlist) : Screen()
    object ParkingHistory : Screen()
    object Radios : Screen()
}

data class ConfirmationDialogRequest(
    val title: String,
    val message: String,
    val confirmButtonText: String = "Evet, Sil",
    val dismissButtonText: String = "Vazgeç",
    val isDestructive: Boolean = true,
    val iconEmoji: String = "⚠️",
    val onConfirm: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repo: PlaylistRepository, settings: Settings, themeMode: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf<Screen>(Screen.Playlists) }
    val playlists by repo.observePlaylists().collectAsState(initial = emptyList())
    val parkingHistory by repo.observeParkingHistory().collectAsState(initial = emptyList())
    val parkedLocation by settings.parkedLocationFlow.collectAsState()
    val autoUpdateEnabled by settings.autoUpdateEnabledFlow.collectAsState()
    val autoUpdateInterval by settings.autoUpdateIntervalFlow.collectAsState()
    val nowPlaying by RifterMediaService.nowPlayingFlow.collectAsState()
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var isSavingLocation by remember { mutableStateOf(false) }

    // Canlı Radyolar (Veritabanı tabanlı dinamik liste, sıralama ve ekleme durumları)
    val turkishRadios by repo.observeRadioStations(RadioCategory.TURKISH).collectAsState(initial = emptyList())
    val kurdishRadios by repo.observeRadioStations(RadioCategory.KURDISH).collectAsState(initial = emptyList())
    var selectedRadioCategory by remember { mutableStateOf(RadioCategory.TURKISH) }
    var isRadioEditMode by remember { mutableStateOf(false) }
    var showAddRadioDialog by remember { mutableStateOf(false) }
    var radioToEdit by remember { mutableStateOf<RadioStation?>(null) }

    // Tüm silme ve onay işlemleri için genel "Emin misiniz?" penceresi durumu
    var confirmDialog by remember { mutableStateOf<ConfirmationDialogRequest?>(null) }

    // Canlı Yol Bilgisayarı & Hava Durumu (Ana ekranda doğrudan görünür)
    var cockpitSnap by remember { mutableStateOf<CockpitSnapshot?>(null) }
    var isLoadingCockpit by remember { mutableStateOf(false) }

    // Donanım geri tuşu desteği
    BackHandler(enabled = screen !is Screen.Playlists) {
        screen = when (val s = screen) {
            is Screen.Picker -> Screen.Songs(s.playlist)
            else -> Screen.Playlists
        }
    }

    // Müzik izni
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasAudioPermission by remember {
        mutableStateOf(context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED)
    }

    // Telefon şarkıları (sadece picker açıkken)
    var deviceSongs by remember { mutableStateOf<List<DeviceSong>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun scanSongsNow() {
        scope.launch {
            isLoading = true
            deviceSongs = withContext(Dispatchers.IO) { repo.scanDeviceSongs(context) }
            isLoading = false
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted) {
            scanSongsNow()
        }
    }

    fun loadDeviceSongs() {
        val currentlyGranted = context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = currentlyGranted
        if (!currentlyGranted) {
            audioPermLauncher.launch(audioPermission)
            return
        }
        scanSongsNow()
    }

    // Konum izni
    var hasLocationPermission by remember {
        mutableStateOf(ParkingLocationHelper.hasLocationPermission(context))
    }

    fun refreshCockpitWeather(showToast: Boolean = false) {
        if (!ParkingLocationHelper.hasLocationPermission(context)) return
        scope.launch {
            isLoadingCockpit = true
            val fresh = CockpitWeatherHelper.fetchCockpitSnapshot(context)
            cockpitSnap = fresh
            isLoadingCockpit = false
            RifterMediaService.notifyDataChanged()
            if (showToast && fresh != null) {
                val tempStr = fresh.temperatureC?.let { " (${it}°C)" } ?: ""
                Toast.makeText(context, "🌤️ Hava durumu güncellendi$tempStr", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            refreshCockpitWeather()
        }
    }

    // İlk açılışta varsayılan radyoları hazırla, konum ve bildirim izinlerini iste
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            repo.ensureDefaultRadiosSeeded()
        }
        if (hasLocationPermission) {
            refreshCockpitWeather()
        }
        val permsToRequest = buildList {
            if (!hasLocationPermission) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (permsToRequest.isNotEmpty()) {
            locationPermLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    fun saveParkingNow() {
        if (!hasLocationPermission) {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        scope.launch {
            isSavingLocation = true
            val result = ParkingLocationHelper.fetchCurrentLocation(context)
            isSavingLocation = false
            if (result != null) {
                val (loc, addr) = result
                val saved = settings.saveParkedLocation(loc.latitude, loc.longitude, addr)
                withContext(Dispatchers.IO) {
                    repo.addParkingRecord(loc.latitude, loc.longitude, addr, saved.timestampMillis)
                }
                ParkingNotifier.showParkedNotification(context, saved)
                RifterMediaService.notifyDataChanged()
                Toast.makeText(context, "📍 Park konumu kaydedildi!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Konum alınamadı, GPS açık mı?", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentScreen = screen
    val title = when (currentScreen) {
        is Screen.Playlists -> "🚗 Rifter Kokpit"
        is Screen.Songs -> currentScreen.playlist.name
        is Screen.Picker -> "Şarkı Ekle → ${currentScreen.playlist.name}"
        is Screen.ParkingHistory -> "🕓 Park Geçmişim (${parkingHistory.size})"
        is Screen.Radios -> "📻 Canlı Radyolar"
    }

    val themeBadge = when (themeMode) {
        Settings.THEME_DARK -> "🌙 Gece"
        Settings.THEME_LIGHT -> "☀️ Gündüz"
        else -> "🔄 Oto"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    if (currentScreen !is Screen.Playlists) {
                        IconButton(onClick = {
                            screen = when (currentScreen) {
                                is Screen.Picker -> Screen.Songs(currentScreen.playlist)
                                else -> Screen.Playlists
                            }
                        }) { Icon(Icons.Default.ArrowBack, "Geri") }
                    }
                },
                actions = {
                    when (currentScreen) {
                        is Screen.Playlists -> {
                            TextButton(
                                onClick = {
                                    val next = settings.cycleThemeMode()
                                    RifterMediaService.notifyDataChanged()
                                    val msg = when (next) {
                                        Settings.THEME_DARK -> "🌙 Gece Sürüş Modu (Göz Almaz)"
                                        Settings.THEME_LIGHT -> "☀️ Gündüz Açık Mod"
                                        else -> "🔄 Sistem Otomatik Tema"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = themeBadge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Settings, "Ayarlar")
                            }
                            IconButton(onClick = { showNewPlaylistDialog = true }) {
                                Icon(Icons.Default.Add, "Yeni liste")
                            }
                        }
                        is Screen.Songs -> {
                            IconButton(onClick = {
                                screen = Screen.Picker(currentScreen.playlist)
                                loadDeviceSongs()
                            }) {
                                Icon(Icons.Default.Add, "Şarkı ekle")
                            }
                        }
                        is Screen.ParkingHistory -> {
                            if (parkingHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        confirmDialog = ConfirmationDialogRequest(
                                            title = "Tüm Park Geçmişini Sil",
                                            message = "Kayıtlı ${parkingHistory.size} adet park geçmişinin tamamını silmek istediğinize emin misiniz?",
                                            confirmButtonText = "Evet, Sil",
                                            isDestructive = true,
                                            iconEmoji = "🗑️",
                                            onConfirm = {
                                                scope.launch(Dispatchers.IO) {
                                                    repo.clearAllParkingRecords()
                                                    RifterMediaService.notifyDataChanged()
                                                }
                                                Toast.makeText(context, "Tüm park geçmişi silindi", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                ) {
                                    Text(
                                        "🗑️ Tümünü Sil",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        is Screen.Radios -> {
                            TextButton(
                                onClick = { isRadioEditMode = !isRadioEditMode }
                            ) {
                                Text(
                                    text = if (isRadioEditMode) "✅ Bitti" else "✏️ Düzenle",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { showAddRadioDialog = true }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = if (selectedRadioCategory == RadioCategory.KURDISH) "Kürtçe Radyo Ekle" else "Türkçe Radyo Ekle"
                                )
                            }
                        }
                        else -> {}
                    }
                }
            )
        },
        bottomBar = {
            // Alt çalar SADECE Ana Menüde (Screen.Playlists) görünür; Radyo veya Park Geçmişine girince peşinden gelmez!
            if (currentScreen is Screen.Playlists) {
                RifterPlayerDeck(
                    nowPlaying = nowPlaying,
                    settings = settings,
                    onPlayPause = {
                        if (!nowPlaying.hasMedia && settings.lastPlaylistId >= 0L) {
                            RifterMediaService.playFromPhone(context, MediaIds.ACTION_RESUME)
                        } else {
                            RifterMediaService.togglePlayPauseFromPhone(context)
                        }
                    },
                    onNext = { RifterMediaService.skipNextFromPhone() },
                    onPrev = { RifterMediaService.skipPreviousFromPhone() },
                    onSeekTo = { posMs -> RifterMediaService.seekToFromPhone(posMs) },
                    onToggleShuffle = { RifterMediaService.toggleShuffleFromPhone() },
                    onToggleRepeat = { RifterMediaService.toggleRepeatFromPhone() }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val s = currentScreen) {
                is Screen.Playlists -> PlaylistListScreen(
                    playlists = playlists,
                    parkedLocation = parkedLocation,
                    parkingHistoryCount = parkingHistory.size,
                    isSavingLocation = isSavingLocation,
                    cockpitSnap = cockpitSnap,
                    isLoadingCockpit = isLoadingCockpit,
                    onRefreshCockpit = { refreshCockpitWeather(showToast = true) },
                    onSaveParkingClick = { saveParkingNow() },
                    onUpdateActiveParkingDetails = { newTitle, newNote ->
                        val updated = settings.updateParkedLocationDetails(newTitle, newNote)
                        if (updated != null) {
                            scope.launch(Dispatchers.IO) {
                                repo.updateParkingRecordByTimestamp(updated.timestampMillis, newTitle, newNote)
                                RifterMediaService.notifyDataChanged()
                            }
                            Toast.makeText(context, "✅ Park başlığı ve notu güncellendi", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateToCarClick = { loc ->
                        ParkingLocationHelper.openWalkingDirections(context, loc.latitude, loc.longitude)
                    },
                    onOpenHistoryClick = { screen = Screen.ParkingHistory },
                    onOpenRadiosClick = { screen = Screen.Radios },
                    onClearParkingClick = {
                        confirmDialog = ConfirmationDialogRequest(
                            title = "Aktif Park Konumunu Sil",
                            message = "Şu anki aktif park konumu bilgisini kaldırmak istediğinize emin misiniz?",
                            confirmButtonText = "Evet, Sil",
                            isDestructive = true,
                            iconEmoji = "🗑️",
                            onConfirm = {
                                settings.clearParkedLocation()
                                ParkingNotifier.cancelNotification(context)
                                RifterMediaService.notifyDataChanged()
                            }
                        )
                    },
                    onPlaylistClick = { screen = Screen.Songs(it) },
                    onRenamePlaylist = { pl -> playlistToRename = pl },
                    onPlayPlaylistDirect = { pl, shuffle ->
                        RifterMediaService.playPlaylistFromPhone(context, pl.id, shuffle)
                    },
                    onDeletePlaylist = { pl ->
                        confirmDialog = ConfirmationDialogRequest(
                            title = "Listeyi Sil",
                            message = "\"${pl.name}\" adlı müzik listesini ve içindeki şarkıları silmek istediğinize emin misiniz?",
                            confirmButtonText = "Evet, Sil",
                            isDestructive = true,
                            iconEmoji = "🗑️",
                            onConfirm = {
                                scope.launch(Dispatchers.IO) {
                                    repo.deletePlaylist(pl)
                                    RifterMediaService.notifyDataChanged(pl.id)
                                }
                            }
                        )
                    }
                )
                is Screen.Songs -> {
                    val songs by repo.observeSongs(s.playlist.id).collectAsState(initial = emptyList())
                    SongListScreen(
                        songs = songs,
                        currentPlayingTitle = nowPlaying.title,
                        isPlaying = nowPlaying.isPlaying,
                        onPlaySong = { song ->
                            RifterMediaService.playFromPhone(context, MediaIds.songId(s.playlist.id, song.id))
                        },
                        onShufflePlayAll = {
                            RifterMediaService.playFromPhone(context, MediaIds.shufflePlaylistId(s.playlist.id))
                        },
                        onRemoveSong = { song ->
                            confirmDialog = ConfirmationDialogRequest(
                                title = "Şarkıyı Listeden Kaldır",
                                message = "\"${song.title}\" şarkısını bu listeden silmek istediğinize emin misiniz?",
                                confirmButtonText = "Evet, Kaldır",
                                isDestructive = true,
                                iconEmoji = "🗑️",
                                onConfirm = {
                                    scope.launch(Dispatchers.IO) {
                                        repo.removeSong(song.id)
                                        RifterMediaService.notifyDataChanged(s.playlist.id)
                                    }
                                }
                            )
                        }
                    )
                }
                is Screen.Picker -> {
                    LaunchedEffect(s.playlist.id, hasAudioPermission) {
                        if (hasAudioPermission && deviceSongs.isEmpty() && !isLoading) {
                            scanSongsNow()
                        }
                    }
                    val songs by repo.observeSongs(s.playlist.id).collectAsState(initial = emptyList())
                    SongPickerScreen(
                        deviceSongs = deviceSongs,
                        playlistSongs = songs,
                        isLoading = isLoading,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onAdd = { song ->
                            scope.launch(Dispatchers.IO) {
                                repo.addSong(s.playlist.id, song)
                                RifterMediaService.notifyDataChanged(s.playlist.id)
                            }
                        }
                    )
                }
                is Screen.ParkingHistory -> {
                    ParkingHistoryScreen(
                        records = parkingHistory,
                        onNavigateClick = { rec ->
                            ParkingLocationHelper.openWalkingDirections(context, rec.latitude, rec.longitude)
                        },
                        onUpdateRecordDetails = { rec, newTitle, newNote ->
                            scope.launch(Dispatchers.IO) {
                                repo.updateParkingRecordDetails(rec.id, newTitle, newNote)
                                if (parkedLocation?.timestampMillis == rec.timestampMillis) {
                                    settings.updateParkedLocationDetails(newTitle, newNote)
                                }
                                RifterMediaService.notifyDataChanged()
                            }
                            Toast.makeText(context, "✅ Park kaydı güncellendi", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteRecord = { rec ->
                            val dateStr = ParkingLocationHelper.formatTimestampWithDay(rec.timestampMillis)
                            confirmDialog = ConfirmationDialogRequest(
                                title = "Park Kaydını Sil",
                                message = "$dateStr tarihli \"${rec.address}\" park kaydını silmek istediğinize emin misiniz?",
                                confirmButtonText = "Evet, Sil",
                                isDestructive = true,
                                iconEmoji = "🗑️",
                                onConfirm = {
                                    scope.launch(Dispatchers.IO) {
                                        repo.deleteParkingRecord(rec.id)
                                        RifterMediaService.notifyDataChanged()
                                    }
                                }
                            )
                        },
                        onDeleteAllClick = {
                            confirmDialog = ConfirmationDialogRequest(
                                title = "Tüm Park Geçmişini Sil",
                                message = "Kayıtlı ${parkingHistory.size} adet park geçmişinin tamamını silmek istediğinize emin misiniz?",
                                confirmButtonText = "Evet, Sil",
                                isDestructive = true,
                                iconEmoji = "🗑️",
                                onConfirm = {
                                    scope.launch(Dispatchers.IO) {
                                        repo.clearAllParkingRecords()
                                        RifterMediaService.notifyDataChanged()
                                    }
                                }
                            )
                        }
                    )
                }
                is Screen.Radios -> {
                    RadioListScreen(
                        turkishRadios = turkishRadios,
                        kurdishRadios = kurdishRadios,
                        selectedCategory = selectedRadioCategory,
                        onSelectCategory = { selectedRadioCategory = it },
                        isEditMode = isRadioEditMode,
                        nowPlaying = nowPlaying,
                        onPlayRadio = { station ->
                            RifterMediaService.playFromPhone(context, MediaIds.radioId(station.id))
                        },
                        onSaveRadioOrder = { newOrder ->
                            scope.launch(Dispatchers.IO) {
                                repo.saveRadioOrder(newOrder)
                                RifterMediaService.notifyDataChanged()
                            }
                        },
                        onMoveRadioToTop = { station ->
                            scope.launch(Dispatchers.IO) {
                                repo.moveRadioStation(selectedRadioCategory, station.id, 0)
                                RifterMediaService.notifyDataChanged()
                            }
                            Toast.makeText(context, "⏫ ${station.name} 1. sıraya alındı", Toast.LENGTH_SHORT).show()
                        },
                        onEditRadioClick = { station ->
                            radioToEdit = station
                        },
                        onDeleteRadioClick = { station ->
                            confirmDialog = ConfirmationDialogRequest(
                                title = "Radyoyu Sil",
                                message = "\"${station.name}\" radyosunu listeden silmek istediğinize emin misiniz?",
                                confirmButtonText = "Evet, Sil",
                                isDestructive = true,
                                iconEmoji = "🗑️",
                                onConfirm = {
                                    scope.launch(Dispatchers.IO) {
                                        repo.deleteRadioStation(selectedRadioCategory, station.id)
                                        RifterMediaService.notifyDataChanged()
                                    }
                                    Toast.makeText(context, "🗑️ Radyo silindi", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onRestoreDefaultsClick = {
                            val catName = if (selectedRadioCategory == RadioCategory.KURDISH) "☀️ Kürtçe FM" else "Türkçe FM"
                            confirmDialog = ConfirmationDialogRequest(
                                title = "Varsayılan $catName Radyolarını Geri Yükle",
                                message = "$catName kategorisindeki radyo sıralamasını ilk fabrika ayarlarına döndürmek ve sisteme gömülü orijinal radyoları geri yüklemek istediğinize emin misiniz?",
                                confirmButtonText = "Evet, Geri Yükle",
                                dismissButtonText = "Vazgeç",
                                isDestructive = false,
                                iconEmoji = "🔄",
                                onConfirm = {
                                    scope.launch(Dispatchers.IO) {
                                        repo.restoreDefaultRadios(selectedRadioCategory)
                                        RifterMediaService.notifyDataChanged()
                                    }
                                    Toast.makeText(context, "🔄 $catName varsayılan listesi geri yüklendi", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    // Ortak İşlem ve Silme Onayı ("Emin misiniz?") Penceresi
    val pendingConfirm = confirmDialog
    if (pendingConfirm != null) {
        AlertDialog(
            onDismissRequest = { confirmDialog = null },
            title = { Text("${pendingConfirm.iconEmoji} ${pendingConfirm.title}", fontWeight = FontWeight.Bold) },
            text = { Text(pendingConfirm.message) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirm.onConfirm()
                        confirmDialog = null
                    },
                    colors = if (pendingConfirm.isDestructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    }
                ) {
                    Text(pendingConfirm.confirmButtonText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDialog = null }) {
                    Text(pendingConfirm.dismissButtonText)
                }
            }
        )
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onConfirm = { name ->
                scope.launch(Dispatchers.IO) {
                    repo.createPlaylist(name)
                    RifterMediaService.notifyDataChanged()
                }
                showNewPlaylistDialog = false
            },
            onDismiss = { showNewPlaylistDialog = false }
        )
    }

    val renameTarget = playlistToRename
    if (renameTarget != null) {
        RenamePlaylistDialog(
            currentName = renameTarget.name,
            onConfirm = { newName ->
                scope.launch(Dispatchers.IO) {
                    repo.renamePlaylist(renameTarget.id, newName)
                    RifterMediaService.notifyDataChanged(renameTarget.id)
                }
                playlistToRename = null
            },
            onDismiss = { playlistToRename = null }
        )
    }

    if (showAddRadioDialog) {
        val currentCategoryStations = if (selectedRadioCategory == RadioCategory.KURDISH) kurdishRadios else turkishRadios
        AddRadioStationDialog(
            category = selectedRadioCategory,
            existingStations = currentCategoryStations,
            nowPlaying = nowPlaying,
            onPreviewStream = { title, subtitle, streamUrl ->
                RifterMediaService.previewStreamUrlFromPhone(context, title, subtitle, streamUrl)
            },
            onAddStation = { name, genre, streamUrl, placeAtTop ->
                scope.launch(Dispatchers.IO) {
                    val added = repo.addCustomRadio(
                        rawName = name,
                        rawGenre = genre,
                        rawStreamUrl = streamUrl,
                        category = selectedRadioCategory,
                        placeAtTop = placeAtTop
                    )
                    RifterMediaService.notifyDataChanged()
                    withContext(Dispatchers.Main) {
                        val catLabel = if (selectedRadioCategory == RadioCategory.KURDISH) "☀️ Kürtçe FM" else "Türkçe FM"
                        Toast.makeText(context, "✅ ${added.name} ($catLabel) listenize eklendi!", Toast.LENGTH_SHORT).show()
                    }
                }
                showAddRadioDialog = false
            },
            onDismiss = { showAddRadioDialog = false }
        )
    }

    val editingRadio = radioToEdit
    if (editingRadio != null) {
        EditRadioStationDialog(
            station = editingRadio,
            nowPlaying = nowPlaying,
            onPreviewStream = { title, subtitle, streamUrl ->
                RifterMediaService.previewStreamUrlFromPhone(context, title, subtitle, streamUrl)
            },
            onSave = { newName, newGenre, newUrl ->
                scope.launch(Dispatchers.IO) {
                    repo.updateRadioStationDetails(editingRadio.id, newName, newGenre, newUrl)
                    RifterMediaService.notifyDataChanged()
                }
                Toast.makeText(context, "✅ Radyo bilgileri güncellendi", Toast.LENGTH_SHORT).show()
                radioToEdit = null
            },
            onDismiss = { radioToEdit = null }
        )
    }

    if (showSettingsDialog) {
        RifterSettingsDialog(
            autoUpdateEnabled = autoUpdateEnabled,
            autoUpdateInterval = autoUpdateInterval,
            themeMode = themeMode,
            onToggleAutoUpdate = { enabled ->
                settings.autoUpdateEnabled = enabled
                RifterMediaService.rescheduleAutoUpdater()
                RifterMediaService.notifyDataChanged()
            },
            onSelectInterval = { mins ->
                settings.autoUpdateIntervalMinutes = mins
                RifterMediaService.rescheduleAutoUpdater()
                RifterMediaService.notifyDataChanged()
            },
            onSelectTheme = { mode ->
                settings.themeMode = mode
                RifterMediaService.notifyDataChanged()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

/**
 * Kompakt, ekranda az yer kaplayan ve tüm butonları (Tekrar dahil) tam sığan alt medya çalar.
 * Sadece ana menüde görünür.
 */
@Composable
fun RifterPlayerDeck(
    nowPlaying: NowPlayingUiState,
    settings: Settings,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    val activeTitle = nowPlaying.title.ifBlank { settings.lastSongTitle }
    val activeArtist = nowPlaying.artist.ifBlank { settings.lastSongArtist }

    if (activeTitle.isBlank()) return

    var sliderDraggingPos by remember { mutableStateOf<Float?>(null) }
    val durationMs = nowPlaying.durationMs
    val currentPosMs = sliderDraggingPos?.toLong() ?: nowPlaying.positionMs.coerceAtLeast(0L)

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // 1. Satır: Şarkı Adı - Sanatçı ve Sağda Süre (0:00 / 4:58)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeArtist.isNotBlank()) "$activeTitle · $activeArtist" else activeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (!nowPlaying.isRadio && durationMs > 0L) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 2. Satır: İnce Slider (Sürükleme Çubuğu)
            if (!nowPlaying.isRadio && durationMs > 0L) {
                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { sliderDraggingPos = it },
                    onValueChangeFinished = {
                        sliderDraggingPos?.let { onSeekTo(it.toLong()) }
                        sliderDraggingPos = null
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }

            // 3. Satır: Eşit Dağılımlı Kumanda Butonları (Radyo çalarken de ⏮ ve ⏭ aktif!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!nowPlaying.isRadio) {
                    OutlinedButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = if (nowPlaying.shuffleEnabled) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(
                            text = if (nowPlaying.shuffleEnabled) "🔀 Açık" else "🔀 Kapalı",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }

                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier.weight(0.75f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⏮", style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = onPlayPause,
                    modifier = Modifier.weight(1.3f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (nowPlaying.isPlaying) "⏸ Durdur" else "▶️ Çal",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onNext,
                    modifier = Modifier.weight(0.75f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("⏭", style = MaterialTheme.typography.bodyMedium)
                }

                if (!nowPlaying.isRadio) {
                    OutlinedButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = if (nowPlaying.repeatOneEnabled) ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(
                            text = if (nowPlaying.repeatOneEnabled) "🔂 Tek" else "🔁 Liste",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ana ekranda doğrudan görünen kompakt Yol Bilgisayarı (Hız, Rakım) ve Hava Durumu kartı.
 */
@Composable
fun CockpitLiveCard(
    snap: CockpitSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (snap != null) {
                    val weatherPart = if (snap.temperatureC != null && snap.weatherDesc != null) {
                        "${snap.weatherDesc} ${snap.temperatureC}°C · 💨 ${snap.windSpeedKmh ?: 0.0} km/s"
                    } else {
                        "Hava durumu bekleniyor"
                    }
                    Text(
                        text = "🚀 Hız: ${snap.speedKmh} km/s  ·  🏔️ Rakım: ${snap.altitudeMeters} m",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$weatherPart",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (isLoading) "🧭 Yol Bilgisayarı & Hava Durumu alınıyor..." else "🧭 Hız, Rakım ve Hava Durumu için yenile'ye dokun",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Yenile",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Küçültülmüş, derli toplu Park Konumu Kartı.
 * Üzerine dokununca veya basılı tutunca (ya da ✏️ Not/Başlık butonuna basınca)
 * tüm detayları (Tarih, Saat, Tam Adres, GPS Koordinatı, Başlık, Not) gösteren ve güncelleyen pencere açılır.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ParkingCard(
    parkedLocation: ParkedLocation?,
    isSavingLocation: Boolean,
    onSaveParkingClick: () -> Unit,
    onUpdateDetails: (String, String) -> Unit,
    onNavigateToCarClick: (ParkedLocation) -> Unit,
    onClearParkingClick: () -> Unit
) {
    var showReadOnlyDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = { if (parkedLocation != null) showReadOnlyDialog = true },
                onLongClick = { if (parkedLocation != null) showReadOnlyDialog = true }
            ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                val headerText = if (parkedLocation != null) {
                    if (parkedLocation.title.isNotBlank()) {
                        "🏷️ ${parkedLocation.title} · ${ParkingLocationHelper.formatTimestamp(parkedLocation.timestampMillis)}"
                    } else {
                        "🚗 Son Park · ${ParkingLocationHelper.formatTimestampWithDay(parkedLocation.timestampMillis)}"
                    }
                } else {
                    "🚗 Rifter Park Konumu"
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (parkedLocation != null) {
                    TextButton(
                        onClick = { showEditDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text(
                            text = if (parkedLocation.title.isBlank() && parkedLocation.note.isBlank()) "✏️ Başlık/Not" else "✏️ Düzenle",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = onClearParkingClick,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Aktif parkı temizle",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (parkedLocation != null) {
                if (parkedLocation.note.isNotBlank()) {
                    Text(
                        text = "📝 ${parkedLocation.note}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "📍 ${parkedLocation.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onNavigateToCarClick(parkedLocation) },
                        modifier = Modifier.weight(1f).height(34.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        Text("🚶 Arabama Götür", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onSaveParkingClick,
                        enabled = !isSavingLocation,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Text(if (isSavingLocation) "Alınıyor..." else "📍 Yeni Park", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else {
                Button(
                    onClick = onSaveParkingClick,
                    enabled = !isSavingLocation,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    Text(
                        if (isSavingLocation) "GPS Konumu Alınıyor..." else "📍 Aracı Park Ettim (Konumu Kaydet)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    if (showReadOnlyDialog && parkedLocation != null) {
        ParkingReadOnlyDetailDialog(
            timestampMillis = parkedLocation.timestampMillis,
            address = parkedLocation.address,
            latitude = parkedLocation.latitude,
            longitude = parkedLocation.longitude,
            title = parkedLocation.title,
            note = parkedLocation.note,
            onNavigateClick = {
                showReadOnlyDialog = false
                onNavigateToCarClick(parkedLocation)
            },
            onDismiss = { showReadOnlyDialog = false }
        )
    }

    if (showEditDialog && parkedLocation != null) {
        ParkingDetailAndEditDialog(
            timestampMillis = parkedLocation.timestampMillis,
            address = parkedLocation.address,
            latitude = parkedLocation.latitude,
            longitude = parkedLocation.longitude,
            initialTitle = parkedLocation.title,
            initialNote = parkedLocation.note,
            onSaveDetails = { newTitle, newNote ->
                onUpdateDetails(newTitle, newNote)
                showEditDialog = false
            },
            onNavigateClick = {
                showEditDialog = false
                onNavigateToCarClick(parkedLocation)
            },
            onNewParkClick = {
                showEditDialog = false
                onSaveParkingClick()
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

/**
 * Uzun basınca veya karta dokununca açılan SADECE OKUMA (Read-Only) Detay Penceresi.
 * İçinde yazı yazma kutusu yoktur; sadece başlık, not, tarih/saat, tam adres ve koordinatı net gösterir.
 */
@Composable
fun ParkingReadOnlyDetailDialog(
    timestampMillis: Long,
    address: String,
    latitude: Double,
    longitude: Double,
    title: String,
    note: String,
    onNavigateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val gpsStr = remember(latitude, longitude) { "%.6f, %.6f".format(java.util.Locale.US, latitude, longitude) }
    val dateStr = remember(timestampMillis) { ParkingLocationHelper.formatTimestampWithDay(timestampMillis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (title.isNotBlank()) "🏷️ $title" else "📍 Park Yeri Detayları",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            SelectionContainer {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (title.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("🏷️ Başlık", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (note.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("📝 Not / Açıklama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(note, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("🗓️ Tarih ve Saat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(dateStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🏠 Tam Adres", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(address))
                                        Toast.makeText(context, "📋 Adres kopyalandı", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("📋 Kopyala", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(address, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🌐 GPS Koordinatları", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(gpsStr))
                                        Toast.makeText(context, "📍 GPS koordinatları kopyalandı", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("📋 Kopyala", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(gpsStr, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onNavigateClick) {
                Text("🚶 Haritada Yol Tarifi Al")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

@Composable
fun ParkingDetailAndEditDialog(
    timestampMillis: Long,
    address: String,
    latitude: Double,
    longitude: Double,
    initialTitle: String,
    initialNote: String,
    onSaveDetails: (String, String) -> Unit,
    onNavigateClick: () -> Unit,
    onNewParkClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var titleText by remember(initialTitle) { mutableStateOf(initialTitle) }
    var noteText by remember(initialNote) { mutableStateOf(initialNote) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("📍 Park Detayı & Not Ekle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Başlık Alanı
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("🏷️ Park Başlığı (örn. AVM, Ev Önü, Hastane)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Hızlı Başlık Etiketleri
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("AVM", "Ev Önü", "İş Yeri", "Çarşı").forEach { tag ->
                        SuggestionChip(
                            onClick = { titleText = tag },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }

                // 2. Not / Açıklama Alanı
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("📝 Not / Kat / Tarif (örn. -2. Kat B4 Sütunu)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                // Hızlı Not Etiketleri
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("-1. Kat", "-2. Kat", "Açık Otopark").forEach { tag ->
                        SuggestionChip(
                            onClick = {
                                noteText = if (noteText.isBlank()) tag else "$noteText · $tag"
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // 3. Tarih, Saat, Tam Adres ve GPS Koordinatları (Seçilebilir ve Kopyalanabilir)
                val gpsStr = "%.6f, %.6f".format(java.util.Locale.US, latitude, longitude)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "🗓️ Tarih & Saat: ${ParkingLocationHelper.formatTimestampWithDay(timestampMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📍 Tam Adres: $address",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(address))
                                    Toast.makeText(context, "📋 Adres kopyalandı", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("📋 Kopyala", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🌐 GPS: $gpsStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(gpsStr))
                                    Toast.makeText(context, "📍 GPS kopyalandı", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("📋 Kopyala", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // 4. Alt Aksiyon Butonları (Arabama Götür & Yeni Park)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onNavigateClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🚶 Arabama Götür")
                    }
                    if (onNewParkClick != null) {
                        OutlinedButton(onClick = onNewParkClick) {
                            Text("📍 Yeni Park")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSaveDetails(titleText.trim(), noteText.trim()) }) {
                Text("💾 Başlık & Notu Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

@Composable
fun PlaylistListScreen(
    playlists: List<Playlist>,
    parkedLocation: ParkedLocation?,
    parkingHistoryCount: Int,
    isSavingLocation: Boolean,
    cockpitSnap: CockpitSnapshot?,
    isLoadingCockpit: Boolean,
    onRefreshCockpit: () -> Unit,
    onSaveParkingClick: () -> Unit,
    onUpdateActiveParkingDetails: (String, String) -> Unit,
    onNavigateToCarClick: (ParkedLocation) -> Unit,
    onOpenHistoryClick: () -> Unit,
    onOpenRadiosClick: () -> Unit,
    onClearParkingClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRenamePlaylist: (Playlist) -> Unit,
    onPlayPlaylistDirect: (Playlist, Boolean) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // 1. Kompakt Anlık Park Konumu Kartı
        ParkingCard(
            parkedLocation = parkedLocation,
            isSavingLocation = isSavingLocation,
            onSaveParkingClick = onSaveParkingClick,
            onUpdateDetails = onUpdateActiveParkingDetails,
            onNavigateToCarClick = onNavigateToCarClick,
            onClearParkingClick = onClearParkingClick
        )

        // 2. Canlı Yol Bilgisayarı (Hız, Rakım) & Hava Durumu Şeridi
        CockpitLiveCard(
            snap = cockpitSnap,
            isLoading = isLoadingCockpit,
            onRefresh = onRefreshCockpit
        )

        // 3. Radyolar (TR / Kurdî) ve Park Geçmişim Butonları
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onOpenRadiosClick,
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("📻 Radyolar (TR / Kurdî)", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            FilledTonalButton(
                onClick = onOpenHistoryClick,
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("🕓 Park Geçmişim ($parkingHistoryCount)", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }

        // 4. Müzik Listeleri
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.List, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Text("Henüz müzik listesi yok", style = MaterialTheme.typography.titleSmall)
                    Text("Sağ üstteki + ile yeni liste oluştur", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    PlaylistCard(
                        playlist = pl,
                        onClick = { onPlaylistClick(pl) },
                        onRename = { onRenamePlaylist(pl) },
                        onPlayDirect = { onPlayPlaylistDirect(pl, false) },
                        onShuffleDirect = { onPlayPlaylistDirect(pl, true) },
                        onDelete = { onDeletePlaylist(pl) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onPlayDirect: () -> Unit,
    onShuffleDirect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Şarkıları görmek veya eklemek için dokun",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onRename,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("✏️ Ad Değiştir", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, "Listeyi sil", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPlayDirect,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    Text("▶️ Çaldır", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onShuffleDirect,
                    modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    Text("🔀 Karışık Çaldır", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ParkingHistoryScreen(
    records: List<ParkingRecord>,
    onNavigateClick: (ParkingRecord) -> Unit,
    onUpdateRecordDetails: (ParkingRecord, String, String) -> Unit,
    onDeleteRecord: (ParkingRecord) -> Unit,
    onDeleteAllClick: () -> Unit
) {
    var viewingRecord by remember { mutableStateOf<ParkingRecord?>(null) }
    var editingRecord by remember { mutableStateOf<ParkingRecord?>(null) }

    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("Henüz park geçmişi yok", style = MaterialTheme.typography.titleMedium)
                Text("Kaydettiğin tüm park yerleri gün ve saatiyle burada saklanır.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedButton(
                    onClick = onDeleteAllClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🗑️ Tüm Park Geçmişini Sil (${records.size} Kayıt)", fontWeight = FontWeight.Bold)
                }
            }
            items(records, key = { it.id }) { rec ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { viewingRecord = rec },
                            onLongClick = { viewingRecord = rec }
                        ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val header = if (rec.title.isNotBlank()) {
                                "🏷️ ${rec.title} · ${ParkingLocationHelper.formatTimestampWithDay(rec.timestampMillis)}"
                            } else {
                                "🗓️ ${ParkingLocationHelper.formatTimestampWithDay(rec.timestampMillis)}"
                            }
                            Text(
                                text = header,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { editingRecord = rec },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (rec.title.isBlank() && rec.note.isBlank()) "✏️ Başlık/Not" else "✏️ Düzenle",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = { onDeleteRecord(rec) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Sil", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (rec.note.isNotBlank()) {
                            Text(
                                text = "📝 ${rec.note}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = "📍 ${rec.address}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "🌐 GPS: %.6f, %.6f".format(java.util.Locale.US, rec.latitude, rec.longitude),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onNavigateClick(rec) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🚶 Haritada Yol Tarifi Al")
                            }
                            OutlinedButton(onClick = { editingRecord = rec }) {
                                Text("✏️ Not")
                            }
                        }
                    }
                }
            }
        }
    }

    val viewRec = viewingRecord
    if (viewRec != null) {
        ParkingReadOnlyDetailDialog(
            timestampMillis = viewRec.timestampMillis,
            address = viewRec.address,
            latitude = viewRec.latitude,
            longitude = viewRec.longitude,
            title = viewRec.title,
            note = viewRec.note,
            onNavigateClick = {
                viewingRecord = null
                onNavigateClick(viewRec)
            },
            onDismiss = { viewingRecord = null }
        )
    }

    val targetRec = editingRecord
    if (targetRec != null) {
        ParkingDetailAndEditDialog(
            timestampMillis = targetRec.timestampMillis,
            address = targetRec.address,
            latitude = targetRec.latitude,
            longitude = targetRec.longitude,
            initialTitle = targetRec.title,
            initialNote = targetRec.note,
            onSaveDetails = { newTitle, newNote ->
                onUpdateRecordDetails(targetRec, newTitle, newNote)
                editingRecord = null
            },
            onNavigateClick = {
                editingRecord = null
                onNavigateClick(targetRec)
            },
            onNewParkClick = null,
            onDismiss = { editingRecord = null }
        )
    }
}

/**
 * Türkçe ve Kürtçe FM Radyo Kategorileri:
 * - Normal modda (isEditMode == false) liste tamamen kilitlidir: sadece dokunup dinlenir, yanlışlıkla sürüklenmez veya değişmez.
 * - Üst bardaki "✏️ Düzenle" butonuna basıldığında (isEditMode == true) sürükle-bırak (☰ veya basılı tutarak yukarı/aşağı taşıma),
 *   1. sıraya alma, düzenleme, silme ve varsayılanları geri yükleme aktifleşir.
 */
@Composable
fun RadioListScreen(
    turkishRadios: List<RadioStation>,
    kurdishRadios: List<RadioStation>,
    selectedCategory: RadioCategory,
    onSelectCategory: (RadioCategory) -> Unit,
    isEditMode: Boolean,
    nowPlaying: NowPlayingUiState,
    onPlayRadio: (RadioStation) -> Unit,
    onSaveRadioOrder: (List<RadioStation>) -> Unit,
    onMoveRadioToTop: (RadioStation) -> Unit,
    onEditRadioClick: (RadioStation) -> Unit,
    onDeleteRadioClick: (RadioStation) -> Unit,
    onRestoreDefaultsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val stations = if (selectedCategory == RadioCategory.TURKISH) turkishRadios else kurdishRadios
    val categoryLabel = if (selectedCategory == RadioCategory.KURDISH) "☀️ Kürtçe FM" else "Türkçe FM"

    // Sürükle-bırak (Drag & Drop) için canlı yerel liste ve durumlar (SADECE isEditMode == true iken çalışır)
    val localStations = remember { mutableStateListOf<RadioStation>() }
    var draggedStationId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    // Düzenleme modundan çıkıldığında aktif sürüklemeyi temizle
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            draggedStationId = null
            dragOffsetY = 0f
        }
    }

    // Sürükleme yapılmıyorken veritabanından gelen güncel listeyi senkronize et
    LaunchedEffect(stations, selectedCategory, draggedStationId == null) {
        if (draggedStationId == null) {
            localStations.clear()
            localStations.addAll(stations)
        }
    }

    fun onDragStep(deltaY: Float) {
        if (!isEditMode) return
        val activeId = draggedStationId ?: return
        dragOffsetY += deltaY

        val currentIndex = localStations.indexOfFirst { it.id == activeId }
        if (currentIndex == -1) return

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currentItemInfo = visibleItems.find { it.index == currentIndex } ?: return
        val draggedCenterY = currentItemInfo.offset + (currentItemInfo.size / 2f) + dragOffsetY

        val targetItemInfo = visibleItems.find { item ->
            item.index != currentIndex &&
                draggedCenterY >= item.offset &&
                draggedCenterY <= (item.offset + item.size)
        }

        if (targetItemInfo != null) {
            val targetIndex = targetItemInfo.index.coerceIn(0, localStations.lastIndex)
            if (targetIndex != currentIndex) {
                val moved = localStations.removeAt(currentIndex)
                localStations.add(targetIndex, moved)
                dragOffsetY += (currentItemInfo.offset - targetItemInfo.offset).toFloat()
            }
        }

        val viewportEnd = listState.layoutInfo.viewportEndOffset
        if (draggedCenterY > viewportEnd - 120) {
            scope.launch { listState.scrollBy(18f) }
        } else if (draggedCenterY < 120) {
            scope.launch { listState.scrollBy(-18f) }
        }
    }

    fun finishDrag() {
        if (draggedStationId != null) {
            draggedStationId = null
            dragOffsetY = 0f
            onSaveRadioOrder(localStations.toList())
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 1. Üst Kategori Seçici (Türkçe FM / ☀️ Kürtçe FM)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedCategory == RadioCategory.TURKISH) {
                Button(
                    onClick = { onSelectCategory(RadioCategory.TURKISH) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Türkçe FM (${turkishRadios.size})", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectCategory(RadioCategory.TURKISH) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Türkçe FM (${turkishRadios.size})")
                }
            }

            if (selectedCategory == RadioCategory.KURDISH) {
                Button(
                    onClick = { onSelectCategory(RadioCategory.KURDISH) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("☀️ Kürtçe FM (${kurdishRadios.size})", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectCategory(RadioCategory.KURDISH) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("☀️ Kürtçe FM (${kurdishRadios.size})")
                }
            }
        }

        // Canlı Yayın Bağlantı / Hata Durumu Şeridi (Yayın açılmazsa kullanıcıya net bilgi verir)
        val errMsg = nowPlaying.errorMessage
        if (errMsg != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "❌ ${nowPlaying.title}: $errMsg",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        } else if (nowPlaying.isRadio && nowPlaying.isBuffering) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "⏳ ${nowPlaying.title} yayınına bağlanıyor...",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 2. SADECE üst bardaki "✏️ Düzenle" açıldığında (isEditMode == true) görünen sıralama ipucu ve Varsayılanları Geri Yükle butonu
        if (isEditMode) {
            Text(
                text = "✋ Sıralamak için radyoya basılı tutup (veya sağdaki ☰ simgesinden tutup) yukarı/aşağı sürükleyin.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            OutlinedButton(
                onClick = onRestoreDefaultsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🔄 Varsayılan $categoryLabel Radyolarını Geri Yükle",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. Radyo Listesi (Sürükle-bırak ve düzenleme butonları SADECE isEditMode == true iken aktiftir!)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(localStations, key = { _, st -> st.id }) { index, station ->
                val isThisPlaying = nowPlaying.title == station.name || nowPlaying.title == "⚠️ Açılamadı: ${station.name}"
                val isBeingDragged = isEditMode && draggedStationId == station.id

                val cardModifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isBeingDragged) 10f else 0f)
                    .graphicsLayer {
                        translationY = if (isBeingDragged) dragOffsetY else 0f
                        scaleX = if (isBeingDragged) 1.03f else 1f
                        scaleY = if (isBeingDragged) 1.03f else 1f
                        shadowElevation = if (isBeingDragged) 18f else 0f
                    }
                    .let { baseMod ->
                        if (isEditMode) {
                            baseMod.pointerInput(station.id, isEditMode) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedStationId = station.id
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDragStep(dragAmount.y)
                                    },
                                    onDragEnd = { finishDrag() },
                                    onDragCancel = { finishDrag() }
                                )
                            }
                        } else {
                            baseMod
                        }
                    }

                Card(
                    onClick = {
                        if (!isEditMode) {
                            onPlayRadio(station)
                        }
                    },
                    modifier = cardModifier,
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isBeingDragged -> MaterialTheme.colorScheme.secondaryContainer
                            isThisPlaying && errMsg != null -> MaterialTheme.colorScheme.errorContainer
                            isThisPlaying -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isBeingDragged) 8.dp else 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sıra Numarası Rozeti (1., 2., 3. ...)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.defaultMinSize(minWidth = 32.dp)
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (!isEditMode) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = station.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = station.genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isEditMode) {
                            // SADECE Düzenle modunda görünen aksiyonlar: 1.Yap (⏫), Düzenle (✏️), Sil (🗑️) ve Sürükleme Tutamacı (☰)
                            if (index > 0) {
                                TextButton(
                                    onClick = { onMoveRadioToTop(station) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("⏫ 1.Yap", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(
                                onClick = { onEditRadioClick(station) },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("✏️", style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(
                                onClick = { onDeleteRadioClick(station) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(32.dp)
                                    .pointerInput(station.id, isEditMode) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggedStationId = station.id
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onDragStep(dragAmount.y)
                                            },
                                            onDragEnd = { finishDrag() },
                                            onDragCancel = { finishDrag() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "☰",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        } else if (isThisPlaying) {
                            val badgeText = when {
                                errMsg != null -> "❌ Hata"
                                nowPlaying.isBuffering -> "⏳ Bağlanıyor"
                                nowPlaying.isPlaying -> "🔊 Çalıyor"
                                else -> "⏸ Seçili"
                            }
                            Text(
                                text = badgeText,
                                color = if (errMsg != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kullanıcı "⚠️ Örnek Link Formatı" butonuna bastığında açılan kompakt rehber ve örnek link kutusu.
 */
@Composable
fun RadioStreamFormatGuideBox(
    onSelectExampleUrl: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "📌 Geçerli Radyo Yayın Linki (Stream URL) Nasıl Olmalı?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "• ❌ Yanlış: https://www.radyositesi.com (Normal web sayfası adresi çalmaz)\n" +
                    "• ✅ Doğru 1 (HLS/.m3u8): https://.../playlist.m3u8\n" +
                    "• ✅ Doğru 2 (Icecast/MP3): https://.../stream veya .../yayin.mp3\n" +
                    "• ✅ Doğru 3 (Portlu): http://yayin.sunucu.com:8040/;",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "💡 İpucu: Google'da ararken 'radyo adı stream url' veya 'radyo adı m3u8' yazarak doğrudan ses adresini bulabilirsiniz.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onSelectExampleUrl("https://radio-trtradyo6.medya.trt.com.tr/master.m3u8") },
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("📋 Örnek .m3u8 Doldur", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onSelectExampleUrl("https://amed.ozelip.com/8040/stream") },
                    modifier = Modifier.weight(1f).height(28.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("📋 Örnek /stream Doldur", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Seçili Kategoriye (Türkçe FM veya ☀️ Kürtçe FM) Yeni Radyo Ekleme Penceresi.
 * Hem "🔍 İnternetten İsimle Ara" hem de "✍️ Manuel Link Ekle" sekmelerini içerir.
 */
@Composable
fun AddRadioStationDialog(
    category: RadioCategory,
    existingStations: List<RadioStation>,
    nowPlaying: NowPlayingUiState,
    onPreviewStream: (String, String, String) -> Unit,
    onAddStation: (name: String, genre: String, streamUrl: String, placeAtTop: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val categoryTitle = if (category == RadioCategory.KURDISH) "☀️ Kürtçe FM" else "Türkçe FM"

    var selectedTab by remember { mutableStateOf(0) } // 0: İnternetten Ara, 1: Manuel Ekle
    var placeAtTop by remember { mutableStateOf(true) }
    var hasTriggeredPreview by remember { mutableStateOf(false) }

    // Sekme 0 (İnternetten Arama) durumları
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<OnlineRadioCandidate>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }

    // Sekme 1 (Manuel Ekleme) durumları
    var manualName by remember { mutableStateOf("") }
    var manualGenre by remember {
        mutableStateOf(if (category == RadioCategory.KURDISH) "Kürtçe Canlı Radyo" else "Türkçe Canlı Radyo")
    }
    var manualUrl by remember { mutableStateOf("") }
    var showFormatGuide by remember { mutableStateOf(false) }

    val isManualAlreadyAdded = remember(manualName, manualUrl, existingStations) {
        val cleanUrl = RadioStations.sanitizeStreamUrl(manualUrl)
        val normUrl = RadioStations.normalizeStreamUrlForComparison(cleanUrl)
        val normName = RadioStations.normalizeRadioNameForComparison(manualName)
        if (normUrl.isBlank() && normName.isBlank()) return@remember false
        existingStations.any { existing ->
            val exNormUrl = RadioStations.normalizeStreamUrlForComparison(existing.streamUrl)
            val exNormName = RadioStations.normalizeRadioNameForComparison(existing.name)
            (normUrl.isNotBlank() && exNormUrl == normUrl) ||
            (normName.isNotBlank() && exNormName == normName)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("➕ $categoryTitle'e Radyo Ekle", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ekleme yöntemi seçici
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == 0) {
                        Button(
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🔍 İnternetten Ara", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🔍 İnternetten Ara", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (selectedTab == 1) {
                        Button(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("✍️ Manuel Link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f).height(34.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("✍️ Manuel Link", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Listenin Neresine Eklensin Seçeneği (1. Sıraya / En Alta)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📌 Listenin en üstüne (1. sıraya) ekle",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Switch(
                        checked = placeAtTop,
                        onCheckedChange = { placeAtTop = it }
                    )
                }

                // Eğer kullanıcı "▶️ Test Dinle" dediyse anlık bağlantı / hata durumunu kutu içinde göster!
                if (hasTriggeredPreview) {
                    val err = nowPlaying.errorMessage
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            err != null -> MaterialTheme.colorScheme.errorContainer
                            nowPlaying.isBuffering -> MaterialTheme.colorScheme.secondaryContainer
                            nowPlaying.isPlaying -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusText = when {
                            err != null -> "❌ Yayın Başarısız: $err"
                            nowPlaying.isBuffering -> "⏳ Test yayınına bağlanıyor, lütfen bekleyin..."
                            nowPlaying.isPlaying -> "✅ Yayın Çalışıyor! (${nowPlaying.title})"
                            else -> "ℹ️ Test durumu bekleniyor..."
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (err != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                if (selectedTab == 0) {
                    // SEKME 0: İNTERNETTEN İSİMLE RADYO ARAMA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Radyo, şehir veya dil (örn. Kürtçe, Mardin, Amed)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (searchQuery.trim().length >= 2) {
                                    scope.launch {
                                        isSearching = true
                                        hasSearched = true
                                        searchResults = RadioStations.searchOnlineStations(searchQuery)
                                        isSearching = false
                                    }
                                }
                            },
                            enabled = !isSearching && searchQuery.trim().length >= 2
                        ) {
                            Text("Ara")
                        }
                    }

                    when {
                        isSearching -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Canlı radyo istasyonları aranıyor...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        hasSearched && searchResults.isEmpty() -> {
                            Text(
                                text = "Sonuç bulunamadı. Farklı bir kelime deneyebilir veya '✍️ Manuel Link' sekmesinden yayın adresini doğrudan yapıştırabilirsiniz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        searchResults.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(searchResults, key = { it.streamUrl }) { cand ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = cand.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = cand.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val isAlreadyAdded = remember(cand, existingStations) {
                                                val candNormUrl = RadioStations.normalizeStreamUrlForComparison(cand.streamUrl)
                                                val candNormName = RadioStations.normalizeRadioNameForComparison(cand.name)
                                                existingStations.any { existing ->
                                                    val exNormUrl = RadioStations.normalizeStreamUrlForComparison(existing.streamUrl)
                                                    val exNormName = RadioStations.normalizeRadioNameForComparison(existing.name)
                                                    (candNormUrl.isNotBlank() && exNormUrl == candNormUrl) ||
                                                    (candNormName.isNotBlank() && exNormName == candNormName)
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        hasTriggeredPreview = true
                                                        onPreviewStream(cand.name, cand.subtitle, cand.streamUrl)
                                                    },
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("▶️ Test Dinle", style = MaterialTheme.typography.labelSmall)
                                                }
                                                if (isAlreadyAdded) {
                                                    FilledTonalButton(
                                                        onClick = {},
                                                        enabled = false,
                                                        modifier = Modifier.weight(1f).height(32.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("✓ Listenizde Ekli", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            onAddStation(cand.name, cand.subtitle, cand.streamUrl, placeAtTop)
                                                        },
                                                        modifier = Modifier.weight(1f).height(32.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("➕ Listeye Ekle", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = "💡 İstediğiniz radyonun adını, dili veya şehri (örn. Kürtçe, Mardin, Amed, Van, TRT) yazıp 'Ara' butonuna dokunun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // SEKME 1: MANUEL RADYO ADI & YAYIN LİNKİ EKLEME
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Radyo Adı (örn. Radyo Mardin)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualGenre,
                        onValueChange = { manualGenre = it },
                        label = { Text("Açıklama / Tür (örn. Kürtçe & Bölgesel)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Yayın Linki Üst Başlığı ve Dikkat / Örnek Format Rehberi Butonu (⚠️)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🔗 Canlı Yayın Linki:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = { showFormatGuide = !showFormatGuide },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = if (showFormatGuide) "🔼 Rehberi Gizle" else "⚠️ Örnek Link Formatı (Dikkat)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (showFormatGuide) {
                        RadioStreamFormatGuideBox(
                            onSelectExampleUrl = { example ->
                                manualUrl = example
                            }
                        )
                    }

                    // Hızlı Protokol Kısayol Butonları (http:// ve https://)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { manualUrl = RadioStations.applyProtocolPrefix(manualUrl, "https://") },
                            modifier = Modifier.weight(1f).height(30.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("🔒 https://", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { manualUrl = RadioStations.applyProtocolPrefix(manualUrl, "http://") },
                            modifier = Modifier.weight(1f).height(30.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("🌐 http://", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = manualUrl,
                        onValueChange = { manualUrl = RadioStations.sanitizeStreamUrl(it) },
                        label = { Text("Örn: https://.../playlist.m3u8 veya /stream") },
                        placeholder = { Text("https://yayin.sunucu.com/stream.mp3") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isManualAlreadyAdded) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Bu radyo veya yayın linki zaten listenizde ekli!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (manualUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                val cleaned = RadioStations.sanitizeStreamUrl(manualUrl)
                                manualUrl = cleaned
                                hasTriggeredPreview = true
                                onPreviewStream(
                                    manualName.ifBlank { "Yeni Radyo" },
                                    manualGenre.ifBlank { "Canlı Yayın" },
                                    cleaned
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("▶️ Yayın Linkini Şimdi Test Dinle")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1) {
                Button(
                    onClick = {
                        val cleaned = RadioStations.sanitizeStreamUrl(manualUrl)
                        onAddStation(manualName.trim(), manualGenre.trim(), cleaned, placeAtTop)
                    },
                    enabled = manualName.isNotBlank() && manualUrl.isNotBlank() && !isManualAlreadyAdded
                ) {
                    Text("💾 Kaydet ve Ekle")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

/**
 * Mevcut Bir Radyo İstasyonunun Adını, Açıklamasını veya Yayın Linkini Düzenleme Penceresi.
 */
@Composable
fun EditRadioStationDialog(
    station: RadioStation,
    nowPlaying: NowPlayingUiState,
    onPreviewStream: (String, String, String) -> Unit,
    onSave: (name: String, genre: String, streamUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(station.name) }
    var genre by remember { mutableStateOf(station.genre) }
    var streamUrl by remember { mutableStateOf(station.streamUrl) }
    var showFormatGuide by remember { mutableStateOf(false) }
    var hasTriggeredPreview by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Radyoyu Düzenle", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Radyo Adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Tür / Açıklama") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🔗 Canlı Yayın Linki:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = { showFormatGuide = !showFormatGuide },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text(
                            text = if (showFormatGuide) "🔼 Rehberi Gizle" else "⚠️ Örnek Link Formatı",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showFormatGuide) {
                    RadioStreamFormatGuideBox(
                        onSelectExampleUrl = { example -> streamUrl = example }
                    )
                }

                // Hızlı Protokol Kısayol Butonları (http:// ve https://)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { streamUrl = RadioStations.applyProtocolPrefix(streamUrl, "https://") },
                        modifier = Modifier.weight(1f).height(30.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("🔒 https://", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { streamUrl = RadioStations.applyProtocolPrefix(streamUrl, "http://") },
                        modifier = Modifier.weight(1f).height(30.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("🌐 http://", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = RadioStations.sanitizeStreamUrl(it) },
                    label = { Text("Canlı Yayın Linki (URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasTriggeredPreview) {
                    val err = nowPlaying.errorMessage
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            err != null -> MaterialTheme.colorScheme.errorContainer
                            nowPlaying.isBuffering -> MaterialTheme.colorScheme.secondaryContainer
                            nowPlaying.isPlaying -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusText = when {
                            err != null -> "❌ Yayın Başarısız: $err"
                            nowPlaying.isBuffering -> "⏳ Test yayınına bağlanıyor..."
                            nowPlaying.isPlaying -> "✅ Yayın Çalışıyor!"
                            else -> "ℹ️ Test durumu bekleniyor..."
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (err != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                if (streamUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val cleaned = RadioStations.sanitizeStreamUrl(streamUrl)
                            streamUrl = cleaned
                            hasTriggeredPreview = true
                            onPreviewStream(name, genre, cleaned)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("▶️ Bu Linki Test Dinle")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleaned = RadioStations.sanitizeStreamUrl(streamUrl)
                    onSave(name.trim(), genre.trim(), cleaned)
                },
                enabled = name.isNotBlank() && streamUrl.isNotBlank()
            ) {
                Text("💾 Güncelle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

@Composable
fun SongListScreen(
    songs: List<PlaylistSong>,
    currentPlayingTitle: String,
    isPlaying: Boolean,
    onPlaySong: (PlaylistSong) -> Unit,
    onShufflePlayAll: () -> Unit,
    onRemoveSong: (PlaylistSong) -> Unit
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Text("Liste boş", style = MaterialTheme.typography.titleMedium)
                Text("Sağ üstteki + ile şarkı ekle", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilledTonalButton(
                    onClick = onShufflePlayAll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔀 Bu Listeyi Karışık Çal (${songs.size} Şarkı)", fontWeight = FontWeight.Bold)
                }
            }
            items(songs, key = { it.id }) { song ->
                val isCurrent = currentPlayingTitle == song.title
                SongCard(
                    title = if (isCurrent && isPlaying) "🔊 ${song.title}" else song.title,
                    artist = song.artist,
                    durationMs = song.durationMs,
                    highlighted = isCurrent,
                    onClick = { onPlaySong(song) }
                ) {
                    IconButton(onClick = { onRemoveSong(song) }) {
                        Icon(Icons.Default.Delete, "Kaldır", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerScreen(
    deviceSongs: List<DeviceSong>,
    playlistSongs: List<PlaylistSong>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAdd: (DeviceSong) -> Unit
) {
    // 0: En Son Eklenen (Varsayılan), 1: İsme Göre (A-Z), 2: Sanatçıya Göre (A-Z)
    var sortMode by remember { mutableIntStateOf(0) }
    val playlistUris = remember(playlistSongs) { playlistSongs.map { it.uri }.toSet() }
    val filtered = remember(deviceSongs, searchQuery, sortMode) {
        val base = if (searchQuery.isBlank()) deviceSongs
        else deviceSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
        }
        when (sortMode) {
            1 -> base.sortedBy { it.title.lowercase(java.util.Locale("tr", "TR")) }
            2 -> base.sortedBy { it.artist.lowercase(java.util.Locale("tr", "TR")) }
            else -> base.sortedByDescending { it.dateAddedSec }
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery, onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Şarkı veya sanatçı ara…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        // Sıralama Seçici (Varsayılan: En Son Eklenen en başta!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = sortMode == 0,
                onClick = { sortMode = 0 },
                label = {
                    Text(
                        "🕒 Son Eklenen",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sortMode == 0) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = sortMode == 1,
                onClick = { sortMode = 1 },
                label = {
                    Text(
                        "🔤 İsme Göre (A-Z)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sortMode == 1) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = sortMode == 2,
                onClick = { sortMode = 2 },
                label = {
                    Text(
                        "🎤 Sanatçı",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sortMode == 2) FontWeight.Bold else FontWeight.Normal
                    )
                },
                modifier = Modifier.weight(0.85f)
            )
        }
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Müzikler taranıyor…")
                }
            }
            deviceSongs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Telefonda müzik bulunamadı")
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.uri }) { song ->
                    val inList = song.uri in playlistUris
                    SongCard(
                        title = song.title,
                        artist = song.artist,
                        durationMs = song.durationMs,
                        dimmed = inList,
                        onClick = if (!inList) ({ onAdd(song) }) else null
                    ) {
                        if (inList) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        else IconButton(onClick = { onAdd(song) }) {
                            Icon(Icons.Default.Add, "Ekle", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongCard(
    title: String,
    artist: String,
    durationMs: Long,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    val containerColor = when {
        highlighted -> MaterialTheme.colorScheme.primaryContainer
        dimmed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surface
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            SongCardContent(title, artist, durationMs, dimmed, trailing)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            SongCardContent(title, artist, durationMs, dimmed, trailing)
        }
    }
}

@Composable
private fun SongCardContent(
    title: String,
    artist: String,
    durationMs: Long,
    dimmed: Boolean,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.PlayArrow, null, modifier = Modifier.size(32.dp),
            tint = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)
            else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$artist · ${formatDuration(durationMs)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

@Composable
fun NewPlaylistDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Liste") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Liste adı (örn. Yolculuk)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Oluştur")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun RenamePlaylistDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liste Adını Değiştir") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Yeni liste adı") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != currentName
            ) {
                Text("Güncelle")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RifterSettingsDialog(
    autoUpdateEnabled: Boolean,
    autoUpdateInterval: Int,
    themeMode: Int,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onSelectTheme: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val intervalOptions = listOf(1, 3, 5, 10, 15)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("⚙️ Rifter Kokpit Ayarları", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1) Otomatik Hava & Yol Güncelleme
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "🌤️ Otomatik Hava & Yol Güncelleme",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (autoUpdateEnabled) "Aktif: Araçta her $autoUpdateInterval dakikada bir günceller"
                                    else "Kapalı: Sadece 'Şimdi Yenile' dediğinizde günceller",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = onToggleAutoUpdate
                            )
                        }

                        if (autoUpdateEnabled) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "⏱️ Güncelleme Sıklığı (Dakika):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                intervalOptions.forEach { min ->
                                    val selected = autoUpdateInterval == min
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectInterval(min) },
                                        label = { Text("${min} dk", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2) Kokpit & Uygulama Teması
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "🎨 Kokpit & Uygulama Teması",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = themeMode == Settings.THEME_DARK,
                                onClick = { onSelectTheme(Settings.THEME_DARK) },
                                label = { Text("🌙 Gece", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = themeMode == Settings.THEME_LIGHT,
                                onClick = { onSelectTheme(Settings.THEME_LIGHT) },
                                label = { Text("☀️ Gündüz", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = themeMode == Settings.THEME_SYSTEM,
                                onClick = { onSelectTheme(Settings.THEME_SYSTEM) },
                                label = { Text("🔄 Otomatik", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Tamam")
            }
        }
    )
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val s = ms / 1000; val m = s / 60; val sec = s % 60
    return "%d:%02d".format(m, sec)
}
