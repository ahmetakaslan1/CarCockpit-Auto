package com.asn.rifterlog.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

data class CockpitSnapshot(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Int,
    val altitudeMeters: Int,
    val address: String,
    val temperatureC: Double?,
    val windSpeedKmh: Double?,
    val weatherDesc: String?,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

object CockpitWeatherHelper {

    @Volatile
    private var cachedWeather: Triple<Double, Double, String>? = null

    suspend fun fetchCockpitSnapshot(context: Context): CockpitSnapshot? = withContext(Dispatchers.IO) {
        val locPair = ParkingLocationHelper.fetchCurrentLocation(context) ?: return@withContext null
        val (loc, addr) = locPair
        val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt() else 0
        val altitudeM = if (loc.hasAltitude()) loc.altitude.roundToInt() else 0

        // Çift motorlu güvenilir hava durumu:
        // 1. Motor: wttr.in (hızlıdır, IP/CGNAT kota sınırına takılmaz)
        // 2. Motor: Open-Meteo (yedek)
        // 3. Önbellek: Ağ bağlantısı kesilirse veya tünelde önceki hava durumunu korur
        val freshWeather = fetchWttrInWeather(loc.latitude, loc.longitude)
            ?: fetchOpenMeteoWeather(loc.latitude, loc.longitude)

        if (freshWeather != null) {
            cachedWeather = freshWeather
        }
        val weather = freshWeather ?: cachedWeather

        CockpitSnapshot(
            latitude = loc.latitude,
            longitude = loc.longitude,
            speedKmh = speedKmh,
            altitudeMeters = altitudeM,
            address = addr,
            temperatureC = weather?.first,
            windSpeedKmh = weather?.second,
            weatherDesc = weather?.third
        )
    }

    private fun fetchWttrInWeather(lat: Double, lng: Double): Triple<Double, Double, String>? {
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://wttr.in/%.4f,%.4f?format=j1",
                lat,
                lng
            )
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RifterLog/1.0 (Android Auto Companion)")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val currentArr = json.optJSONArray("current_condition") ?: return null
            if (currentArr.length() == 0) return null
            val current = currentArr.getJSONObject(0)
            val temp = current.optString("temp_C", "").toDoubleOrNull() ?: return null
            val wind = current.optString("windspeedKmph", "0").toDoubleOrNull() ?: 0.0
            val code = current.optString("weatherCode", "113").toIntOrNull() ?: 113
            val descArr = current.optJSONArray("weatherDesc")
            val rawDesc = if (descArr != null && descArr.length() > 0) {
                descArr.getJSONObject(0).optString("value", "")
            } else ""
            val trDesc = wttrCodeToTurkish(code, rawDesc)
            Triple(temp, wind, trDesc)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchOpenMeteoWeather(lat: Double, lng: Double): Triple<Double, Double, String>? {
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true",
                lat,
                lng
            )
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RifterLog/1.0 (Android Auto Companion)")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current_weather")
            val temp = current.getDouble("temperature")
            val wind = current.getDouble("windspeed")
            val code = current.getInt("weathercode")
            Triple(temp, wind, weatherCodeToTurkish(code))
        } catch (_: Exception) {
            null
        }
    }

    private fun wttrCodeToTurkish(code: Int, rawDesc: String): String = when (code) {
        113 -> "☀️ Açık ve Güneşli"
        116 -> "⛅ Parçalı Bulutlu"
        119 -> "☁️ Bulutlu"
        122 -> "☁️ Çok Bulutlu / Kapalı"
        143, 248, 260 -> "🌫️ Sisli / Puslu"
        176, 263, 266, 293, 296 -> "🌦️ Hafif Yağmurlu / Çisenti"
        299, 302, 305, 308, 356, 359 -> "🌧️ Yağmurlu / Sağanak"
        179, 182, 185, 227, 230, 323, 326, 329, 332, 335, 338 -> "❄️ Kar Yağışlı"
        200, 386, 389, 392, 395 -> "⛈️ Gök Gürültülü Fırtına"
        else -> {
            val lower = rawDesc.lowercase(Locale.US)
            when {
                lower.contains("sun") || lower.contains("clear") -> "☀️ Açık"
                lower.contains("cloud") || lower.contains("overcast") -> "⛅ Bulutlu"
                lower.contains("rain") || lower.contains("drizzle") || lower.contains("shower") -> "🌧️ Yağmurlu"
                lower.contains("snow") -> "❄️ Karlı"
                lower.contains("fog") || lower.contains("mist") -> "🌫️ Sisli"
                lower.contains("thunder") || lower.contains("storm") -> "⛈️ Fırtınalı"
                rawDesc.isNotBlank() -> "🌤️ $rawDesc"
                else -> "🌤️ Normal"
            }
        }
    }

    private fun weatherCodeToTurkish(code: Int): String = when (code) {
        0 -> "☀️ Açık ve Güneşli"
        1, 2 -> "⛅ Az / Parçalı Bulutlu"
        3 -> "☁️ Kapalı / Bulutlu"
        45, 48 -> "🌫️ Sisli"
        51, 53, 55 -> "🌦️ Hafif Çisenti"
        61, 63, 65 -> "🌧️ Yağmurlu"
        71, 73, 75, 77 -> "❄️ Kar Yağışlı"
        80, 81, 82 -> "🌧️ Sağanak Yağışlı"
        95, 96, 99 -> "⛈️ Gök Gürültülü Fırtına"
        else -> "🌤️ Normal"
    }
}

