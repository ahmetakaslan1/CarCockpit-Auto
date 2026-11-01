package com.asn.rifterlog.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    // ── Playlist CRUD ──────────────────────────────────────────────────────────
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun observePlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    suspend fun getPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    // ── Song CRUD ──────────────────────────────────────────────────────────────
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSong>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY addedAt ASC")
    suspend fun getSongs(playlistId: Long): List<PlaylistSong>

    @Insert
    suspend fun insertSong(song: PlaylistSong): Long

    @Query("DELETE FROM playlist_songs WHERE id = :id")
    suspend fun deleteSong(id: Long)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND uri = :uri")
    suspend fun songExistsInPlaylist(playlistId: Long, uri: String): Int

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun songCount(playlistId: Long): Int

    // ── Parking History CRUD ───────────────────────────────────────────────────
    @Insert
    suspend fun insertParkingRecord(record: ParkingRecord): Long

    @Query("SELECT * FROM parking_records ORDER BY timestampMillis DESC")
    fun observeParkingHistory(): Flow<List<ParkingRecord>>

    @Query("SELECT * FROM parking_records ORDER BY timestampMillis DESC LIMIT 20")
    suspend fun getRecentParkingHistory(): List<ParkingRecord>

    @Query("DELETE FROM parking_records WHERE id = :id")
    suspend fun deleteParkingRecord(id: Long)

    @Query("DELETE FROM parking_records")
    suspend fun clearAllParkingRecords()

    @Query("UPDATE parking_records SET title = :title, note = :note WHERE id = :id")
    suspend fun updateParkingRecordDetails(id: Long, title: String, note: String)

    @Query("UPDATE parking_records SET title = :title, note = :note WHERE timestampMillis = :timestampMillis")
    suspend fun updateParkingRecordByTimestamp(timestampMillis: Long, title: String, note: String)

    // ── Radio Stations CRUD & Ordering ─────────────────────────────────────────
    @Query("SELECT * FROM radio_stations WHERE category = :category ORDER BY sortOrder ASC")
    fun observeRadioStations(category: String): Flow<List<RadioStation>>

    @Query("SELECT * FROM radio_stations WHERE category = :category ORDER BY sortOrder ASC")
    suspend fun getRadioStations(category: String): List<RadioStation>

    @Query("SELECT * FROM radio_stations WHERE id = :id LIMIT 1")
    suspend fun getRadioStationById(id: String): RadioStation?

    @Query("SELECT COUNT(*) FROM radio_stations")
    suspend fun getRadioStationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRadioStations(stations: List<RadioStation>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRadioStation(station: RadioStation)

    @Query("UPDATE radio_stations SET name = :name, genre = :genre, streamUrl = :streamUrl WHERE id = :id")
    suspend fun updateRadioStationDetails(id: String, name: String, genre: String, streamUrl: String)

    @Query("UPDATE radio_stations SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateRadioSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM radio_stations WHERE id = :id")
    suspend fun deleteRadioStation(id: String)

    @Query("DELETE FROM radio_stations WHERE category = :category")
    suspend fun deleteRadioStationsByCategory(category: String)
}
