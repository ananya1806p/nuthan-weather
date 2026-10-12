const $ = (id) => document.getElementById(id);
const state = {
  location: loadLocation(),
  unit: localStorage.getItem("nuthan-unit") === "fahrenheit" ? "fahrenheit" : "celsius",
  deferredInstall: null,
  clockInterval: null,
  apiKey: "",
  chatMessages: [],
  chatPage: false,
};

const codes = {
  0: ["Clear sky", "☀️"], 1: ["Mostly clear", "🌤️"], 2: ["Partly cloudy", "⛅"],
  3: ["Overcast", "☁️"], 45: ["Foggy", "🌫️"], 48: ["Rime fog", "🌫️"],
  51: ["Light drizzle", "🌦️"], 53: ["Drizzle", "🌦️"], 55: ["Heavy drizzle", "🌧️"],
  56: ["Freezing drizzle", "🌧️"], 57: ["Heavy freezing drizzle", "🌧️"],
  61: ["Light rain", "🌦️"], 63: ["Rain", "🌧️"], 65: ["Heavy rain", "🌧️"],
  66: ["Freezing rain", "🌧️"], 67: ["Heavy freezing rain", "🌧️"],
  71: ["Light snow", "🌨️"], 73: ["Snow", "❄️"], 75: ["Heavy snow", "❄️"],
  77: ["Snow grains", "🌨️"], 80: ["Light showers", "🌦️"], 81: ["Rain showers", "🌧️"],
  82: ["Heavy showers", "🌧️"], 85: ["Snow showers", "🌨️"], 86: ["Heavy snow showers", "❄️"],
  95: ["Thunderstorm", "⛈️"], 96: ["Storm with hail", "⛈️"], 99: ["Heavy thunderstorm", "⛈️"],
};

function loadLocation() {
  try {
    const saved = JSON.parse(localStorage.getItem("nuthan-location"));
    if (saved && Number.isFinite(saved.latitude) && Number.isFinite(saved.longitude)) return saved;
  } catch { /* Ignore corrupt or unavailable saved preferences. */ }
  return { name: "London", latitude: 51.5072, longitude: -0.1276 };
}

function saveLocation(location) {
  state.location = location;
  try { localStorage.setItem("nuthan-location", JSON.stringify(location)); } catch { /* Storage is optional. */ }
}

function showStatus(message = "", kind = "") {
  const el = $("status");
  el.textContent = message;
  el.className = `status${kind ? ` ${kind}` : ""}`;
}

function weather(code) {
  return codes[Number(code)] || ["Conditions unavailable", "🌡️"];
}

function num(value, digits = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed.toFixed(digits) : "—";
}

function temperature(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return "—°";
  const shown = state.unit === "fahrenheit" ? parsed * 9 / 5 + 32 : parsed;
  return `${Math.round(shown)}°`;
}

function displayTime(value, options) {
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?)?$/
    .exec(String(value));
  if (!match) return "—";
  const [, year, month, day, hour = "0", minute = "0", second = "0"] = match;
  const parsed = new Date(Date.UTC(
    Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), Number(second),
  ));
  if (
    parsed.getUTCFullYear() !== Number(year)
    || parsed.getUTCMonth() !== Number(month) - 1
    || parsed.getUTCDate() !== Number(day)
    || parsed.getUTCHours() !== Number(hour)
    || parsed.getUTCMinutes() !== Number(minute)
    || parsed.getUTCSeconds() !== Number(second)
  ) return "—";
  return new Intl.DateTimeFormat(undefined, { ...options, timeZone: "UTC" }).format(parsed);
}

function localTimeKey(timeZone, date = new Date()) {
  try {
    const parts = Object.fromEntries(
      new Intl.DateTimeFormat("en-CA", {
        timeZone,
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hourCycle: "h23",
      }).formatToParts(date).map(({ type, value }) => [type, value]),
    );
    return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
  } catch {
    return null;
  }
}

function renderPlaceClock(timeZone) {
  if (state.clockInterval) window.clearInterval(state.clockInterval);
  const update = () => {
    try {
      const time = new Intl.DateTimeFormat(undefined, {
        timeZone,
        weekday: "long",
        month: "long",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
      }).format(new Date());
      $("local-time").textContent = `Local time · ${time} (${timeZone})`;
    } catch {
      $("local-time").textContent = "Local time unavailable";
    }
  };
  update();
  state.clockInterval = window.setInterval(update, 60_000);
}

