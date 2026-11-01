package com.asn.rifterlog.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlaylistRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.playlistDao()

    // ── Playlist ──────────────────────────────────────────────────────────────
    fun observePlaylists(): Flow<List<Playlist>> = dao.observePlaylists()
    suspend fun getPlaylists(): List<Playlist> = dao.getPlaylists()
    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(Playlist(name = name))
    suspend fun deletePlaylist(playlist: Playlist) = dao.deletePlaylist(playlist)
    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name)

    // ── Songs ─────────────────────────────────────────────────────────────────
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSong>> = dao.observeSongs(playlistId)
    suspend fun getSongs(playlistId: Long): List<PlaylistSong> = dao.getSongs(playlistId)
    suspend fun songCount(playlistId: Long): Int = dao.songCount(playlistId)

    suspend fun addSong(playlistId: Long, song: DeviceSong): Boolean {
        if (dao.songExistsInPlaylist(playlistId, song.uri) > 0) return false
        dao.insertSong(song.toPlaylistSong(playlistId))
        return true
    }

    suspend fun removeSong(id: Long) = dao.deleteSong(id)

    // ── Parking History ───────────────────────────────────────────────────────
    suspend fun addParkingRecord(
        lat: Double,
        lng: Double,
        address: String,
        timestampMillis: Long,
        title: String = "",
        note: String = ""
    ): Long =
        dao.insertParkingRecord(
            ParkingRecord(
                latitude = lat,
                longitude = lng,
                address = address,
                timestampMillis = timestampMillis,
                title = title,
                note = note
            )
        )

    fun observeParkingHistory(): Flow<List<ParkingRecord>> = dao.observeParkingHistory()
    suspend fun getRecentParkingHistory(): List<ParkingRecord> = dao.getRecentParkingHistory()
    suspend fun deleteParkingRecord(id: Long) = dao.deleteParkingRecord(id)
    suspend fun clearAllParkingRecords() = dao.clearAllParkingRecords()
    suspend fun updateParkingRecordDetails(id: Long, title: String, note: String) =
        dao.updateParkingRecordDetails(id, title, note)
    suspend fun updateParkingRecordByTimestamp(timestampMillis: Long, title: String, note: String) =
        dao.updateParkingRecordByTimestamp(timestampMillis, title, note)

    // ── Radio Stations (Dynamic CRUD + Persistent Ordering) ────────────────────
    suspend fun ensureDefaultRadiosSeeded() {
        if (dao.getRadioStationCount() == 0) {
            dao.insertRadioStations(RadioStations.allDefaults)
        }
    }

    fun observeRadioStations(category: RadioCategory): Flow<List<RadioStation>> =
        dao.observeRadioStations(category.name)

    suspend fun getRadioStations(category: RadioCategory): List<RadioStation> {
        ensureDefaultRadiosSeeded()
        return dao.getRadioStations(category.name)
    }

    suspend fun getRadioStationById(id: String): RadioStation? {
        ensureDefaultRadiosSeeded()
        return dao.getRadioStationById(id)
    }

    suspend fun addCustomRadio(
        rawName: String,
        rawGenre: String,
        rawStreamUrl: String,
        category: RadioCategory,
        placeAtTop: Boolean = true
    ): RadioStation {
        ensureDefaultRadiosSeeded()
        val prefix = if (category == RadioCategory.KURDISH) "☀️ " else "📻 "
        val cleanName = rawName.trim().let {
            if (it.startsWith("☀️") || it.startsWith("📻")) it else "$prefix$it"
        }
        val cleanGenre = rawGenre.trim().ifBlank {
            if (category == RadioCategory.KURDISH) "Kürtçe Canlı Radyo" else "Türkçe Canlı Radyo"
        }
        val existing = dao.getRadioStations(category.name).toMutableList()
        val cleanUrl = RadioStations.sanitizeStreamUrl(rawStreamUrl)

        // Zaten aynı yayın linki veya aynı radyo adı varsa mükerrer kayıt oluşturma
        val normCleanUrl = RadioStations.normalizeStreamUrlForComparison(cleanUrl)
        val normCleanName = RadioStations.normalizeRadioNameForComparison(cleanName)
        val alreadyPresent = existing.find { ex ->
            val exNormUrl = RadioStations.normalizeStreamUrlForComparison(ex.streamUrl)
            val exNormName = RadioStations.normalizeRadioNameForComparison(ex.name)
            (normCleanUrl.isNotBlank() && exNormUrl == normCleanUrl) ||
            (normCleanName.isNotBlank() && exNormName == normCleanName)
        }
        if (alreadyPresent != null) {
            return alreadyPresent
        }

        val newStation = RadioStation(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            genre = cleanGenre,
            streamUrl = cleanUrl,
            category = category.name,
            sortOrder = 0
        )
        if (placeAtTop) {
            existing.add(0, newStation)
        } else {
            existing.add(newStation)
        }
        val reindexed = existing.mapIndexed { idx, st -> st.copy(sortOrder = idx) }
        dao.insertRadioStations(reindexed)
        return reindexed.first { it.id == newStation.id }
    }

    suspend fun updateRadioStationDetails(id: String, rawName: String, rawGenre: String, rawStreamUrl: String) {
        val current = dao.getRadioStationById(id) ?: return
        val prefix = if (current.categoryEnum == RadioCategory.KURDISH) "☀️ " else "📻 "
        val cleanName = rawName.trim().let {
            if (it.startsWith("☀️") || it.startsWith("📻")) it else "$prefix$it"
        }
        val cleanGenre = rawGenre.trim().ifBlank { current.genre }
        val cleanUrl = RadioStations.sanitizeStreamUrl(rawStreamUrl)
        dao.updateRadioStationDetails(id, cleanName, cleanGenre, cleanUrl)
    }

    suspend fun deleteRadioStation(category: RadioCategory, id: String) {
        dao.deleteRadioStation(id)
        val remaining = dao.getRadioStations(category.name)
            .mapIndexed { idx, st -> st.copy(sortOrder = idx) }
        if (remaining.isNotEmpty()) {
            dao.insertRadioStations(remaining)
        }
    }

    suspend fun moveRadioStation(category: RadioCategory, stationId: String, targetIndex: Int) {
        val list = dao.getRadioStations(category.name).toMutableList()
        val fromIndex = list.indexOfFirst { it.id == stationId }
        if (fromIndex == -1) return
        val clampedTarget = targetIndex.coerceIn(0, list.lastIndex)
        if (fromIndex == clampedTarget) return
        val item = list.removeAt(fromIndex)
        list.add(clampedTarget, item)
        val reindexed = list.mapIndexed { idx, st -> st.copy(sortOrder = idx) }
        dao.insertRadioStations(reindexed)
    }

    suspend fun saveRadioOrder(orderedStations: List<RadioStation>) {
        val reindexed = orderedStations.mapIndexed { idx, st -> st.copy(sortOrder = idx) }
        dao.insertRadioStations(reindexed)
    }

    suspend fun restoreDefaultRadios(category: RadioCategory) {
        dao.deleteRadioStationsByCategory(category.name)
        val defaults = if (category == RadioCategory.KURDISH) {
            RadioStations.defaultKurdishList
        } else {
            RadioStations.defaultTurkishList
        }
        dao.insertRadioStations(defaults)
    }

    // ── Device scan ───────────────────────────────────────────────────────────
    suspend fun scanDeviceSongs(context: Context): List<DeviceSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<DeviceSong>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // Varsayılan olarak telefona en son indirilen / eklenen müzikler en üstte gelsin
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val addedSec = cursor.getLong(dateAddedCol)
                val modSec = cursor.getLong(dateModCol)
                songs.add(DeviceSong(
                    uri = uri.toString(),
                    title = cursor.getString(titleCol) ?: "Bilinmeyen",
                    artist = cursor.getString(artistCol) ?: "Bilinmeyen Sanatçı",
                    durationMs = cursor.getLong(durationCol),
                    dateAddedSec = maxOf(addedSec, modSec)
                ))
            }
        }
        songs
    }
}

data class DeviceSong(
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val dateAddedSec: Long = 0L
) {
    fun toPlaylistSong(playlistId: Long) = PlaylistSong(
        playlistId = playlistId,
        uri = uri, title = title, artist = artist, durationMs = durationMs
    )
    fun contentUri(): Uri = Uri.parse(uri)
}
