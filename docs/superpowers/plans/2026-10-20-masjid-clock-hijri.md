# Masjid Clock Hijri Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new "Hijri" tab in Masjid Clock that makes the m5 Hijri program from a month start date, a Hijri month, and a Hijri year, and sends it to the card.

**Architecture:** Pure-JVM code in `hijri/` builds `progam.hds` from 12 stored HD2020 month bases: it writes the year digits (cut from stored HD2020 exports) and the day counter start (`HijriStart`, already done). A new `HijriViewModel` copies the connection, time, and send flow of `AppViewModel` (the codebase already copies this flow in `TimeViewModel`) and sends the program as an in-memory `HdsExport`. A new `HijriScreen` uses the 4-step layout of the Send page.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose Material 3 (BOM 2025.02.00), JUnit 4, java.time (desugared), Gradle 8.11.1 with Java 21.

**Spec:** `docs/superpowers/specs/2026-10-20-masjid-clock-hijri-design.md`

## Global Constraints

- Run all Gradle commands from `app/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`.
- minSdk 24; `java.time` is allowed (core library desugaring is on).
- Only the m5 display (384 × 32), only Dhaka.
- The program must be byte for byte what HD2020 makes for the same inputs on the same day.
- The month names, in this order: Muharram, Safar, Rabiul Awl, Rabiul Akhr, Juma Ula, Juma Akhir, Rajab, Shaban, Ramadan, Shawwal, Zul-qaadh, Zul-hijjah.
- Year: exactly 4 ASCII digits, 1400 to 1499. Error text: "Enter the Hijri year, for example 1448."
- Date warning when the month start date is more than 30 days before or after today: "Check the date: it is far from today."
- Damaged data text: "The program data of the app is damaged."
- The app remembers the 3 inputs (saved on each change to a valid value); first use: date = today, month and year empty.
- No change in the behavior of the Send, Create, and Time pages.
- Probe the card only until it answers; never probe again after a send (flicker rule, `docs/protocol.md` 4.1).
- User-visible texts in Simplified Technical English, like the existing `send/Messages.kt`.

## Review Focus

- The Material 3 date picker gives UTC midnight milliseconds; in UTC+6 a local-zone conversion gives the wrong day → Task 2 tests `HijriInput.dateFromPicker`.
- Year typed with non-ASCII digits (Bengali `১৪৪৮`, Arabic-Indic `١٤٤٨`) or spaces: `String.toIntOrNull` accepts Unicode digits → Task 2 tests that these are rejected.
- Saved values from an older or damaged state (month 13, a date that does not parse) → Task 2 tests that the defaults are used.
- The app comes back to the foreground after a send on the Hijri tab: it must not start the probes again → Task 4 rule in `HijriViewModel.startChecks` and a manual check.
- The date exactly 30 days from today gives no warning; 31 days gives the warning → Task 2 test.

---

### Task 1: Make the m5 Hijri program

