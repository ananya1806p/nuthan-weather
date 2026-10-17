package com.ananya.nuthanweather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Rose = Color(0xFFB45A84)
private val RoseDark = Color(0xFF744461)
private val Lilac = Color(0xFF8E78A6)
private val Ink = Color(0xFF49384E)
private val Muted = Color(0xFF89788C)
private val Canvas = Color(0xFFFFF8FB)

class MainActivity : ComponentActivity() {
    private var pendingLocationResult: ((Result<Coordinates>) -> Unit)? = null
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentLocation()
        } else {
            completeLocation(Result.failure(SecurityException("Location permission was denied. Search for a city instead.")))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Rose,
                    onPrimary = Color.White,
                    secondary = Lilac,
                    background = Canvas,
                    surface = Color.White,
                    onSurface = Ink,
                ),
            ) {
                NativeWeatherApp(onUseLocation = ::requestLocation)
            }
        }
    }

    private fun requestLocation(onResult: (Result<Coordinates>) -> Unit) {
        pendingLocationResult = onResult
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            fetchCurrentLocation()
        } else {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    private fun fetchCurrentLocation() {
        val cancellation = CancellationTokenSource()
        locationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellation.token,
        ).addOnSuccessListener { location ->
            if (location == null) {
                completeLocation(
                    Result.failure(IllegalStateException("Current location is unavailable. Search for a city instead.")),
                )
            } else {
                completeLocation(Result.success(Coordinates(location.latitude, location.longitude)))
            }
        }.addOnFailureListener { error ->
            completeLocation(
                Result.failure(
                    IllegalStateException(
                        error.message ?: "Current location could not be read. Search for a city instead.",
                        error,
                    ),
                ),
            )
        }
    }

    private fun completeLocation(result: Result<Coordinates>) {
        pendingLocationResult?.invoke(result)
        pendingLocationResult = null
    }
}

