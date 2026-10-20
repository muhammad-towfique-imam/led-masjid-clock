# Masjid Clock: the Hijri page — design

Date: 2026-10-20. Branch: `hijri-on-phone`. App: Masjid Clock (`com.cyanwarelabs.masjidclock.app`), code in `app/`.

## 1. Intent

Many masjids that use the clock displays have no laptop. Each Hijri month, someone must set the new month on the
display. Today this needs the desktop app (`app_ui.py`) and HD2020 on a Windows PC. The Hijri page lets a person
do it with only a phone: enter the month, the year, and the month start date, connect to the display WiFi, and
send. The phone makes the program itself. No HD2020, no PC, no `.hdpkg` file.

This is the first step to make Masjid Clock independent of a laptop. Bangla and waqt come later, with the same
method.

## 2. Goals, non-goals, anti-goals

**Goals**

- A new page "Hijri" that makes and sends the m5 Hijri program from 3 inputs.
- The program is the same, byte for byte, as the program that the desktop app + HD2020 make for the same inputs
  on the same day.

**Non-goals (this version)**

- Display models m1–m4. Only m5 (384 × 32) is supported. (User decision.)
- Locations other than Dhaka. (User decision.)
- A better Maghrib rule. The app copies the Python behavior, including its known flaw (section 5.3). (User decision.)
- Saving or sharing the Hijri program as an `.hdpkg` file. (My call: the user asked for a page like Send.)
- Calculating the Hijri month or year from the date. The person enters them. (User decision.)
- A check that the connected card is the m5 Hijri display, or a confirmation before the send. The card does not
  report its size. The behavior is the same as on the Send page. (User decision: a card that gets a program for a
  larger display does not fail; it shows the part that fits.)

**Anti-goals (a failure even if it "works")**

- A program that differs from what HD2020 makes for the same inputs (wrong day number, a shifted month, a wrong
  start time). The day number on a masjid display must be correct.
- Any flicker after a send: the page must keep the rule "probe only until the card answers" (see
  `docs/protocol.md` section 4.1).
- A change to the behavior of the Send, Create, or Time pages.

## 3. The page (approved on a mockup)

A new tab **Hijri** in the bottom bar, after Time: Send | Create | Time | Hijri. The page has 4 step cards, like
the Send page (`ui/Screens.kt`):

