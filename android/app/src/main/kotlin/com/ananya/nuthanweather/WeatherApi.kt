package com.ananya.nuthanweather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Coordinates(val latitude: Double, val longitude: Double)

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String?,
    val country: String?,
) {
    val label: String
        get() = listOfNotNull(name, region, country)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(", ")

    val coordinates: Coordinates
        get() = Coordinates(latitude, longitude)
}

data class CurrentConditions(
    val time: LocalDateTime,
    val temperature: Double?,
    val feelsLike: Double?,
    val humidity: Int?,
    val windKmh: Double?,
    val precipitationMm: Double?,
    val weatherCode: Int,
)

data class HourlyConditions(
    val time: LocalDateTime,
    val temperature: Double?,
    val precipitationChance: Int?,
    val weatherCode: Int,
)

data class DailyConditions(
    val date: LocalDate,
    val high: Double?,
    val low: Double?,
    val precipitationChance: Int?,
    val weatherCode: Int,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
)

data class WeatherForecast(
    val timezone: String,
    val current: CurrentConditions,
    val hourly: List<HourlyConditions>,
    val daily: List<DailyConditions>,
)

object WeatherApi {
    suspend fun searchPlaces(query: String): List<Place> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val response = getJson(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=8&language=en&format=json",
        )
        val results = response.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
                if (!latitude.isFinite() || !longitude.isFinite()) continue
                add(
                    Place(
                        name = name,
                        latitude = latitude,
                        longitude = longitude,
                        region = item.optString("admin1").takeIf(String::isNotBlank),
                        country = item.optString("country").takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    suspend fun forecast(coordinates: Coordinates): WeatherForecast = withContext(Dispatchers.IO) {
        require(coordinates.latitude in -90.0..90.0 && coordinates.longitude in -180.0..180.0) {
            "The selected location is invalid."
        }
        val query = listOf(
            "latitude=${coordinates.latitude}",
            "longitude=${coordinates.longitude}",
            "temperature_unit=celsius",
            "wind_speed_unit=kmh",
            "precipitation_unit=mm",
            "current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m",
            "hourly=temperature_2m,precipitation_probability,weather_code",
            "daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
            "timezone=auto",
            "forecast_days=7",
        ).joinToString("&")

        val response = getJson("https://api.open-meteo.com/v1/forecast?$query")
        val timezone = response.optString("timezone").takeIf(String::isNotBlank)
            ?: throw WeatherServiceException("The weather service did not return the location time zone.")
        val current = response.optJSONObject("current")
            ?: throw WeatherServiceException("The weather service returned an incomplete forecast.")
        val hourly = response.optJSONObject("hourly")
            ?: throw WeatherServiceException("The hourly forecast is unavailable.")
        val daily = response.optJSONObject("daily")
            ?: throw WeatherServiceException("The daily forecast is unavailable.")

        WeatherForecast(
            timezone = timezone,
            current = CurrentConditions(
                time = current.optString("time").toLocalDateTime("current time"),
                temperature = current.optionalDouble("temperature_2m"),
                feelsLike = current.optionalDouble("apparent_temperature"),
                humidity = current.optionalInt("relative_humidity_2m"),
                windKmh = current.optionalDouble("wind_speed_10m"),
                precipitationMm = current.optionalDouble("precipitation"),
                weatherCode = current.optInt("weather_code", -1),
            ),
            hourly = hourly.toHourlyConditions(),
            daily = daily.toDailyConditions(),
        )
    }

    fun formatLocalClock(
        timezone: String,
        instant: Instant,
        locale: Locale = Locale.getDefault(),
    ): String {
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneOffset.UTC)
        val formatter = DateTimeFormatter.ofPattern(
            "EEEE, MMMM d · h:mm a",
            locale,
        ).withZone(zone)
        return formatter.format(instant)
    }

    fun localForecastTimes(forecast: WeatherForecast, instant: Instant): List<HourlyConditions> {
        val zone = runCatching { ZoneId.of(forecast.timezone) }.getOrDefault(ZoneOffset.UTC)
        val now = LocalDateTime.ofInstant(instant, zone)
        return forecast.hourly.filter { !it.time.isBefore(now) }.take(6)
    }

    private fun JSONObject.toHourlyConditions(): List<HourlyConditions> {
        val times = optJSONArray("time") ?: return emptyList()
        val temperatures = optJSONArray("temperature_2m")
        val rainChances = optJSONArray("precipitation_probability")
        val codes = optJSONArray("weather_code")
        return buildList {
            for (index in 0 until times.length()) {
                val time = times.optString(index).toLocalDateTimeOrNull() ?: continue
                add(
                    HourlyConditions(
                        time = time,
                        temperature = temperatures?.optionalDouble(index),
                        precipitationChance = rainChances?.optionalInt(index),
                        weatherCode = codes?.optInt(index, -1) ?: -1,
                    ),
                )
            }
        }
    }

    private fun JSONObject.toDailyConditions(): List<DailyConditions> {
        val dates = optJSONArray("time") ?: return emptyList()
        val codes = optJSONArray("weather_code")
        val highs = optJSONArray("temperature_2m_max")
        val lows = optJSONArray("temperature_2m_min")
        val rainChances = optJSONArray("precipitation_probability_max")
        val sunrises = optJSONArray("sunrise")
        val sunsets = optJSONArray("sunset")
        return buildList {
            for (index in 0 until dates.length()) {
                val date = runCatching { LocalDate.parse(dates.optString(index)) }.getOrNull() ?: continue
                add(
                    DailyConditions(
                        date = date,
                        high = highs?.optionalDouble(index),
                        low = lows?.optionalDouble(index),
                        precipitationChance = rainChances?.optionalInt(index),
                        weatherCode = codes?.optInt(index, -1) ?: -1,
                        sunrise = sunrises?.optString(index)?.toLocalDateTimeOrNull(),
                        sunset = sunsets?.optString(index)?.toLocalDateTimeOrNull(),
                    ),
                )
            }
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            val payload = runCatching { JSONObject(body) }.getOrElse {
                throw WeatherServiceException("The weather service returned an unreadable response.")
            }
            if (status !in 200..299 || payload.optBoolean("error")) {
                val reason = payload.optString("reason").takeIf(String::isNotBlank)
                throw WeatherServiceException(
                    reason ?: "The weather service returned an error (HTTP $status).",
                )
            }
            return payload
        } finally {
            connection.disconnect()
        }
    }
}

class WeatherServiceException(message: String) : Exception(message)

private fun String.toLocalDateTime(description: String): LocalDateTime =
    toLocalDateTimeOrNull() ?: throw WeatherServiceException("The weather service returned an invalid $description.")

private fun String.toLocalDateTimeOrNull(): LocalDateTime? =
    runCatching { LocalDateTime.parse(this) }.getOrNull()

private fun JSONObject.optionalDouble(key: String): Double? {
    if (isNull(key)) return null
    return optDouble(key, Double.NaN).takeIf(Double::isFinite)
}

private fun JSONObject.optionalInt(key: String): Int? {
    if (isNull(key)) return null
    return optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
}

private fun JSONArray.optionalDouble(index: Int): Double? {
    if (isNull(index)) return null
    return optDouble(index, Double.NaN).takeIf(Double::isFinite)
}

private fun JSONArray.optionalInt(index: Int): Int? {
    if (isNull(index)) return null
    return optInt(index, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "Conditions unavailable"
}
