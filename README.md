# TV Home: a Google TV-style launcher for Android TV

A custom home screen for Android TV 14 (Kotlin + Jetpack Compose). It gives you the Google TV look: top tabs, a big featured banner, rows of app cards, favorites, a clock, and quick settings. It is fully D-pad / remote friendly.

**What it does not have:** Google's own recommendations, Watchlist and live free channels. Those come from Google services and can't be reproduced by a third-party app.

## Features
- Tabs: **For you**, **Apps**, **Settings** (focusing a tab switches to it, like Google TV)
- Rotating **Featured** banner built from your installed streaming apps (YouTube, Netflix, Prime Video, Disney+, etc.)
- **Favorites** row: press and hold OK on any app to add or remove it
- **Apps** tab with every installed app
- **Settings** tab with shortcuts to Wi-Fi, Display, Sound, Bluetooth, Apps, Date & time, Storage
- Clock, focus highlight and zoom animation, Back goes to "For you"

## Build the APK without Android Studio (GitHub, free)
1. Create a free account at github.com and make a **new repository**.
2. Upload every file from this folder (**Add file -> Upload files**). Make sure `.github/workflows/build.yml` is included. If it isn't, use **Add file -> Create new file**, name it `.github/workflows/build.yml` and paste the contents.
3. Open the **Actions** tab -> **Build APK** -> **Run workflow**. Wait about 3-5 minutes.
4. Open the finished run, download the **TvHome-apk** artifact, unzip it to get `app-debug.apk`.
5. Install the APK on the TV (see below). Skip the Android Studio steps.

## Build the APK with Android Studio (alternative)
1. Install **Android Studio** (current stable) on a computer.
2. **File -> Open** and choose this `TvHome` folder. Let Gradle sync finish (needs internet the first time).
3. **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
4. The file is at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the TV
1. On the TV: **Settings -> System -> About**, tap **Android TV OS build** 7 times to enable Developer options.
2. **Settings -> System -> Developer options -> USB debugging** (or **Network debugging** / **Wireless debugging**) -> on.
3. On the computer, with the TV and computer on the same Wi-Fi (use the TV's IP from Settings -> Network):
   ```
   adb connect TV_IP_ADDRESS:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Accept the "Allow debugging" prompt on the TV the first time.

## Make it your home screen
Open **TV Home** from your apps list first to check it works. Then either:
- Press the **Home** button. Some TVs ask which home app to use. Choose **TV Home** and "Always".
- Or set it with ADB:
  ```
  adb shell cmd package set-home-activity com.myapp.tvhome/.MainActivity
  ```

## Go back to the original home screen
- Uninstall it: `adb uninstall com.myapp.tvhome` (or Settings -> Apps -> TV Home -> Uninstall), or
- Settings -> Apps -> TV Home -> **Clear defaults**, then press Home and choose the original launcher.

Do not disable or remove the stock launcher. Leaving it installed is your safety net.

## Notes
- Some TVs (a few Xiaomi, TCL and Hisense models) ignore third-party home apps or reopen the stock launcher. If that happens, the app still works when opened from the apps list.
- Apps only appear if they declare a TV or normal launcher entry. Phone-only apps you sideloaded show up too.
- Minimum Android version: 8.0 (API 26). Designed for Android TV 14. The code was written without a compiler or a TV on hand, so if Gradle reports an error on first build, send it over and it can be fixed quickly.
