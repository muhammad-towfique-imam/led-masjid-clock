# Masjid Clock (Android)

The app sends programs to the masjid clock displays (Huidu WF1, WF2, WF4 cards) over the card WiFi.
It is for masjids that have no laptop. It started as HD Sender (github.com/muhammad-towfique-imam/hd-sender).
The app ID is `com.cyanwarelabs.masjidclock.app`. Design: `docs/superpowers/specs/2026-10-18-android-wf-sender-design.md`.

## Make a package (.hdpkg)

A package holds the program of exactly 1 display. It is a ZIP file with the extension `.hdpkg`.

**On the phone (recommended):**

1. In HD2020, export the program of the display with the U-disk option to a USB drive.
2. Connect the USB drive to the phone with a USB OTG adapter.
3. In Masjid Clock, open the menu (≡ at the top left) and tap **Create**. The first time, allow file access: turn on "Allow access to
   manage all files" for Masjid Clock, then go back. Create needs Android 11 or later
   (Send works from Android 7.0).
4. When the app shows "Found on: … display", type a file name in step 4, for example
   `masjid-north-english`. Then tap **Save** (to Downloads) or **Share** (for example to WhatsApp).

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

The release APK is `android/build/outputs/apk/release/masjid-clock-<version>.apk`.
`local.properties` must contain `sdk.dir=` with the path of the Android SDK.

## Release key

Android installs an update over an earlier version only if both use the same key.
The certificate SHA-256 of the Masjid Clock key is
`2fbf779f1b860193471a14f38b7a4455c9a1fa5c4c55e6ed031c1eba019f52ec`.
This key is not the HD Sender key. Masjid Clock and HD Sender are 2 different apps on the phone.

**How the build gets the key:** git contains the key and its passwords only as an encrypted file,
`keystore-backup/masjid-clock-key.enc` (AES-256-GCM, key from the passphrase with PBKDF2).
The release build decrypts it into `build/signing/` with the passphrase from the environment
variable `MASJIDCLOCK_KEY_PASSPHRASE`, or from the file `.env` in this folder:

```
MASJIDCLOCK_KEY_PASSPHRASE=your long passphrase
```

`.env.example` shows the format: copy it to `.env` and set the value.
`.env` is ignored by git. Without a passphrase, the release APK is unsigned (with a warning).
With a wrong passphrase, the build stops. If there is no encrypted file, the build uses
`keystore.properties` and its keystore, if they exist.

**CAUTION: Keep the passphrase in Bitwarden. Without it, the encrypted file is useless. Never commit
`.env`, `keystore/`, or `keystore.properties`.**

**Make the encrypted file (1 time, or after a key change):**

1. Write the passphrase into `.env` (at least 16 characters; use 5 or more random words).
2. Run `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew encryptReleaseKey`.
   It reads `keystore.properties` and its keystore, and writes `keystore-backup/masjid-clock-key.enc`.
3. Commit `keystore-backup/masjid-clock-key.enc`.

**On a new computer:** clone the repository, write `.env` with the passphrase from Bitwarden,
and build the release. Check the certificate with
`apksigner verify --print-certs android/build/outputs/apk/release/masjid-clock-<version>.apk`.

