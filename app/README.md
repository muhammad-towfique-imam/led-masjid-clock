# HD Sender (Android)

The app sends HD2020 programs to Huidu WF cards (WF1, WF2, WF4) over the card WiFi.
It is for sites that have no laptop. Design: `docs/superpowers/specs/2026-10-18-android-wf-sender-design.md`.

## Make a package (.hdpkg)

A package holds the program of exactly 1 display. It is a ZIP file with the extension `.hdpkg`.

**On the phone (recommended):**

1. In HD2020, export the program of the display with the U-disk option to a USB drive.
2. Connect the USB drive to the phone with a USB OTG adapter.
3. In HD Sender, tap **Create**. The first time, allow file access (on Android 11 and later,
   turn on "Allow access to manage all files" for HD Sender, then go back).
4. When the app shows "Found on: … display", tap **Save** (to Downloads) or **Share** (for example
   to WhatsApp), and type a file name, for example `masjid-north-english`.

**On a PC:** put `screen.hds` and `progam.hds` from the U-disk export into a ZIP file, at its root,
and rename the ZIP to `<name>.hdpkg`. Use only English letters, numbers, spaces, `-` and `_` in the name.

The app ignores `set.hds`, `smart.ss`, and `font.hds`. It never sends them.
The Send flow accepts only `.hdpkg` files.

## Build

Use Java 21. Run the commands in this folder.

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :protocol:test :android:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :android:assembleRelease
```

The release APK is `android/build/outputs/apk/release/android-release.apk`.
`local.properties` must contain `sdk.dir=` with the path of the Android SDK.

## Release key

Android installs an update over an earlier version only if both use the same key.

**CAUTION: Keep the key file and its passwords in a safe place, with a backup copy outside this computer.
If you lose the key, every phone must uninstall the app before it can install a new version.
Never commit the key or `keystore.properties` to git.**

Make the key 1 time:

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/release.jks -alias wfsender -keyalg RSA -keysize 4096 -validity 10000
```

Then make `keystore.properties` in this folder:

```properties
storeFile=keystore/release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=wfsender
keyPassword=YOUR_KEY_PASSWORD
```

## Install on a phone

1. Send the APK file to the phone (for example by WhatsApp).
2. Open the APK. If Android asks, allow "Install unknown apps" for that app.
3. Install the app.

## Use

On the home screen, tap **Send**. The Send page has 3 steps.

1. **Load file:** open the .hdpkg file with the app ("Open with" in WhatsApp), or tap "Load file".
2. **Connect to the display WiFi:** connect the phone to the WiFi of the display in Android settings.
   The password is 88888888. Wait until the status shows "Display found".
3. **Send:** tap "Send", and then "Yes, send". If the send fails, tap "Share log" and send the log
   to the person who sent you the package. Tap "Send again" to try again.

## Manual tests before a release (spec section 7.2)

1. Visible change: send a `TEST` text program from HD2020, then send the clock program from the app.
   The display must change from `TEST` to the clock.
2. Weak signal: send from far away. The app must retry and then show the weak-signal message.
3. Mobile data on: the send must be successful.
4. No card WiFi, and a WiFi that is not a card: the status must not be "Display found".
5. Import from WhatsApp: receive an .hdpkg file in WhatsApp and open it with the app.
6. After a send, the display must not flicker while the phone stays on the display WiFi.
7. Create: the first Create asks for file access; the second does not.
8. Create without a USB drive shows the message; when the drive is connected, the app finds it.
9. Create with a USB drive without an export shows "The USB drive has no HD2020 export".
10. Save twice with the same name: Downloads has `name.hdpkg` and `name (1).hdpkg`.
11. Share to WhatsApp, open the file with "Open with" → HD Sender, and send it to the display.
12. Load a `.zip` file in Send: the app shows "This file is not an HD Sender package".

The protocol was confirmed only on a WF1 card with a 128 × 64 screen.
Test WF2 and WF4 cards and other screen sizes before you use the app with them.
