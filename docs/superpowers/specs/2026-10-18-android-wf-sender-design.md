# Android WF Sender: Design

- **Date:** 2026-10-18
- **Branch:** `android-wf-sender`
- **Code location:** `app/`
- **Status:** Approved in conversation. Waiting for review of this written spec.

## 1. Purpose

Some masjid clock displays are at remote sites where nobody has a laptop.
Today, a person must use HD2020 on a Windows PC to send a program to a display.

The Android app lets a person at the site send a program to a display with a phone.
You make the program in HD2020 and export it with the U-disk option.
You send the export to the person as a ZIP file. The person opens the ZIP in the app
and sends each program to its card over the card WiFi.

The app only relays files. It does not make or change programs.

### 1.1 Success criteria

1. A person at the site receives a ZIP file, opens it in the app, and updates a card
   without help.
2. The card shows the new program after the send.
3. If the send fails, the app tells the person what to do, and the person can send
   a log to you.

### 1.2 Users

The people at the sites are not technical. The app must use few steps and clear messages.
All screens are in English.

## 2. Decisions

| Topic | Decision |
|---|---|
| Delivery of programs | 1 ZIP file for each site, with 1 folder for each card |
| Card selection | The person connects to the card WiFi in Android settings and selects the folder in the app |
| Card check | Confirmation only: the app shows the folder name and the screen size, and the person confirms |
| Distribution | A signed APK file, sent directly to the person (no Google Play) |
| Language | English only |
| WiFi mode | Each card uses its own hotspot: `192.168.4.1`, TCP port `6101` |
| Technology | Native Kotlin, Jetpack Compose |
| Minimum Android version | 7.0 (API level 24) |

## 3. Background: the protocol