**Make a new key (only if there is no key):**

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/masjid-clock.jks -alias masjidclock -keyalg RSA -keysize 4096 -validity 10000
```

Then write `keystore.properties` (`storeFile=keystore/masjid-clock.jks`, `storePassword=…`, `keyAlias=masjidclock`,
`keyPassword=…`) and make the encrypted file as above. A new key cannot update phones that have the app
with the old key.

## Install on a phone

1. Send the APK file to the phone (for example by WhatsApp).
2. Open the APK. If Android asks, allow "Install unknown apps" for that app.
3. Install the app.

## Use

### The display tabs

A masjid clock is a stack of separate displays, each with its own card and WiFi. The bottom bar has 1 tab for
each display, in the order of the stack: **English**, **Bangla**, **Hijri** (m5 displays, 384 wide). On each tab:
connect to the WiFi of that display, set the time (or skip), and tap **Send**.

- **English:** no input. The app sends the stored English program; the card draws the date and the time.
- **Bangla:** enter the Bangla year (the first time, the app fills in the current year; the year starts on
  14 April). The box shows the date that the display shows today, for example "25 Poush 1432".
- **Hijri:** see "Set the Hijri month" below.

In the step "Connect", the list "Displays nearby" shows the card WiFi networks near the phone (names such as
`WF1_…`), with the card of this tab first and marked ✓. Tap a card or "Open WiFi settings" to choose the WiFi and
type its password. The first time, tap "Show displays nearby" and allow the permission ("Nearby devices" on
Android 13 and later; "Location" on Android 7–12, with the location setting on).

The app remembers the inputs of each card: after a successful send, it saves the inputs under the card name. When
it finds a card that it knows, the inputs change to that card's values ("Values from the last send to this card.").
For an unknown card, the inputs keep the last values.

The app remembers which card got which program. If the connected card last got a different program, the step
"Connect" shows a warning under the card name, for example "This card last got the Hijri program."

The menu (≡ at the top left) has **Send** (a package), **Create** (a package), and **Time**. The back arrow returns
to the last tab. The app opens on the last tab that you used.

### Send a package

Open the menu and tap **Send**. It has 4 steps.

1. **Load file:** open the .hdpkg file with the app ("Open with" in WhatsApp), or tap "Load file".
2. **Connect to the display WiFi:** connect the phone to the WiFi of the display in Android settings.
   The password is 88888888. Wait until the app shows the card name (for example `WF1_3030f98d38da`),
   or "Display found".
3. **Set the time:** tap "Set the time" to set the display clock to the phone time, or "Skip".
4. **Send:** tap "Send". If the send fails, tap "Share log" and send the log
   to the person who sent you the package. ("Share log" shows only after a failed send.) Tap "Send again" to try again.

## Set the display clock

1. Open the menu and tap **Time**.
2. Connect the phone to the WiFi of the display. Wait until the app shows the card name.
3. Check the phone time on the screen, and tap **Set the time**.

The display gets the phone's local time. Keep the phone's automatic time on.

## Set the Hijri month

For m5 displays (384 × 32) only. The phone makes the Hijri program itself: no PC and no package.

1. Open the **Hijri** tab.
2. **Hijri month:** set the month start date (the first day of the Hijri month), select the Hijri month,
   and enter the Hijri year. Under "Lower half", select "Divider line" (the dashed line), "Outside temperature"
   ("Outside Temp: .. °C" from the card's temperature sensor; 0 °C without a sensor), or "Nothing".
   The box under the inputs shows the date that the display will show now, for example
   "9 Juma Ula 1448", and the time when the date changes each day (Maghrib in Dhaka). The app remembers these values.
3. **Connect to the display WiFi** and wait for the card name.
4. **Set the time** or tap **Skip**.
5. Tap **Send**.

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
   An export larger than 20 MB shows "The HD2020 export is larger than 20 MB…".
10. Save twice with the same name: Downloads has `name.hdpkg` and `name (1).hdpkg`.
11. Share to WhatsApp, open the file with "Open with" → Masjid Clock, and send it to the display.
12. Load a `.zip` file in Send: the app shows "This file is not a display package".
13. Time: the display clock changes to the phone time. Test it once with the phone time set by hand
    to a wrong value (for example 12:34), then with automatic time on again.
14. Time without a display WiFi: the button stays disabled. With a WiFi that is not a display:
    "No display found on this WiFi".

15. Hijri: send the current month with the real year. The display must show the same text and the same day
    number as the program from the desktop app.
16. Hijri: after a send, switch to another app and back. The display must not flicker.
17. Hijri: during a Hijri send, open an .hdpkg file with "Open with". The send must continue, and the Send
    page must not start a second send.

18. English and Bangla: send each tab to its card and compare the display with the desktop app's program.
19. Bangla: the summary shows the correct date; on 13 and 14 April, the first-use year is correct.
20. Connect the Hijri card on the Bangla tab: the warning "This card last got the Hijri program." shows.
21. The menu: Send, Create, and Time open; the back arrow and the Android back button return to the last tab.
22. Close and open the app: it opens on the last tab.
23. Open the menu (≡) and press the Android back button: the menu closes, and the app stays open.
24. Send Hijri to card A, then change the inputs and send to card B. Connect card A again: the inputs change to
    card A's values and the note shows. Connect an unknown card: the inputs keep the last values.
25. Hijri with "Outside temperature": the lower half shows "Outside Temp" and the value in all 3 sections.
26. Displays nearby: on each tab, the list shows the card WiFis near the phone, with the card of that tab first and
    marked ✓. Test the permission question once on Android 13+ and once on Android 7–12 (location on and off).

If Create does not find a USB drive, tap the ⓘ icon at the top of the Create tab ("Share details")
and send the text to support.

**Cards with firmware V4.45** (for example the `bangla` and `hijri` cards): these cards refuse the
normal send at the start packet. The app then sends again by itself with HD2020's safe form over UDP.
Confirmed on the `hijri` card (WF1, V4.45) on 2026-10-19.

Confirmed so far: program send on a WF1 card with older firmware (128 × 64) and on a WF1 card with
firmware V4.45 (384 × 32); time sync on a WF2 card. Test WF2 and WF4 program sends before you use the
app with them.