1. **Hijri month**
   - **Month start date:** a date picker. The Gregorian date of the first day of the Hijri month (the same input
     as "start date" in the desktop app's Hijri page).
   - **Hijri month:** a list of the 12 names from `hijri_utils.get_hijri_months()`, in this order:
     Muharram, Safar, Rabiul Awl, Rabiul Akhr, Juma Ula, Juma Akhir, Rajab, Shaban, Ramadan, Shawwal,
     Zul-qaadh, Zul-hijjah.
   - **Hijri year:** a number field.
   - **A summary box** under the inputs: "Display: m5 — 384 × 32", "The display shows <month> <year>.", and
     "Day 1 starts on <date> at <HH:MM> (Maghrib)." The date and time are the counter start (section 5.3).
2. **Connect to the display WiFi:** the same as step 2 of the Send page (WiFi settings button, the connection
   status, the card name in the larger font when found).
3. **Set the time:** the same optional step as step 3 of the Send page (Set the time / Skip).
4. **Send:** the same as step 4 of the Send page (progress, result, Send again, Share log). The Send button is
   enabled only when step 1 is valid, the display is found, and step 3 is done or skipped.

The step states, the back-button block, and the screen-on rule during a send are the same as on the Send page.

## 4. Inputs and validation (my calls)

- **The app remembers the 3 inputs** (user decision). When the page opens, the inputs show the last saved values.
  The app saves a value each time the person changes it to a valid value (my call), in the app's private
  settings, so that the values stay after the app closes.
- **Date:** the last saved date; the first time, today. Any date is accepted. If the date is more than 30 days
  before or after today (user decision), the summary box shows a warning: "Check the date: it is far from today."
  The person can still send.
- **Month:** the last saved month; the first time, empty. The person must select one.
- **Year:** the last saved year; the first time, empty. Exactly 4 digits, from 1400 to 1499 (my call). Otherwise
  the field shows "Enter the Hijri year, for example 1448."

## 5. How the app makes the program

The method was tested on 2026-10-19/20: the script rebuilt 5 HD2020 exports byte for byte, and a file made from
the `m05` base with the year 1450 was shown correctly on the m5 card (correct year and correct day number).
The experiment scripts are in `/home/towfique/Documents/debug/hijri-kit/experiment/` (`hijri_gen.py`,
`check.py`); the exports are in `/home/towfique/Documents/debug/exports/`.

### 5.1 The data that the app contains

From the 17 exports (all made with the m5 template, year 1448):

- `screen.hds` of m5 (66 bytes; identical in all exports).
- 12 base files: `progam.hds` of `m01` … `m12` (one per month; each month has its own layout).
- The digit library: the 10 digits `0`–`9`, each a cell of 7 × 16 pixels in 3 color planes, cut from `m01` and
  `y1449`, `y1450`, `y1462`, `y1473` at the year columns 91, 98, 105, 112 of the `m01` text area.

How the data goes into the APK (as assets, a resource, or Kotlin constants, and whether a tool makes it from the
exports) is left to the builder. The source exports must be copied into the repository, because the
`Documents/debug` folder is not part of it.

### 5.2 The file format (only what the app uses)

- `progam.hds` = `HC` header (31 bytes) + `HP` program block + 9 `HA` areas.
- `HP` + 4: the number of areas (9). `HP` + 52: the area offset table, 4 bytes (big-endian) per area, relative
  to the start of `HP`.
- Area header: bytes 4–5 x, 6–7 y, 8–9 width, 10–11 height (big-endian).
- **Day counter areas:** the 3 areas of 306 bytes. Bytes 37–40 of the area: the counter start, 32-bit
  big-endian wall-clock seconds (local time written as if it were UTC).
- **Text areas:** the other areas with y 0 or 1 and height 16 or 19 (3 month-and-year areas). The bitmap starts
  at area byte 62: for each row, 3 color planes; each plane is `ceil(width / 32) * 4` bytes; the most significant
  bit is the left pixel.
- There is no checksum. The file size does not change.

### 5.3 The steps

1. Take the base of the selected month.
2. In each of the 3 text areas, find the base year `1448`: search the 4 digit cells (step 7 pixels) at each column
   and at a row offset from −3 to (height − 16 + 3). Write the 4 digits of the new year into the same cells, in
   all 3 planes. (The year is at row offset −2 in `m07` and `m12`, and the `m11` areas are 19 rows high.)
3. Write the counter start into the 3 day counter areas: `HijriStart.counterStart(monthStart, today)` (already
   in `hijri/HijriStart.kt`, tested against the Python code), where `today` is the phone date.
4. Send `screen.hds` + the new `progam.hds` with the existing send code: the normal form first, then the safe
   form when the card requires it (`AppViewModel.send` / `sendSafe`, `send/CardSend.kt`).

The counter start rule, from `hijri_utils.py`: the day before the month start date, at the earlier Maghrib of
max(today, month start) and month start + 30 days. Maghrib: `hijri/Maghrib.kt` (Karachi method, Dhaka, UTC+6).

### 5.4 Errors

- If the base year is not found in a text area, the app must not send. It shows "The program data of the app is
  damaged." This can occur only with a wrong build, so a test must cover all 12 months.

## 6. Send log

The log is the same as on the Send page (header, frames, ACKs, result), with 1 more line after the header:
`Hijri: <month> <year>, month start <yyyy-mm-dd>, counter start <yyyy-mm-dd HH:MM>`.

## 7. Tests

- Make `y1449`, `y1450`, `y1462`, `y1473` and `d-plus1` from the `m01` base: each result must be identical to the
  HD2020 export (the test needs those 5 `progam.hds` files as test data).
- For each of the 12 months: the year is found in all 3 text areas; a change to 1449 and back to 1448 gives the
  base again.
- The validation rules of section 4, and the saved values: they come back after the page (and the app) opens again.
- Optional (my call): if the user makes 1 more HD2020 export of a month other than `m01` (for example `m07` at
  the year 1462), add it as a byte-for-byte test. The plan does not wait for it.
- The existing tests must still pass.
- Manual test on the m5 card before a release: send the current month with the real year; the display must show
  the same text and the same day number as the program from the desktop app.

## 8. Left to the builder

- The code structure (a new view model or a shared one; how to reuse the WiFi, time, and send steps of the Send
  page without a change in their behavior).
- How the base files and the digit library are stored in the app (section 5.1).
- The exact look of the date picker and the month list (Material 3, like the other pages).