The protocol was confirmed in a spike on 2026-10-18 with a WF1 card (128 × 64).
The source is the reverse-engineered Go library
[alparslanahmed/huidu-led](https://github.com/alparslanahmed/huidu-led) (`hd2020_gen6.go`).
A Wireshark capture showed that HD2020 and the spike script `send_hds.py` send identical
bytes, except for the transfer ID and the checksum. The card accepted the program from
the script.

### 3.1 The U-disk export

HD2020 writes these files to the USB drive:

| File | Content | Used by the app |
|---|---|---|
| `screen.hds` | 66-byte `HS` block: the screen settings. Width at bytes 4–5, height at bytes 6–7 (big-endian). | Yes |
| `progam.hds` | `HC` header + `HP` program block + `HA` area blocks with bitmaps | Yes |
| `font.hds` | Glyph bitmaps | No (HD2020 does not send it separately) |
| `set.hds` | Card settings | **No. Never send.** |
| `smart.ss` | Panel hardware settings | **No. Never send.** |

The name `progam.hds` (not `program.hds`) is the name that HD2020 uses.

### 3.2 Frame format

All multi-byte values are big-endian, except for the transfer ID.

**Request frame (`HT`), sent by the app:**

| Bytes | Value |
|---|---|
| 0–1 | `H` `T` |
| 2 | `0x00` |
| 3 | `0x1B` |
| 4–5 | Total frame length |
| 6 | Command |
| 7–25 | `0x00` |
| 26 | Sequence number (starts at 1, low 8 bits) |
| 27 … n−4 | Payload |
| n−3, n−2 | Sum of all bytes before the checksum, low 16 bits |
| n−1 | `0xAA` |

**ACK frame (`HR`), sent by the card:** 31 bytes. Bytes 0–1 are `H` `R`.
Byte 20 is the command that the card answers. Byte 21 is the status (`0x00` = success).

### 3.3 Program transfer

`chunks` = `progam.hds` split into 1,000-byte parts. The last part can be shorter.
`tid` = a random 16-bit transfer ID, little-endian.

Before the transfer, the app writes the number of chunks into bytes 9–10 of `progam.hds`
(big-endian). HD2020 does the same. The file on disk has 0 there.

| Order | Command | Sequence | Payload |
|---|---|---|---|
| 1 | `0x13` | 1 | The full `screen.hds` |
| 2 | `0x02` | 2 | `0x05`, `tid`, number of chunks (2 bytes) |
| 3 … | `0x03` | 3 + i | `0x05`, `tid`, chunk index i (2 bytes), chunk i |
| last | `0x04` | 3 + number of chunks | `0x03`, `tid` |

The app sends 1 frame, then waits for the ACK with the same command, then sends the next frame.
If an ACK for a different command has status 0, the app ignores it and continues to wait.

### 3.4 Probe

The probe is the SDK version packet: `08 00 01 20 05 00 00 01` (8 bytes).
A WF card answers with an `HR` frame. The probe does not change the card.
Bytes 2–17 of the reply contain a device ID. The app does not use the device ID.

## 4. Structure

`app/` is 1 Gradle project with 2 modules.

```
app/
├── protocol/   pure Kotlin library, no Android code, JVM unit tests
└── android/    the Android application (Jetpack Compose)
```

### 4.1 Module `protocol`

| Unit | Purpose |
|---|---|
| `HdsExport` | Reads 1 card folder. Checks that `screen.hds` starts with `HS` and is 66 bytes, and that `progam.hds` starts with `HC`. Gives the screen width and height. |
| `HtFrame` | Builds `HT` frames. Parses `HR` ACK frames. |
| `ProgramTransfer` | Makes the ordered frame list from an `HdsExport` and a transfer ID (section 3.3). |
| `CardSender` | Sends the frames through a socket that it receives from the caller. Waits for each ACK, retries, and reports progress (section 6). Also runs the probe. |

`CardSender` does not open network connections itself. It receives a socket factory.
Thus the tests can use a local fake card, and the Android module can give a socket that is
bound to the card WiFi.

### 4.2 Module `android`

| Unit | Purpose |
|---|---|
| `ZipImporter` | Receives the site ZIP and unpacks it into the private storage of the app (section 5.1). |
| `CardNetwork` | Finds the connected WiFi network and gives a socket factory that is bound to it (section 5.3). |
| `SiteScreen` | Shows the list of cards in the imported ZIP. |
| `SendScreen` | Shows the connection status, the confirmation, the progress, and the result. |
| `SendLog` | Records the steps of 1 send and shares them as text. |

## 5. Behavior

### 5.1 ZIP import

1. The person opens the ZIP with "Open with" (for example from WhatsApp) or with the
   "Open ZIP" button (Android file picker).
2. The app unpacks the ZIP into its private storage. A new import replaces the earlier site.
3. Each folder that contains both `screen.hds` and `progam.hds` is 1 card.
   The folder name is the card label. If these files are at the root of the ZIP,
   the ZIP file name is the label.
4. The app does not unpack or use `set.hds`, `smart.ss`, `font.hds`, or other files.

The app rejects the ZIP and shows a message in these conditions:

- An entry path is absolute, or contains `..`.
- The ZIP contains no card folder.
- A card folder fails the `HdsExport` checks.
- The unpacked size is more than 20 MB.

The app keeps the last imported site after it closes.

### 5.2 Screens

**Site screen**

- Shows the site name (the ZIP file name) and 1 row for each card:
  label and screen size, for example "english — 128 × 64".
- Has an "Open ZIP" button.
- If no site is imported, it tells the person to open the ZIP that they received.

**Send screen** (opens when the person selects a card)

1. Shows the instructions: "Connect the phone to the WiFi of the display in Android settings.
   The password is 88888888." It has a button that opens the Android WiFi settings.
2. Shows the connection status (section 5.3). The "Send" button is available only when the
   status is "Display found".
3. When the person taps "Send", the app asks: "This program is for a 128 × 64 display
   named 'english'. Is the phone connected to this display?" with "Yes, send" and "Cancel".
4. During the send, the screen shows a progress bar (frames sent of total frames).
   The screen stays on, and the back button is disabled.
5. At the end, the screen shows the result message (section 6.2) and a "Share log" button.

### 5.3 Connection check

The check runs every 2 seconds while the Send screen is open and no send is active.

1. `CardNetwork` looks for a connected network with WiFi transport. It does not require
   internet access, because the card WiFi has none.
2. If no WiFi network is found, the status is "Not connected to WiFi".
3. If one is found, the app opens a TCP connection to `192.168.4.1:6101` through a socket
   that is bound to that network. Connect timeout: 3 seconds.
4. The app sends the probe and waits up to 3 seconds for a reply that starts with `HR`.
5. If the reply arrives, the status is "Display found". If not, the status is
   "No display found on this WiFi".

All card traffic uses sockets that are bound to the WiFi network. Thus the traffic does not
go over mobile data when the WiFi has no internet access.

The app needs only the `INTERNET` and `ACCESS_NETWORK_STATE` permissions.
It does not need the location permission.

## 6. Send and error handling

### 6.1 Send algorithm

1. Make the frame list with a new random transfer ID.
2. Open 1 TCP connection to `192.168.4.1:6101`, bound to the WiFi network.
   Connect timeout: 5 seconds.
3. For each frame: send the frame, then wait up to 10 seconds for its ACK.
4. On success of the `0x04` frame, close the connection. The send is complete.

**Retries:**

- If a connection fails, a write fails, an ACK times out, or the card closes the connection,
  the app closes the connection and starts again at step 1. The capture showed that the card
  accepts a complete transfer after failed partial transfers.
- The app makes a maximum of 3 attempts.
- If `CardNetwork` reports that the WiFi network is lost, the app stops at once and does
  not retry.
- If an ACK has a status that is not 0, the app stops at once and does not retry.
- If a reply does not start with `HR`, the app stops at once and does not retry.

### 6.2 Messages

| Condition | Message |
|---|---|
| Success | "Done. The display shows the new program." |
| Network failures after 3 attempts | "The WiFi signal is too weak. Move nearer to the display and try again." |
| The WiFi disconnected during the send | "The phone left the WiFi of the display. Connect again and try again." |
| ACK status not 0 | "The display rejected the program (code 0xNN). Contact the person who sent you the file." |
| Unexpected reply | "The display sent an unexpected reply. Contact the person who sent you the file." |

### 6.3 Send log

The log of 1 send contains:

- The date and time, the app version, and the Android version.
- The site name, the card label, the screen size, and the size of `progam.hds`.
- For each attempt: the transfer ID, each frame (command, sequence, size), each ACK
  (command, status), and the error, if any.
- The result.

"Share log" sends the log as plain text through the Android share sheet
(for example to WhatsApp).

## 7. Testing

### 7.1 Automatic tests (JVM, no phone)

**Test data:** the 128 × 64 export from the spike (`screen.hds`, `progam.hds`) and the
16 frames of 1 complete transfer from the spike capture, stored in
`app/protocol/src/test/resources/`.

| Area | Tests |
|---|---|
| `HtFrame` | Frame layout, length, checksum, terminator. ACK parse: valid ACK, wrong prefix, short reply. |
| `HdsExport` | Valid export. Missing file. Wrong prefix. Wrong `screen.hds` size. Width and height values. |
| `ProgramTransfer` | With the transfer ID of the captured transfer, the app makes the same 16 frames, byte for byte. |
| `CardSender` | With a local fake card: normal send; missing ACK then retry then success; 3 failed attempts; error status (no retry); connection closed during transfer; unexpected reply; progress reports. |
| `CardSender` probe | `HR` reply; no reply; reply without `HR`. |
| `ZipImporter` | Valid site ZIP with 2 cards. Files at the ZIP root. No card folder. Folder with only `screen.hds`. Path with `..`. Absolute path. Unpacked size more than 20 MB. `set.hds` and `smart.ss` are not unpacked. |

`ZipImporter` keeps its logic in plain Kotlin that works on `java.io` streams and files,
so that it can run in JVM tests.

### 7.2 Manual tests on a real card (before the first release)

1. **Visible change:** Send a program with the text `TEST` from HD2020. Then send the clock
   program from the app. The display must change from `TEST` to the clock.
2. **Weak signal:** Send from far away. The app must retry, and if all attempts fail,
   it must show the weak-signal message.
3. **Mobile data on:** Send with mobile data on. The send must be successful.
4. **No card WiFi:** Open the Send screen with no WiFi, and with a WiFi that is not a card.
   The status must not be "Display found".
5. **Import from WhatsApp:** Receive a site ZIP in WhatsApp and open it with the app.

## 8. Release

- The release is 1 signed APK file.
- A new release key is made for the app. Android installs an update over an earlier version
  only if both use the same key. **The owner must keep the key and its passwords in a safe
  place.** If the key is lost, each phone must uninstall the app before it can install
  a new version.
- The key and its passwords must not be committed to git.
- The person installs the APK and allows "Install unknown apps" 1 time.

## 9. Out of scope

- Making or editing programs (HD2020 on the PC does this).
- Automatic connection to the card WiFi.
- A check that the selected folder belongs to the connected card.
- Time sync, brightness, and other card commands.
- Cards that use a router (station mode) or an IP address other than `192.168.4.1`.
- Bangla screens.
- Google Play publishing.
- iOS.

## 10. Open items

1. The visible-change test (section 7.2, test 1) has not been done yet.
   It is required before the first release.
2. The protocol was confirmed only on a WF1 card with a 128 × 64 screen.
   WF2 and WF4 cards and other screen sizes must be tested before the app is used with them.