async function requestJson(url) {
  let response;
  try {
    response = await fetch(url, { headers: { Accept: "application/json" } });
  } catch (error) {
    throw new Error("Weather services are unreachable. Check your connection and try again.", { cause: error });
  }
  if (!response.ok) throw new Error(`Weather service returned an error (${response.status}). Try again shortly.`);
  let payload;
  try { payload = await response.json(); } catch (error) {
    throw new Error("Weather service sent an unreadable response. Please try again.", { cause: error });
  }
  if (!payload || typeof payload !== "object" || payload.error) {
    throw new Error(payload?.reason || "Weather service returned an unexpected response.");
  }
  return payload;
}

async function searchPlaces(query) {
  const params = new URLSearchParams({ name: query, count: "8", language: "en", format: "json" });
  const data = await requestJson(`https://geocoding-api.open-meteo.com/v1/search?${params}`);
  return Array.isArray(data.results) ? data.results : [];
}

function placeLabel(place) {
  return [place.name, place.admin1, place.country]
    .filter((value, index, all) => value && all.findIndex((entry) => entry?.toLowerCase() === value.toLowerCase()) === index)
    .join(", ");
}

function renderSearchResults(places) {
  const container = $("search-results");
  container.replaceChildren();
  if (!places.length) {
    showStatus("No matching places found. Check the spelling or try a nearby city.", "error");
    return;
  }
  showStatus("Choose the right place:");
  for (const place of places) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "place-result";
    button.textContent = placeLabel(place);
    button.addEventListener("click", () => {
      saveLocation({ name: placeLabel(place), latitude: Number(place.latitude), longitude: Number(place.longitude) });
      container.replaceChildren();
      loadWeather();
    });
    container.append(button);
  }
}

async function loadWeather() {
  const { latitude, longitude } = state.location;
  $("place-name").textContent = state.location.name;
  showStatus("Gathering your forecast…");
  $("condition").textContent = "Gathering your forecast…";
  const params = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
    temperature_unit: "celsius",
    wind_speed_unit: "kmh",
    precipitation_unit: "mm",
    current: "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m",
    hourly: "temperature_2m,precipitation_probability,weather_code",
    daily: "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
    timezone: "auto",
    forecast_days: "7",
  });
  try {
    const data = await requestJson(`https://api.open-meteo.com/v1/forecast?${params}`);
    if (!data.current || !data.daily || !Array.isArray(data.daily.time) || !data.daily.time.length) {
      throw new Error("Weather service returned an incomplete forecast. Please try again.");
    }
    renderWeather(data);
    showStatus("");
  } catch (error) {
    showStatus(error instanceof Error ? error.message : "Couldn't load weather right now.", "error");
    $("condition").textContent = "Forecast temporarily unavailable";
  }
}

function renderWeather(data) {
  const current = data.current;
  const daily = data.daily;
  const [label, icon] = weather(current.weather_code);
  $("place-name").textContent = state.location.name;
  renderPlaceClock(data.timezone);
  $("temperature").textContent = temperature(current.temperature_2m);
  $("weather-icon").textContent = icon;
  $("condition").textContent = label;
  $("high-low").textContent = `High ${temperature(daily.temperature_2m_max?.[0])}   ·   Low ${temperature(daily.temperature_2m_min?.[0])}`;
  $("feels-like").textContent = temperature(current.apparent_temperature);
  $("humidity").textContent = Number.isFinite(Number(current.relative_humidity_2m)) ? `${num(current.relative_humidity_2m)}%` : "—";
  $("wind").textContent = Number.isFinite(Number(current.wind_speed_10m)) ? `${num(current.wind_speed_10m)} km/h` : "—";
  $("rain-now").textContent = Number.isFinite(Number(current.precipitation)) ? `${num(current.precipitation, 1)} mm` : "—";
  $("unit-note").textContent = state.unit === "fahrenheit" ? "°F" : "°C";

  renderHourly(data.hourly || {}, localTimeKey(data.timezone) || current.time);
  renderDaily(daily);
  const sunrise = daily.sunrise?.[0];
  const sunset = daily.sunset?.[0];
  $("sunrise-sunset").textContent = sunrise && sunset
    ? `☀ Sunrise ${displayTime(sunrise, { hour: "numeric", minute: "2-digit" })}  ·  Sunset ${displayTime(sunset, { hour: "numeric", minute: "2-digit" })}`
    : "☀ Sunrise and sunset unavailable";
}

