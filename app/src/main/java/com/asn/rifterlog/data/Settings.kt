package com.asn.rifterlog.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParkedLocation(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val address: String,
    val title: String = "",
    val note: String = ""
)

/**
 * Uygulama ayarları (karıştır modu, tekrar modu, tema modu, son çalınan ve park konumu).
 */
class Settings(context: Context) {
    companion object {
        const val THEME_DARK = 0   // Gece sürüş modu (Varsayılan - göz almaz)
        const val THEME_LIGHT = 1  // Gündüz açık mod
        const val THEME_SYSTEM = 2 // Sistem otomatik
    }

    private val prefs = context.getSharedPreferences("rifter_settings", Context.MODE_PRIVATE)

    private val _parkedLocationFlow = MutableStateFlow(loadParkedLocation())
    val parkedLocationFlow: StateFlow<ParkedLocation?> = _parkedLocationFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(prefs.getInt("theme_mode", THEME_DARK))
    val themeModeFlow: StateFlow<Int> = _themeModeFlow.asStateFlow()

    private val _autoUpdateEnabledFlow = MutableStateFlow(prefs.getBoolean("auto_update_enabled", true))
    val autoUpdateEnabledFlow: StateFlow<Boolean> = _autoUpdateEnabledFlow.asStateFlow()

    private val _autoUpdateIntervalFlow = MutableStateFlow(prefs.getInt("auto_update_interval_min", 5))
    val autoUpdateIntervalFlow: StateFlow<Int> = _autoUpdateIntervalFlow.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "has_parked_loc", "parked_time", "parked_addr", "parked_title", "parked_note" -> {
                _parkedLocationFlow.value = loadParkedLocation()
            }
            "theme_mode" -> {
                _themeModeFlow.value = prefs.getInt("theme_mode", THEME_DARK)
            }
            "auto_update_enabled" -> {
                _autoUpdateEnabledFlow.value = prefs.getBoolean("auto_update_enabled", true)
            }
            "auto_update_interval_min" -> {
                _autoUpdateIntervalFlow.value = prefs.getInt("auto_update_interval_min", 5)
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    var themeMode: Int
        get() = prefs.getInt("theme_mode", THEME_DARK)
        set(v) {
            prefs.edit().putInt("theme_mode", v).apply()
            _themeModeFlow.value = v
        }

    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean("auto_update_enabled", true)
        set(v) {
            prefs.edit().putBoolean("auto_update_enabled", v).apply()
            _autoUpdateEnabledFlow.value = v
        }

    var autoUpdateIntervalMinutes: Int
        get() = prefs.getInt("auto_update_interval_min", 5)
        set(v) {
            val safe = v.coerceIn(1, 60)
            prefs.edit().putInt("auto_update_interval_min", safe).apply()
            _autoUpdateIntervalFlow.value = safe
        }

    fun cycleThemeMode(): Int {
        val next = when (themeMode) {
            THEME_DARK -> THEME_LIGHT
            THEME_LIGHT -> THEME_SYSTEM
            else -> THEME_DARK
        }
        themeMode = next
        return next
    }

    var shuffleEnabled: Boolean
        get() = prefs.getBoolean("shuffle", false)
        set(v) = prefs.edit().putBoolean("shuffle", v).apply()

    var repeatOneEnabled: Boolean
        get() = prefs.getBoolean("repeat_one", false)
        set(v) = prefs.edit().putBoolean("repeat_one", v).apply()

    var lastPlaylistId: Long
        get() = prefs.getLong("last_playlist_id", -1L)
        set(v) = prefs.edit().putLong("last_playlist_id", v).apply()

    var lastSongIndex: Int
        get() = prefs.getInt("last_song_index", 0)
        set(v) = prefs.edit().putInt("last_song_index", v).apply()

    var lastPositionMs: Long
        get() = prefs.getLong("last_position_ms", 0L)
        set(v) = prefs.edit().putLong("last_position_ms", v).apply()

    var lastSongTitle: String
        get() = prefs.getString("last_song_title", "") ?: ""
        set(v) = prefs.edit().putString("last_song_title", v).apply()

    var lastSongArtist: String
        get() = prefs.getString("last_song_artist", "") ?: ""
        set(v) = prefs.edit().putString("last_song_artist", v).apply()

    fun getParkedLocation(): ParkedLocation? = loadParkedLocation()

    private fun loadParkedLocation(): ParkedLocation? {
        if (!prefs.getBoolean("has_parked_loc", false)) return null
        val lat = Double.fromBits(prefs.getLong("parked_lat_bits", 0L))
        val lng = Double.fromBits(prefs.getLong("parked_lng_bits", 0L))
        val time = prefs.getLong("parked_time", 0L)
        val addr = prefs.getString("parked_addr", "") ?: ""
        val title = prefs.getString("parked_title", "") ?: ""
        val note = prefs.getString("parked_note", "") ?: ""
        return ParkedLocation(lat, lng, time, addr, title, note)
    }

    fun saveParkedLocation(lat: Double, lng: Double, address: String, title: String = "", note: String = ""): ParkedLocation {
        val now = System.currentTimeMillis()
        val loc = ParkedLocation(lat, lng, now, address, title, note)
        prefs.edit()
            .putBoolean("has_parked_loc", true)
            .putLong("parked_lat_bits", lat.toRawBits())
            .putLong("parked_lng_bits", lng.toRawBits())
            .putLong("parked_time", now)
            .putString("parked_addr", address)
            .putString("parked_title", title)
            .putString("parked_note", note)
            .commit()
        _parkedLocationFlow.value = loc
        return loc
    }

    fun updateParkedLocationDetails(title: String, note: String): ParkedLocation? {
        val current = loadParkedLocation() ?: return null
        val updated = current.copy(title = title, note = note)
        prefs.edit()
            .putString("parked_title", title)
            .putString("parked_note", note)
            .commit()
        _parkedLocationFlow.value = updated
        return updated
    }

    fun clearParkedLocation() {
        prefs.edit().putBoolean("has_parked_loc", false).commit()
        _parkedLocationFlow.value = null
    }
}
