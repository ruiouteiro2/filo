package com.filo.app.data.weather

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filo.app.R
import com.filo.app.core.prefs.filoDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private const val TAG = "Weather"

@Serializable
data class Weather(
    val temperatureC: Int,
    val code: Int,
    val fetchedAt: Long,
    val lat: Double,
    val lon: Double,
    /** Defaults keep a cache written by an older version decodable. */
    val isDay: Boolean = true,
    val feelsLikeC: Int? = null,
    val humidity: Int? = null,
    val windKmh: Int? = null,
)

@Serializable
private data class OpenMeteoResponse(val current: Current) {
    @Serializable
    data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int = 1,
        @SerialName("apparent_temperature") val feelsLike: Double? = null,
        @SerialName("relative_humidity_2m") val humidity: Int? = null,
        @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    )
}

/**
 * Open-Meteo needs no key and asks for no attribution. One call, cached for 30 minutes and
 * refreshed by the periodic worker, so opening the app ten times costs one request.
 */
class WeatherRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("weather_cache")

    private val http: HttpClient by lazy { HttpClient(OkHttp) }

    suspend fun cached(): Weather? = withContext(Dispatchers.IO) {
        val raw = context.filoDataStore.data.first()[key] ?: return@withContext null
        runCatching { json.decodeFromString(Weather.serializer(), raw) }.getOrNull()
    }

    /** Returns the cache unless it is stale or for a different place. */
    suspend fun get(lat: Double, lon: Double, force: Boolean = false): Weather? {
        val cache = cached()
        if (!force && cache != null && cache.isFreshFor(lat, lon)) return cache
        return fetch(lat, lon) ?: cache
    }

    private suspend fun fetch(lat: Double, lon: Double): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day,apparent_temperature,relative_humidity_2m,wind_speed_10m"
            val body = http.get(url).bodyAsText()
            val parsed = json.decodeFromString(OpenMeteoResponse.serializer(), body)
            val weather = Weather(
                temperatureC = parsed.current.temperature.roundToInt(),
                code = parsed.current.weatherCode,
                fetchedAt = Instant.now().toEpochMilli(),
                lat = lat,
                lon = lon,
                isDay = parsed.current.isDay == 1,
                feelsLikeC = parsed.current.feelsLike?.roundToInt(),
                humidity = parsed.current.humidity,
                windKmh = parsed.current.windSpeed?.roundToInt(),
            )
            context.filoDataStore.edit { it[key] = json.encodeToString(Weather.serializer(), weather) }
            weather
        }.onFailure { Log.w(TAG, "weather fetch failed", it) }.getOrNull()
    }

    companion object {
        val CACHE_FOR: Duration = Duration.ofMinutes(30)
    }
}

private fun Weather.isFreshFor(lat: Double, lon: Double): Boolean {
    val sameSpot = kotlin.math.abs(this.lat - lat) < 0.2 && kotlin.math.abs(this.lon - lon) < 0.2
    val age = Duration.between(Instant.ofEpochMilli(fetchedAt), Instant.now())
    return sameSpot && age < WeatherRepository.CACHE_FOR
}

/** WMO code to our small icon set and a localised description. */
object Wmo {

    fun descriptionRes(code: Int, isDay: Boolean = true): Int = when (code) {
        0 -> if (isDay) R.string.weather_clear else R.string.weather_clear_night
        1 -> R.string.weather_mainly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_overcast
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57 -> R.string.weather_drizzle
        61, 63, 65 -> R.string.weather_rain
        66, 67 -> R.string.weather_freezing_rain
        71, 73, 75, 77 -> R.string.weather_snow
        80, 81, 82 -> R.string.weather_showers
        85, 86 -> R.string.weather_snow_showers
        95, 96, 99 -> R.string.weather_thunderstorm
        else -> R.string.weather_unknown
    }

    /**
     * Night gets its own sky. A sun over "her" card at midnight reads as broken even when
     * the temperature is right, because the one thing everyone can check is whether it is
     * dark outside.
     */
    fun iconRes(code: Int, isDay: Boolean = true): Int = when (code) {
        0 -> if (isDay) R.drawable.ic_weather_clear else R.drawable.ic_weather_clear_night
        1, 2 -> if (isDay) R.drawable.ic_weather_partly else R.drawable.ic_weather_partly_night
        3 -> R.drawable.ic_weather_cloud
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_weather_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
        95, 96, 99 -> R.drawable.ic_weather_thunder
        else -> R.drawable.ic_weather_cloud
    }
}