function renderHourly(hourly, currentTime) {
  const container = $("hourly-list");
  container.replaceChildren();
  if (!Array.isArray(hourly.time) || !hourly.time.length) {
    container.textContent = "Hourly forecast is not available right now.";
    return;
  }
  const start = hourly.time.findIndex((time) => time >= currentTime);
  if (start < 0) {
    container.textContent = "Upcoming hourly forecast is not available right now.";
    return;
  }
  for (const index of Array.from({ length: Math.min(6, hourly.time.length - start) }, (_, offset) => start + offset)) {
    const [label, icon] = weather(hourly.weather_code?.[index]);
    const card = document.createElement("article");
    card.className = "hour-card";
    card.setAttribute("aria-label", `${displayTime(hourly.time[index], { hour: "numeric" })}: ${label}, ${temperature(hourly.temperature_2m?.[index])}`);
    card.innerHTML = `<span class="hour-time">${displayTime(hourly.time[index], { hour: "numeric" })}</span>
      <span class="hour-icon" aria-hidden="true">${icon}</span>
      <strong class="hour-temp">${temperature(hourly.temperature_2m?.[index])}</strong>
      <span class="hour-rain">☂ ${num(hourly.precipitation_probability?.[index])}%</span>`;
    container.append(card);
  }
}

function renderDaily(daily) {
  const container = $("daily-list");
  container.replaceChildren();
  for (let index = 0; index < Math.min(7, daily.time.length); index += 1) {
    const [label, icon] = weather(daily.weather_code?.[index]);
    const dayName = index === 0
      ? "Today"
      : displayTime(daily.time[index], { weekday: "long" });
    const rainValue = daily.precipitation_probability_max?.[index];
    const row = document.createElement("article");
    row.className = "day-row";
    row.innerHTML = `<span class="day-name">${dayName}</span>
      <span class="day-weather"><span class="day-weather-icon" aria-hidden="true">${icon}</span><span>${label}</span></span>
      <span class="day-rain">☂ ${rainValue == null ? "—" : `${num(rainValue)}%`}</span>
      <span class="day-temps"><span>H ${temperature(daily.temperature_2m_max?.[index])}</span><span>L ${temperature(daily.temperature_2m_min?.[index])}</span></span>`;
    container.append(row);
  }
}

async function onSearch(event) {
  event.preventDefault();
  const query = $("city-search").value.trim();
  $("search-results").replaceChildren();
  if (!query) {
    showStatus("Enter a city or place name to search.", "error");
    $("city-search").focus();
    return;
  }
  showStatus("Looking for your place…");
  try {
    const places = await searchPlaces(query);
    renderSearchResults(places);
  } catch (error) {
    showStatus(error instanceof Error ? error.message : "Couldn't search right now.", "error");
  }
}

function useMyLocation() {
  $("search-results").replaceChildren();
  if (!("geolocation" in navigator)) {
    showStatus("Location isn't available in this browser. Search for a city instead.", "error");
    return;
  }
  showStatus("Waiting for your browser to share your location…");
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      if (!Number.isFinite(coords.latitude) || !Number.isFinite(coords.longitude)) {
        showStatus("Your browser returned an invalid location. Search for a city instead.", "error");
        return;
      }
      saveLocation({ name: "Your location", latitude: coords.latitude, longitude: coords.longitude });
      loadWeather();
    },
    (error) => {
      const message = error.code === error.PERMISSION_DENIED
        ? "Location permission was denied. Allow it in browser settings or search for a city."
        : error.code === error.TIMEOUT
          ? "Location request timed out. Try again or search for a city."
          : "Your location couldn't be read. Search for a city instead.";
      showStatus(message, "error");
    },
    { enableHighAccuracy: false, timeout: 12000, maximumAge: 300000 },
  );
}

function setUnit() {
  state.unit = state.unit === "celsius" ? "fahrenheit" : "celsius";
  try { localStorage.setItem("nuthan-unit", state.unit); } catch { /* Storage is optional. */ }
  $("unit-toggle").textContent = state.unit === "celsius" ? "°C" : "°F";
  loadWeather();
}

function configureInstall() {
  const button = $("install-button");
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    state.deferredInstall = event;
    button.hidden = false;
  });
  button.addEventListener("click", async () => {
    if (!state.deferredInstall) return;
    state.deferredInstall.prompt();
    await state.deferredInstall.userChoice;
    state.deferredInstall = null;
    button.hidden = true;
  });
  window.addEventListener("appinstalled", () => {
    button.hidden = true;
    state.deferredInstall = null;
  });
}

