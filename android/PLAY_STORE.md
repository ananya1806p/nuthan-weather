# Native Android app and Play Store checklist

`android\` is a native Kotlin/Jetpack Compose app. It calls Open-Meteo directly for forecasts and geocoding and uses Android's location permission only after the user taps **Use my location**. It is not a WebView wrapper and does not require the PWA or a server to display weather.

The Android app includes the weather dashboard, city search, seven-day outlook, place-local clock, and a native AI chat screen. Gemini chat uses the user's own Gemini API key in memory for the current app session; it is not embedded or saved. Obtain a key from [Google AI Studio](https://aistudio.google.com/app/apikey) and keep it private. Messages and the supplied key are sent directly to Google when the user chats. A shared, preconfigured chatbot would require a separately hosted, rate-limited backend and a privately configured key; a public Android app must not ship a shared Gemini key inside its source or APK.

## Build and test

Open the `android\` folder in current Android Studio with JDK 17 and Android SDK 37 installed, or run:

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug bundleRelease
```

The `Build native Android app` GitHub Actions workflow runs the tests and builds a debug APK for CI validation plus a release Android App Bundle (AAB) artifact. It does not publish the debug APK for installation: debug builds are for testing, and installing APKs from outside Google Play can trigger Android security warnings. The release AAB is **unsigned** until an app-owner upload key is configured; it cannot be uploaded to Play Console as-is. Distribute the native app through Google Play after signing, account setup, and review.

## Before uploading to Google Play

1. Create and verify the developer account in Google Play Console. Complete the identity, contact, payments, and app-ownership steps shown for that account.
2. Choose the final package name before creating the Play listing; it cannot be renamed later. The current application ID is `com.ananya.nuthanweather`.
3. Create and back up an **upload keystore** safely. Do not commit it to Git. For automated signed release builds, configure the repository actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The Android build workflow uses those secrets to sign the AAB; without all four, it creates only an unsigned release bundle. Never put key values in this checklist or app source.
4. Confirm the final public publisher identity, support contact, icon, screenshots, store description, and content-rating answers.
5. Publish the privacy-policy page and link it in Play Console. Accurately complete Data safety and app-access declarations, including that optional location is sent to Open-Meteo and, if used, chat prompts and the user's Gemini key are sent to Google. Obtain a privacy/legal review before public release.
6. Upload the signed AAB to an internal test track and verify install, location permission/denial, city search, network/offline handling, unit switching, and AI chat with a valid user key.
7. Follow the testing track and production-access requirements Play Console displays for the developer account. Some newer personal accounts must complete a closed test before requesting production access.
8. Submit the completed listing for Google review. Google decides approval; building an AAB does not guarantee publication.

### Draft store listing

- **App name:** Nuthan Weather
- **Short description:** Check the weather, find a forecast, and chat with Nuthan AI.
- **Full description:** Nuthan Weather brings current weather estimates, a seven-day outlook, city search, and optional device location together in a gentle, easy-to-read app. Choose Celsius or Fahrenheit and see the selected location's local time, sunrise, sunset, wind, humidity, and precipitation. An optional Gemini-powered chat can help answer questions; it requires your own Gemini API key, which stays in the current app session and is sent to Google when you chat. Weather data is provided by Open-Meteo and needs an internet connection. Location is optional; city search always works without granting location permission.
- **Privacy policy:** `https://ananya1806p.github.io/nuthan-weather/privacy.html`
- **Icon:** `pwa\icons\icon-512.png` (confirm Play's current icon requirements)
- **Screenshots:** Capture the native Android app on a phone and tablet after testing; don't use PWA or browser screenshots for the native listing.

The build targets Android 17 (API 37) and uses version code 2. Recheck Google Play's current target-API deadline before each release.

## App and data behavior

- Weather and location search use the public Open-Meteo API. A working internet connection is needed for fresh forecasts.
- Location permission is optional; users can always select a city instead. The app requests location only in response to a user action and sends coordinates to Open-Meteo to retrieve weather.
- Gemini is optional and key-gated per user. The app keeps the entered key only in live app memory and sends it to Google's Gemini API in the authorization header when chatting. Restarting the app clears it.
- No analytics, ads, account sign-in, or background location are included.
- `pwa\` remains the cross-platform web app for browsers, iOS, Windows, macOS, and Android installation. The `android\` project is the separate native Android app.
