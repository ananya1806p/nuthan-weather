package com.ananya.nuthanweather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.util.Locale

class WeatherApiTest {
    @Test
    fun locationClockUsesTheRequestedIanaTimezone() {
        val instant = Instant.parse("2026-01-15T12:00:00Z")

        assertEquals(
            "Thursday, January 15 · 5:30 PM",
            WeatherApi.formatLocalClock("Asia/Kolkata", instant, Locale.US),
        )
        assertEquals(
            "Thursday, January 15 · 4:00 AM",
            WeatherApi.formatLocalClock("America/Los_Angeles", instant, Locale.US),
        )
    }

    @Test
    fun unknownWeatherCodesHaveASafeDescription() {
        assertEquals("Conditions unavailable", weatherDescription(-1))
    }

    @Test
    fun hourlyForecastBeginsAtTheNextHourInThePlaceTimezone() {
        val forecast = WeatherForecast(
            timezone = "Asia/Kolkata",
            current = CurrentConditions(
                time = LocalDateTime.parse("2026-01-15T12:00"),
                temperature = 20.0,
                feelsLike = 20.0,
                humidity = 50,
                windKmh = 8.0,
                precipitationMm = 0.0,
                weatherCode = 0,
            ),
            hourly = listOf(
                HourlyConditions(LocalDateTime.parse("2026-01-15T12:00"), 20.0, 0, 0),
                HourlyConditions(LocalDateTime.parse("2026-01-15T13:00"), 21.0, 0, 0),
                HourlyConditions(LocalDateTime.parse("2026-01-15T14:00"), 22.0, 0, 0),
            ),
            daily = emptyList(),
        )

        val upcoming = WeatherApi.localForecastTimes(forecast, Instant.parse("2026-01-15T06:45:00Z"))

        assertEquals(2, upcoming.size)
        assertTrue(upcoming.first().time.isEqual(LocalDateTime.parse("2026-01-15T13:00")))
    }
}