function setPage(page) {
  state.chatPage = page === "chat";
  $("weather-view").hidden = state.chatPage;
  $("chat-view").hidden = !state.chatPage;
  $("unit-toggle").hidden = state.chatPage;
  $("weather-tab").classList.toggle("active", !state.chatPage);
  $("chat-tab").classList.toggle("active", state.chatPage);
  $("weather-tab").setAttribute("aria-pressed", String(!state.chatPage));
  $("chat-tab").setAttribute("aria-pressed", String(state.chatPage));
  if (state.chatPage) $("chat-prompt").focus();
}

function appendChatMessage(role, text) {
  const bubble = document.createElement("article");
  bubble.className = `chat-bubble ${role === "user" ? "user-bubble" : "assistant-bubble"}`;
  const name = document.createElement("strong");
  name.textContent = role === "user" ? "You" : "Nuthan";
  const paragraph = document.createElement("p");
  paragraph.textContent = text;
  bubble.append(name, paragraph);
  $("chat-messages").append(bubble);
  $("chat-messages").scrollTop = $("chat-messages").scrollHeight;
}

async function sendChat(event) {
  event.preventDefault();
  const prompt = $("chat-prompt").value.trim();
  if (!prompt) return;
  if (!state.apiKey) {
    $("chat-status").textContent = "Add your Gemini API key above to start chatting.";
    $("api-key").focus();
    return;
  }

  state.chatMessages.push({ role: "user", parts: [{ text: prompt }] });
  appendChatMessage("user", prompt);
  $("chat-prompt").value = "";
  $("send-message").disabled = true;
  $("chat-status").className = "status";
  $("chat-status").textContent = "Nuthan is thinking…";
  try {
    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": state.apiKey,
        },
        body: JSON.stringify({
          systemInstruction: {
            parts: [{ text: "You are Nuthan, a friendly, accurate assistant. Use clear language. If unsure, say so." }],
          },
          contents: state.chatMessages,
        }),
      },
    );
    const result = await response.json();
    if (!response.ok) {
      const detail = result?.error?.message;
      throw new Error(detail || `Gemini returned an error (${response.status}). Check your key and try again.`);
    }
    const answer = result?.candidates?.[0]?.content?.parts
      ?.map((part) => part.text || "")
      .join("")
      .trim();
    if (!answer) throw new Error("Gemini returned an empty response. Please try again.");
    state.chatMessages.push({ role: "model", parts: [{ text: answer }] });
    appendChatMessage("assistant", answer);
    $("chat-status").textContent = "";
  } catch (error) {
    state.chatMessages.pop();
    $("chat-status").className = "status error";
    $("chat-status").textContent = error instanceof TypeError
      ? "Couldn't reach Gemini. Check your connection and try again."
      : (error instanceof Error ? error.message : "Gemini couldn't reply. Please try again.");
  } finally {
    $("send-message").disabled = false;
    $("chat-prompt").focus();
  }
}

$("search-form").addEventListener("submit", onSearch);
$("location-button").addEventListener("click", useMyLocation);
$("unit-toggle").addEventListener("click", setUnit);
$("weather-tab").addEventListener("click", () => setPage("weather"));
$("chat-tab").addEventListener("click", () => setPage("chat"));
$("chat-form").addEventListener("submit", sendChat);
$("key-form").addEventListener("submit", (event) => {
  event.preventDefault();
  const value = $("api-key").value.trim();
  if (!value) {
    $("key-status").textContent = "Paste a Gemini key to use chat.";
    return;
  }
  state.apiKey = value;
  $("api-key").value = "";
  $("key-status").textContent = "Key ready for this page session.";
});
$("forget-key").addEventListener("click", () => {
  state.apiKey = "";
  state.chatMessages = [];
  $("chat-messages").replaceChildren();
  appendChatMessage("assistant", "Hi there! What would you like a hand with?");
  $("key-status").textContent = "Key forgotten.";
  $("chat-status").textContent = "";
});
$("unit-toggle").textContent = state.unit === "celsius" ? "°C" : "°F";
configureInstall();
loadWeather();

if ("serviceWorker" in navigator && (location.protocol === "https:" || location.hostname === "localhost")) {
  navigator.serviceWorker.register("./service-worker.js").catch(() => {
    showStatus("The app works online, but offline app-shell setup failed.", "error");
  });
}