**Files:**
- Create: `app/android/src/main/resources/hijri/m5/screen.hds`, `m01.hds` … `m12.hds`, `y1449.hds`, `y1450.hds`, `y1462.hds`, `y1473.hds` (copies)
- Create: `app/android/src/test/resources/hijri/m5/d-plus1.hds` (copy)
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/hijri/HijriProgram.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/hijri/HijriProgramTest.kt`

**Interfaces:**
- Consumes: `HijriStart.counterStart(monthStart: LocalDate, today: LocalDate): LocalDateTime` (exists).
- Produces:
  - `class HijriDataException(message: String) : Exception(message)`
  - `object HijriM5 { val screen: ByteArray; fun base(month: Int): ByteArray /* 1..12 */; val digits: Map<Char, Glyph> }`
  - `class Glyph` (the pixels of 1 digit cell: 3 planes × 16 rows × 7 columns; `equals` compares the pixels)
  - `object HijriProgram { fun make(base: ByteArray, digits: Map<Char, Glyph>, year: Int, counterStart: LocalDateTime, baseYear: Int = 1448): ByteArray }` — throws `HijriDataException` if `baseYear` is not found in a text area.

- [ ] **Step 1: Copy the data.** From `/home/towfique/Documents/debug/exports/<folder>/progam.hds`: folders `m01`…`m12` → `m01.hds`…`m12.hds`; `y1449`, `y1450`, `y1462`, `y1473` → `y1449.hds` …; `d-plus1` → the test resource `d-plus1.hds`. `screen.hds` from `m01/screen.hds`. Check with `sha256sum` that the copies equal the sources.

- [ ] **Step 2: Write the failing tests** in `HijriProgramTest`:
  - `rebuildsTheYearExportsFromM01`: for (1449, `y1449.hds`), (1450, …), (1462, …), (1473, …): `HijriProgram.make(HijriM5.base(1), HijriM5.digits, year, LocalDateTime.of(2026, 9, 30, 17, 20))` equals the export bytes (`assertArrayEquals`).
  - `rebuildsDPlus1`: year 1448, counter start `2026-10-01T17:20` → equals `d-plus1.hds`.
  - `keepsTheBaseForTheSameYearAndStart`: `make(base(1), digits, 1448, 2026-09-30T17:20)` equals `base(1)`.
  - `everyMonthRoundTrips`: for months 1..12: `make(make(base(m), digits, 1449, start), digits, 1448, start, baseYear = 1449)` equals `make(base(m), digits, 1448, start)`, and the 1449 result differs from the base. (`start` = the counter start read back is not needed; use `2026-09-30T17:20`, which is the start in all bases.)
  - `damagedBaseThrows`: clear the bitmap bytes of the first text area of `base(1)` → `assertThrows(HijriDataException::class.java)`.
  - `digitLibraryHasAllDigits`: `HijriM5.digits.keys == ('0'..'9').toSet()`.

- [ ] **Step 3: Run the tests; they fail** (unresolved references).
  Run: `./gradlew :android:testDebugUnitTest --tests '*HijriProgramTest*'`

- [ ] **Step 4: Implement `HijriProgram.kt`.** The file format (spec 5.2):
  - Area list: count at `HP+4` (HP starts at byte 31); offsets at `HP+52`, 4 bytes big-endian each, relative to HP; an area ends where the next starts (the last at the file end).
  - Area header: x at 4–5, y at 6–7, width at 8–9, height at 10–11.
  - Day counter area = an area of 306 bytes; counter start at area bytes 37–40, big-endian seconds of `counterStart` taken as UTC (`counterStart.toEpochSecond(ZoneOffset.UTC)`).
  - Text area = not 306 bytes, y in {0, 1}, height in {16, 19}.
  - Bitmap from area byte 62; row r, plane p (0..2) starts at `62 + (r*3 + p) * stride`, `stride = ceil(width/32)*4`; bit 7 of a byte is the left pixel.
  - Year search (spec 5.3 step 2): for row offset `dr` in `-3..(height-16+3)`, for column `col` in `0..stride*8 - 4*7`: the 4 cells at `col + k*7` (k = 0..3), each 7 columns × 16 rows with glyph row `r` at area row `r + dr` (rows outside the area read as 0), equal the glyphs of the 4 digits of `baseYear`. Write the new year's glyphs into the same cells (all 3 planes, only rows inside the area).
  - `HijriM5` loads the resources with `HijriM5::class.java.getResourceAsStream("/hijri/m5/<name>")`. The digit library: cut cells at columns 91, 98, 105, 112, row offset 0, from the first text area of `m01.hds` (`1448`) and of `y1449`, `y1450`, `y1462`, `y1473` (the digits of the file name). If 2 cuts of the same digit differ, throw `HijriDataException`.

- [ ] **Step 5: Run the tests; they pass.** Same command. Then run all tests: `./gradlew :protocol:test :android:testDebugUnitTest`.

- [ ] **Step 6: Commit** — `git add` the resources, `HijriProgram.kt`, the test; message "Make the m5 Hijri program from the month bases".

### Task 2: The inputs: months, validation, and saved values

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/hijri/HijriInput.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/hijri/HijriInputTest.kt`

**Interfaces:**
- Produces:
  - `object HijriMonths { val NAMES: List<String> /* the 12 names, Global Constraints order */ }`
  - `object HijriInput { const val YEAR_ERROR = "Enter the Hijri year, for example 1448."; const val DATE_WARNING = "Check the date: it is far from today."; fun parseYear(text: String): Int? /* null unless ^[0-9]{4}$ and 1400..1499 */; fun dateIsFar(date: LocalDate, today: LocalDate): Boolean /* |days| > 30 */; fun dateFromPicker(utcMillis: Long): LocalDate; fun toPickerMillis(date: LocalDate): Long }`
  - `data class HijriValues(val monthStart: LocalDate, val month: Int?, val yearText: String)`
  - `class HijriSettings(private val read: (String) -> String?, private val write: (String, String?) -> Unit) { fun load(today: LocalDate): HijriValues; fun saveDate(date: LocalDate); fun saveMonth(month: Int); fun saveYear(year: Int) }` — keys `monthStart` (ISO date), `month` (1..12), `year`.

