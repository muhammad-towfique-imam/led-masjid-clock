# Masjid Clock Sender (Android)

The app sends HD2020 programs to Huidu WF cards (WF1, WF2, WF4) over the card WiFi.
It is for sites that have no laptop. Design: `docs/superpowers/specs/2026-10-18-android-wf-sender-design.md`.

## Make a site ZIP

1. In HD2020, export the program of each card with the U-disk option.
2. Make 1 folder for each card. Copy `screen.hds` and `progam.hds` of that card into the folder.
   Use a clear folder name, for example `english` or `hijri`. The app shows this name.
3. Compress the folders into 1 ZIP file. Give the ZIP the name of the site, for example `masjid-north.zip`.
4. Send the ZIP to the person at the site.

The app ignores `set.hds`, `smart.ss`, and `font.hds`. It never sends them.

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

1. Open the site ZIP with the app ("Open with" in WhatsApp), or tap "Open ZIP" in the app.
2. Select the display.
3. Connect the phone to the WiFi of the display in Android settings. The password is 88888888.
4. When the app shows "Display found", tap "Send", and then "Yes, send".
5. If the send fails, tap "Share log" and send the log to the person who sent you the ZIP.

## Manual tests before a release (spec section 7.2)

1. Visible change: send a `TEST` text program from HD2020, then send the clock program from the app.
   The display must change from `TEST` to the clock.
2. Weak signal: send from far away. The app must retry and then show the weak-signal message.
3. Mobile data on: the send must be successful.
4. No card WiFi, and a WiFi that is not a card: the status must not be "Display found".
5. Import from WhatsApp: receive a site ZIP in WhatsApp and open it with the app.

The protocol was confirmed only on a WF1 card with a 128 × 64 screen.
Test WF2 and WF4 cards and other screen sizes before you use the app with them.