@Composable
private fun NativeWeatherApp(
    onUseLocation: ((Result<Coordinates>) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var latitude by rememberSaveable { mutableStateOf(51.5072) }
    var longitude by rememberSaveable { mutableStateOf(-0.1276) }
    var placeName by rememberSaveable { mutableStateOf("London") }
    var forecast by remember { mutableStateOf<WeatherForecast?>(null) }
    var loading by remember { mutableStateOf(true) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var fahrenheit by rememberSaveable { mutableStateOf(false) }
    var chatPage by rememberSaveable { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var clockInstant by remember { mutableStateOf(Instant.now()) }
    val chatSession = remember { NativeChatState() }

    LaunchedEffect(latitude, longitude, refreshKey) {
        loading = true
        weatherError = null
        try {
            forecast = WeatherApi.forecast(Coordinates(latitude, longitude))
        } catch (error: Exception) {
            weatherError = error.message ?: "Couldn't load weather. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            clockInstant = Instant.now()
        }
    }

    fun search() {
        if (query.isBlank()) {
            searchError = "Enter a city or place name to search."
            places = emptyList()
            return
        }
        scope.launch {
            searchLoading = true
            searchError = null
            places = emptyList()
            try {
                places = WeatherApi.searchPlaces(query)
                if (places.isEmpty()) searchError = "No matching places found. Try another spelling or nearby city."
            } catch (error: Exception) {
                searchError = error.message ?: "Couldn't search for places. Check your connection and try again."
            } finally {
                searchLoading = false
            }
        }
    }

    Scaffold(
        containerColor = Canvas,
        contentWindowInsets = WindowInsets.systemBars,
    ) { insets ->
        Box(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF1D7E4),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = Rose,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Nuthan", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
                        Text("Weather, with a little warmth", color = Muted, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { chatPage = !chatPage }) {
                    Text(if (chatPage) "Weather" else "Nuthan AI")
                }
            }

            if (chatPage) {
                NativeChatScreen(chatSession)
            } else {
                Text(
                    "A softer forecast\nfor your day.",
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RoseDark,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; searchError = null },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Find a city or place") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                    )
                    Button(
                        onClick = ::search,
                        enabled = !searchLoading,
                        modifier = Modifier.height(54.dp),
                    ) {
                        if (searchLoading) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Search")
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        weatherError = "Getting your location…"
                        onUseLocation { result ->
                            result.onSuccess { coordinates ->
                                latitude = coordinates.latitude
                                longitude = coordinates.longitude
                                placeName = "Your location"
                                places = emptyList()
                                weatherError = null
                            }.onFailure { error ->
                                weatherError = error.message ?: "Location could not be read. Search for a city instead."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use my location")
                }

                searchError?.let { InlineNotice(it, isError = true) }
                places.forEach { place ->
                    OutlinedButton(
                        onClick = {
                            latitude = place.latitude
                            longitude = place.longitude
                            placeName = place.label
                            places = emptyList()
                            query = ""
                            searchError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(place.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                val data = forecast
                if (data != null) {
                    CurrentWeatherCard(
                        placeName = placeName,
                        forecast = data,
                        instant = clockInstant,
                        fahrenheit = fahrenheit,
                        onToggleUnit = { fahrenheit = !fahrenheit },
                        onRefresh = { refreshKey += 1 },
                    )
                    if (weatherError != null) InlineNotice(weatherError.orEmpty(), isError = true)
                    WeatherDetails(data.current, fahrenheit)
                    HourlyForecast(data, clockInstant, fahrenheit)
                    WeeklyForecast(data, fahrenheit)
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (loading) CircularProgressIndicator(color = Rose)
                            Text(
                                weatherError ?: "Gathering your forecast…",
                                color = if (weatherError == null) Muted else RoseDark,
                            )
                            if (!loading) {
                                Button(onClick = { refreshKey += 1 }) { Text("Try again") }
                            }
                        }
                    }
                }
                Text(
                    "Forecast estimates from Open-Meteo · Celsius, km/h, and mm",
                    color = Muted,
                    fontSize = 11.sp,
                )
                PrivacyLink()
            }
        }
        }
    }
}

@Composable
private fun CurrentWeatherCard(
    placeName: String,
    forecast: WeatherForecast,
    instant: Instant,
    fahrenheit: Boolean,
    onToggleUnit: () -> Unit,
    onRefresh: () -> Unit,
) {
    val today = forecast.daily.firstOrNull()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = RoseDark,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(Color(0xFFAA5C84), Color(0xFF76628E))),
                )
                .padding(22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("RIGHT NOW", color = Color(0xFFFFDCEB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(placeName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${WeatherApi.formatLocalClock(forecast.timezone, instant)} · ${forecast.timezone}",
                        color = Color(0xFFFFEAF2),
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh forecast", tint = Color.White)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    temperatureText(forecast.current.temperature, fahrenheit),
                    color = Color.White,
                    fontSize = 66.sp,
                    lineHeight = 74.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(weatherEmoji(forecast.current.weatherCode), fontSize = 38.sp)
                    Text(weatherDescription(forecast.current.weatherCode), color = Color.White, fontWeight = FontWeight.Medium)
                    Text(
                        "H ${temperatureText(today?.high, fahrenheit)} · L ${temperatureText(today?.low, fahrenheit)}",
                        color = Color(0xFFFFEAF2),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onToggleUnit,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Text(if (fahrenheit) "Show °C" else "Show °F")
            }
        }
    }
}

@Composable
private fun WeatherDetails(current: CurrentConditions, fahrenheit: Boolean) {
    SectionTitle("The feel of today", "A few little details")
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        MetricCard("Feels like", temperatureText(current.feelsLike, fahrenheit), Modifier.weight(1f))
        MetricCard("Humidity", current.humidity?.let { "$it%" } ?: "—", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        MetricCard("Wind", current.windKmh?.let { "${it.roundToInt()} km/h" } ?: "—", Modifier.weight(1f))
        MetricCard("Precipitation · past hour", current.precipitationMm?.let { "%.1f mm".format(Locale.getDefault(), it) } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun HourlyForecast(forecast: WeatherForecast, instant: Instant, fahrenheit: Boolean) {
    SectionTitle("The next few hours", "Coming up")
    val hours = WeatherApi.localForecastTimes(forecast, instant)
    if (hours.isEmpty()) {
        InlineNotice("Hourly forecast is temporarily unavailable.", isError = false)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        hours.take(4).forEach { hour ->
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(hour.time.format(DateTimeFormatter.ofPattern("h a", Locale.getDefault())), color = Muted, fontSize = 11.sp)
                    Text(weatherEmoji(hour.weatherCode), fontSize = 21.sp)
                    Text(temperatureText(hour.temperature, fahrenheit), fontWeight = FontWeight.Bold, color = Ink)
                    Text(hour.precipitationChance?.let { "☂ $it%" } ?: "—", color = Lilac, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun WeeklyForecast(forecast: WeatherForecast, fahrenheit: Boolean) {
    SectionTitle("Seven-day outlook", "Ahead this week")
    forecast.daily.forEachIndexed { index, day ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (index == 0) "Today" else day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())),
                    modifier = Modifier.width(55.dp),
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                Text(weatherEmoji(day.weatherCode), fontSize = 20.sp)
                Text(
                    weatherDescription(day.weatherCode),
                    modifier = Modifier.weight(1f),
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(day.precipitationChance?.let { "$it%" } ?: "—", color = Lilac, fontSize = 12.sp)
                Text(
                    "${temperatureText(day.high, fahrenheit)} / ${temperatureText(day.low, fahrenheit)}",
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    fontSize = 12.sp,
                )
            }
        }
    }
    forecast.daily.firstOrNull()?.let { today ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(today.sunrise?.let { "Sunrise · ${it.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}" } ?: "Sunrise unavailable", color = Muted, fontSize = 12.sp)
            Text(today.sunset?.let { "Sunset · ${it.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))}" } ?: "Sunset unavailable", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NativeChatScreen(session: NativeChatState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text("Nuthan AI", color = RoseDark, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text("Ask Nuthan to explain, brainstorm, or help you write.", color = Muted)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("Your Gemini API key", color = Ink, fontWeight = FontWeight.SemiBold)
                Text(
                    "Paste your own key to enable chat. It stays in app memory and is sent to Google with your messages; it isn't saved on this device.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                TextButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://aistudio.google.com/app/apikey"),
                            ),
                        )
                    },
                ) {
                    Text("Get a Gemini API key")
                }
                OutlinedTextField(
                    value = session.keyInput,
                    onValueChange = { session.keyInput = it; session.status = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Gemini API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (session.apiKey.isBlank()) "Chat is off until you add a key." else "Key ready for this session.",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                    Row {
                        TextButton(onClick = {
                            session.apiKey = ""
                            session.keyInput = ""
                            session.turns.clear()
                            session.turns.add(ChatTurn("model", "Hi there! What would you like a hand with?"))
                            session.status = "Key forgotten."
                        }) { Text("Forget") }
                        Button(
                            onClick = {
                                if (session.keyInput.isBlank()) {
                                    session.status = "Paste a Gemini API key to use chat."
                                } else {
                                    session.apiKey = session.keyInput.trim()
                                    session.keyInput = ""
                                    session.status = "Key ready for this app session."
                                }
                            },
                        ) { Text("Use key") }
                    }
                }
            }
        }
        session.status?.let { InlineNotice(it, isError = session.apiKey.isBlank() && it.contains("key", ignoreCase = true)) }
        session.turns.forEach { turn ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (turn.role == "user") Color(0xFFFFEDF4) else Color.White,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (turn.role == "user") "You" else "Nuthan", color = Rose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(turn.text, color = Ink)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = session.message,
                onValueChange = { session.message = it },
                modifier = Modifier.weight(1f),
                label = { Text("Write a message") },
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (session.apiKey.isNotBlank() && session.message.isNotBlank() && !session.sending) {
                            submitChat(session, scope)
                        } else if (session.apiKey.isBlank()) {
                            session.status = "Add your Gemini API key above to start chatting."
                        }
                    },
                ),
            )
            IconButton(
                onClick = {
                    if (session.apiKey.isBlank()) {
                        session.status = "Add your Gemini API key above to start chatting."
                    } else if (session.message.isNotBlank() && !session.sending) {
                        submitChat(session, scope)
                    }
                },
                enabled = !session.sending,
            ) {
                if (session.sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Send, contentDescription = "Send message", tint = Rose)
            }
        }
        Text(
            "AI can make mistakes. Don't share sensitive information.",
            color = Muted,
            fontSize = 11.sp,
        )
        PrivacyLink()
    }
}

private class NativeChatState {
    var keyInput by mutableStateOf("")
    var apiKey by mutableStateOf("")
    var message by mutableStateOf("")
    var sending by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    val turns = mutableStateListOf(ChatTurn("model", "Hi there! What would you like a hand with?"))
}

private fun submitChat(
    session: NativeChatState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val userTurn = ChatTurn("user", session.message.trim())
    session.turns.add(userTurn)
    session.message = ""
    session.sending = true
    session.status = "Nuthan is thinking…"
    scope.launch {
        try {
            val answer = GeminiApi.reply(session.apiKey, session.turns.toList())
            session.turns.add(ChatTurn("model", answer))
            session.status = null
        } catch (error: Exception) {
            session.turns.remove(userTurn)
            session.status = error.message ?: "Gemini couldn't reply. Check your connection and API key."
        } finally {
            session.sending = false
        }
    }
}

@Composable
private fun SectionTitle(title: String, eyebrow: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(eyebrow.uppercase(Locale.getDefault()), color = Lilac, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(title, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(value, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InlineNotice(message: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) Color(0xFFFFEEF0) else Color(0xFFF4EFF8),
    ) {
        Text(
            message,
            modifier = Modifier.padding(13.dp),
            color = if (isError) Color(0xFF9A3450) else Muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PrivacyLink() {
    val context = LocalContext.current
    TextButton(
        onClick = {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://ananya1806p.github.io/nuthan-weather/privacy.html"),
            )
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Privacy policy", color = Rose, fontSize = 12.sp)
    }
}

private fun temperatureText(celsius: Double?, fahrenheit: Boolean): String {
    if (celsius == null || !celsius.isFinite()) return "—"
    val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
    return "${value.roundToInt()}°${if (fahrenheit) "F" else "C"}"
}

private fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1 -> "🌤️"
    2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