- [ ] **Step 1: Write the failing tests:**
  - `monthNames`: `NAMES.size == 12`, `NAMES[0] == "Muharram"`, `NAMES[4] == "Juma Ula"`, `NAMES[11] == "Zul-hijjah"`.
  - `parseYear`: `"1448"` → 1448; `"1400"` → 1400; `"1499"` → 1499; `"1399"`, `"1500"`, `"448"`, `"14480"`, `" 1448"`, `"14a8"`, `""`, `"১৪৪৮"`, `"١٤٤٨"` → null.
  - `dateIsFar`: today 2026-10-20: 2026-11-19 (30 days) → false; 2026-11-20 → true; 2026-09-20 → false; 2026-09-19 → true.
  - `pickerConversion`: `dateFromPicker(LocalDate.of(2026,10,12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) == 2026-10-12`, and `dateFromPicker(toPickerMillis(d)) == d`, with the default time zone set to `Asia/Dhaka` during the test (restore it after).
  - `settingsFirstUse`: empty map → `HijriValues(today, null, "")`.
  - `settingsRoundTrip`: save date 2026-10-12, month 5, year 1448 into a `HashMap`-backed `HijriSettings`; a new `HijriSettings` on the same map loads `HijriValues(2026-10-12, 5, "1448")`.
  - `settingsIgnoreDamagedValues`: map `{monthStart="12/10/26", month="13", year="abc"}` → `HijriValues(today, null, "")`.

- [ ] **Step 2: Run; they fail.** `./gradlew :android:testDebugUnitTest --tests '*HijriInputTest*'`
- [ ] **Step 3: Implement `HijriInput.kt`.** `parseYear` uses a regex `^[0-9]{4}$`, not `toIntOrNull` alone. The picker conversion uses `ZoneOffset.UTC` both ways.
- [ ] **Step 4: Run; they pass.** Then all tests.
- [ ] **Step 5: Commit** — "Add the Hijri inputs, their checks, and their saved values".

### Task 3: The send log for a program without a package

**Files:**
- Modify: `app/android/src/main/kotlin/com/masjidclock/wfsender/send/SendLog.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/send/SendLogTest.kt`

**Interfaces:**
- Produces: a second constructor `SendLog(header: List<String>, appVersion: String, androidVersion: String, now: () -> Date = { Date() })`. The log starts with `Masjid Clock <appVersion>, Android <androidVersion>`, `Time: <yyyy-MM-dd HH:mm:ss>`, then the `header` lines. The existing constructor (site, card) keeps its exact output.
- Produces: `fun hijriLogHeader(month: String, year: Int, monthStart: LocalDate, counterStart: LocalDateTime, programSize: Int): List<String>` in `hijri/HijriInput.kt` → `["Display: m5, 384 × 32, progam.hds <size> bytes", "Hijri: <month> <year>, month start <yyyy-mm-dd>, counter start <yyyy-mm-dd HH:mm>"]`.

- [ ] **Step 1: Write the failing tests:** `SendLogTest.headerConstructor` — with `now` fixed, the text starts with the 2 standard lines, then the 2 header lines from `hijriLogHeader("Juma Ula", 1448, 2026-10-12, 2026-10-11T17:14, 6053)`, i.e. `"Hijri: Juma Ula 1448, month start 2026-10-12, counter start 2026-10-11 17:14"`. The existing SendLog tests stay unchanged.
- [ ] **Step 2: Run; it fails.** `./gradlew :android:testDebugUnitTest --tests '*SendLogTest*'`
- [ ] **Step 3: Implement** the constructor (the old constructor delegates to it) and `hijriLogHeader`.
- [ ] **Step 4: Run; all tests pass.**
- [ ] **Step 5: Commit** — "Let the send log start with any header lines".

### Task 4: The Hijri tab

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/HijriViewModel.kt`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/ui/HijriScreen.kt`
- Modify: `AppViewModel.kt` (`Page.HIJRI`, `openHijri()`), `ui/MainScaffold.kt` (tab, title, `sending`), `MainActivity.kt` (the new view model), `app/android/build.gradle.kts` (version), `app/README.md`

