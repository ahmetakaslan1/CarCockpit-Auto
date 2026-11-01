package com.asn.rifterlog.media

object MediaIds {
    const val ROOT = "root"

    // Üst sekmeler (Android Auto ekranının tepesinde doğrudan erişilen 3 ana sekme)
    const val TAB_MUSIC = "tab/music"
    const val TAB_WEATHER_TRIP = "tab/weather_trip"
    const val TAB_COCKPIT = "tab/cockpit"

    // Müzik & Radyo klasör ve eylemleri
    const val ACTION_RESUME = "action/resume"
    const val FOLDER_RADIOS = "folder/radios"
    const val FOLDER_RADIOS_TURKISH = "folder/radios_turkish"
    const val FOLDER_RADIOS_KURDISH = "folder/radios_kurdish"

    fun shufflePlaylistId(playlistId: Long) = "action/shuffle_playlist/$playlistId"
    fun parseShufflePlaylistId(mediaId: String): Long? =
        if (mediaId.startsWith("action/shuffle_playlist/"))
            mediaId.removePrefix("action/shuffle_playlist/").toLongOrNull()
        else null

    fun radioId(stationId: String) = "radio/$stationId"
    fun parseRadioId(mediaId: String): String? =
        if (mediaId.startsWith("radio/")) mediaId.removePrefix("radio/")
        else null

    // Kokpit klasör ve eylemleri (Tamamı FLAG_BROWSABLE çalışır, müzik çalar ekranına atmaz!)
    const val ACTION_SAVE_PARKING = "action/save_parking"
    const val ACTION_INFO_PARKING = "action/info_parking"
    const val ACTION_TOGGLE_THEME = "action/toggle_theme"
    const val ACTION_REFRESH_WEATHER = "action/refresh_weather"
    const val DETAIL_WEATHER = "detail/weather"
    const val DETAIL_SPEED_ALT = "detail/speed_alt"
    const val DETAIL_DURATION = "detail/duration"
    const val FOLDER_TRIP_WEATHER = "folder/trip_weather"
    const val FOLDER_PARKING_HISTORY = "folder/parking_history"
    const val ACTION_NOOP = "action/noop"

    fun parkingHistoryItemId(id: Long) = "parking_hist/$id"
    fun parseParkingHistoryItemId(mediaId: String): Long? =
        if (mediaId.startsWith("parking_hist/")) mediaId.removePrefix("parking_hist/").toLongOrNull()
        else null

    // Custom PlaybackState eylemleri (Şimdi Çalıyor ekranındaki Karıştır ve Tekrar butonları)
    const val CUSTOM_ACTION_SHUFFLE = "custom_action_shuffle"
    const val CUSTOM_ACTION_REPEAT = "custom_action_repeat"

    fun playlistId(id: Long) = "playlist/$id"
    fun parsePlaylistId(mediaId: String): Long? =
        if (mediaId.startsWith("playlist/")) mediaId.removePrefix("playlist/").toLongOrNull()
        else null

    fun songId(playlistId: Long, songId: Long) = "song/$playlistId/$songId"
    fun parseSongId(mediaId: String): Pair<Long, Long>? {
        if (!mediaId.startsWith("song/")) return null
        val parts = mediaId.removePrefix("song/").split("/")
        if (parts.size != 2) return null
        return Pair(parts[0].toLongOrNull() ?: return null, parts[1].toLongOrNull() ?: return null)
    }
}
