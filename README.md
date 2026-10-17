# Nuthan Weather

Nuthan is a responsive Streamlit weather dashboard with an optional Gemini chat. The weather view is ready to use without an API key: it uses Open-Meteo for city search, current conditions, and a seven-day forecast.

## Installable app for Android, Windows, and Mac

An installable Progressive Web App (PWA) is included in `pwa\`. It provides the weather dashboard, city search, current-location support, Celsius/Fahrenheit switching, and an optional Gemini chat. Weather uses Open-Meteo without a weather API key.

### Open the hosted app

The PWA is published at **[nuthan-weather](https://ananya1806p.github.io/nuthan-weather/)**. This link works on Android, Windows, and Mac in a supported browser; install it from the browser menu to add it to the home screen or desktop. The public deployment contains only the PWA, and updates to its `pwa\` files on the repository's `main` branch are deployed automatically.

### Preview on this computer

From the project folder, run one of:

```powershell
py -3 -m http.server 8080 --directory pwa
```

```bash
python3 -m http.server 8080 --directory pwa
```

Then open `http://localhost:8080`. Stop the server with `Ctrl+C`. On localhost, supported browsers can test the install prompt and location permission.

### Install on your devices

To install on a phone or another computer, publish the contents of `pwa\` on an HTTPS static web host. A plain local address is only reachable from this computer; geolocation and install support on another device require an HTTPS site. Once it is hosted:

- **Android:** Open the HTTPS address in Chrome and choose **Install app** or **Add to Home screen**.
- **Windows or other desktop:** Open it in a supported browser such as Chrome or Edge and choose **Install app** from the address bar or app menu.
- **Mac:** Open it in Safari and choose **File > Add to Dock**, or use a supported Chromium browser's **Install app** action.

The PWA caches its interface so it can reopen offline, but live forecasts and Gemini chat still need internet access. A Gemini key can be entered in the AI settings; it stays in page memory and is sent to Google for chat requests. Browser install options vary by browser and operating-system version. This produces an installable browser app, not separate signed native Android APK or macOS packages.

### Native Android app and Play Store preparation

A separate native Android app is under `android\`; it is written in Kotlin and Jetpack Compose, not a website inside a WebView. It includes city search, location permission, local-time weather and forecast, temperature units, and the Nuthan AI chat. Read [`android\PLAY_STORE.md`](android/PLAY_STORE.md) for Android Studio build instructions and the remaining Play Console, upload-key, privacy, and testing checklist. Google Play requires the release AAB to be signed with the app owner's upload key; that private key is intentionally not stored in this repository.

AI chat is implemented but requires each user to provide their own Gemini API key in the app session. No shared key is embedded or available by default. A shared chatbot for every user would require a separately hosted, protected and rate-limited AI backend.

The original Streamlit version remains available:

```powershell
streamlit run app.py
```

## Run on Windows

Open the project folder in VS Code, then run these commands in PowerShell:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
streamlit run app.py
```

The app opens in your browser. Stop it with `Ctrl+C`.

### If PowerShell blocks activation

You can allow local virtual-environment scripts for your Windows user, then activate again:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
.\.venv\Scripts\Activate.ps1
```

## Weather features

- Current temperature, feels-like temperature, humidity, wind, and precipitation from the selected place
- A live current clock converted to the selected place's time zone, plus hourly, sunrise, sunset, and daily forecast times in that zone
- Hourly outlook and a seven-day forecast, including rain chances and sunrise/sunset
- Searchable places, popular cities, and optional browser geolocation
- Clear feedback for denied location permission, unavailable locations, and weather-service/network errors
- No embedded weather API key; Open-Meteo may be unavailable without an internet connection

Browser geolocation requires permission and a secure browser context (HTTPS or a local development address). If permission is denied or location is unavailable, use city search instead.

The weather dashboard uses Open-Meteo, so it does not require a weather API key. It requests Celsius, km/h, and mm explicitly; precipitation is the preceding hour's total. Current conditions are forecast estimates, not a live local weather-station reading, and fresh forecast data requires an internet connection.

## Optional Gemini chat

The **Nuthan AI** workspace retains the original Gemini chat. To use it, create a key in [Google AI Studio](https://aistudio.google.com/app/apikey) and keep it private. Paste it into the password field in Chat settings to use it for the current session, or configure it in `.env`:

```text
GEMINI_API_KEY=your_gemini_api_key_here
```

or create `.streamlit\secrets.toml`:

```toml
GEMINI_API_KEY = "your_gemini_api_key_here"
```

Both files are excluded from source control. The weather dashboard does not use this key.

## Troubleshooting

- **Weather data unavailable:** Check your internet connection and try again. Open-Meteo is an external service, and the app cannot show a fresh forecast while offline.
- **No matching city:** Check the spelling or search for a nearby city, region, or country.
- **Location denied:** Allow location access in the browser's site settings, then use the location button again; city search is always available.
- **Gemini chat unavailable:** Confirm that `GEMINI_API_KEY` is configured and that the key and selected model are valid.
- **Command not found:** Make sure `.venv` is activated and that dependency installation completed successfully.