**Interfaces:**
- Consumes: Task 1 `HijriM5`, `HijriProgram.make`, `HijriDataException`; Task 2 `HijriMonths`, `HijriInput`, `HijriSettings`, `HijriValues`; Task 3 `SendLog(header, …)`, `hijriLogHeader`; existing `stepStates(...)` (step 1 = inputs valid), `ConnectionCheck`, `CardSender`, `SafeSender`, `CardClock`, `SendGate`, `SendState`, `TimeStep`, `Messages`, `StepCard`, `StatusLine`, `FoundLine`, `IconLabel`, `KeepScreenOn`.
- Produces: `class HijriViewModel(app: Application) : AndroidViewModel(app)` with `values: StateFlow<HijriValues>`, `setDate(LocalDate)`, `setMonth(Int)`, `setYearText(String)`, `connection`, `cardName`, `timeStep`, `sendState`, `startChecks()`, `stopChecks()`, `setTime()`, `skipTime()`, `send()`, `sendAgain()`.

- [ ] **Step 1: `HijriViewModel`.** Copy the connection check, card name, time step, and send flow of `AppViewModel` (normal form, then `SafeSender` on `SAFE_REQUIRED`), with these differences:
  - The export is `HdsExport(HijriM5.screen, HijriProgram.make(HijriM5.base(month), HijriM5.digits, year, HijriStart.counterStart(monthStart, LocalDate.now())))`, made when Send is tapped. On `HijriDataException`, finish with `SendState.Finished(false, "The program data of the app is damaged.", log)` and do not send.
  - The log: `SendLog(hijriLogHeader(...), BuildConfig.VERSION_NAME, Build.VERSION.RELEASE)`.
  - `startChecks()` does nothing while a send runs or after a send finished (`sendState` is `Sending` or `Finished`); `sendAgain()` sets `Idle`, `TimeStep.Pending`, and starts the checks.
  - Settings: `HijriSettings` on `getSharedPreferences("hijri", MODE_PRIVATE)`; `setDate`/`setMonth` save at once; `setYearText` updates the text and saves only when `HijriInput.parseYear` gives a year.
  - A send needs: month set, year valid, display found, time step done.
- [ ] **Step 2: `HijriScreen`** (mockup approved in the spec, section 3): 4 `StepCard`s with `stepStates(inputsValid, connection, timeStep.done, sendState)`:
  1. "Hijri month": a date field that opens a Material 3 `DatePickerDialog` (`initialSelectedDateMillis = HijriInput.toPickerMillis(date)`, result via `HijriInput.dateFromPicker`); an `ExposedDropdownMenuBox` with `HijriMonths.NAMES`; an `OutlinedTextField` "Hijri year" (number keyboard; shows `YEAR_ERROR` when the text is not empty and not valid); the summary box: "Display: m5 — 384 × 32", "The display shows <month> <year>.", "Day 1 starts on <d MMM yyyy> at <HH:mm> (Maghrib)." (from `HijriStart.counterStart(date, today)`), and `DATE_WARNING` when `HijriInput.dateIsFar`. Show the last 2 lines only when the inputs are valid.
  2.–4. The same content as steps 2–4 of `SendScreen` (WiFi settings button and status with `FoundLine`, time step with Set the time / Skip, Send with progress / result / Send again / Share log).
  - `LifecycleResumeEffect`: `startChecks()` on resume, `stopChecks()` on pause, like `TimeScreen`. `BackHandler` and `KeepScreenOn` during a send, like `SendScreen`. All inputs are disabled during a send.
- [ ] **Step 3: Navigation.** `Page.HIJRI`; `AppViewModel.openHijri()` like `openTime()`; a 4th `NavigationBarItem` "Hijri" (icon `Icons.Filled.Star`) after Time; top bar title "Hijri month"; the bar items are disabled while either view model sends. `MainActivity` creates `HijriViewModel` with `by viewModels()` and passes it to `WfSenderApp`.
- [ ] **Step 4: Version and README.** `versionCode = 4`, `versionName = "0.2.0"`. README: a section "Set the Hijri month" with the 4 steps, the note "m5 displays (384 × 32) only", and manual tests: (a) the current month with the real year shows the same text and day number as the desktop app's program; (b) after a send, switch to another app and back: the display does not flicker.
- [ ] **Step 5: Build and test.** `./gradlew clean :protocol:test :android:testDebugUnitTest :android:assembleRelease` → all tests pass; `masjid-clock-0.2.0.apk` is signed with SHA-256 `2fbf779f1b860193471a14f38b7a4455c9a1fa5c4c55e6ed031c1eba019f52ec` (`apksigner verify --print-certs`). Delete `app/build/signing` after the check.
- [ ] **Step 6: Commit** — "Add the Hijri tab". Copy the APK to `/home/towfique/Documents/debug/` for the card test.
