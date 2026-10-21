# Masjid Clock Waqt Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A 4th tab "Waqt" that makes the m5 waqt program (the prayer times with dated changes, Sunrise and Sunset) on the phone and sends it.

**Architecture:** `waqt/` holds 3 pure-JVM parts: the Sunrise/Sunset calculation (an extension of `Maghrib.kt`), the schedule (the desktop app's time table and program copies, plus the 2 user-decided end rules), and the program writer (HD2020's `HP` block repeated once per copy, with the windows and the digit cells written in). A `WaqtViewModel` on `DisplayViewModel` and a `WaqtScreen` on `DisplayTabScreen` add the tab.

**Tech Stack:** Kotlin 2.1.20, Compose Material 3 (BOM 2025.02.00), JUnit 4, java.time; Python 3 (standard library) for golden values.

**Spec:** `docs/superpowers/specs/2026-10-21-masjid-clock-waqt-design.md` (section 8 = the confirmed format; 4.2a = the end rules).

## Global Constraints

- Gradle from `app/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`; test: `./gradlew :protocol:test :android:testDebugUnitTest`.
- m5 only; Dhaka (23.777176, 90.399452), UTC+6, Karachi method, no DST; all times wall-clock, written as UTC seconds.
- Prayers (index 0–5): Fajr, Zuhr, Asr, Magrib, Isha, Jumu'ah; index 6 Sunrise, 7 Sunset. Times are 12-hour, hour 1–12 stored as typed (12 stays 12); Fajr shows "am", the others "pm".
- The apply time of a change: date − 1 day, at the time (+12 h if index > 0 and hour < 12), + 30 minutes (`get_apply_date`).
- The end rule (user): with operator changes, 23:59:59 on (date of the last operator change + 3 days); without, now + 30 days. Sunrise/Sunset calculated for each day up to the end date. The 23:59:00 end of a split first day stays.
- No limit on change dates.
- Exports: `/home/towfique/Documents/debug/waqt-kit/exports/<case>/` (`progam.hds`, `screen.hds`, `inputs.json` with `now`, `times`, `changes`).
- Texts: tab "Waqt", page title "Waqt display", step title "Prayer times", the first row "Today", the button "+ Add a change", the summary lines "Today: Sunrise <h:mm am>, Sunset <h:mm pm> (calculated)." and "The program covers <d MMM yyyy> to <d MMM yyyy>.".
- Version 0.5.0 (`versionCode = 21`).

## Review Focus

- A change whose apply time is before now (for example yesterday's date): it must become the "Today" time and leave the list, not create a copy in the past → Task 4 test `mergeMovesPastChangesToToday`.
- A Zuhr or Jumu'ah at 12:xx: the display must show "12:30" and the apply time must be 12:30 + 30 min on the day before → Task 2 test.
- 2 changes of 1 prayer on the same date: the step must not allow Next → Task 4 test.
- A change more than 30 days ahead: no overlapping copies, the end 3 days after it → Task 2 test `longScheduleHasNoOverlap`.
- No operator changes: the program must still run 30 days → Task 2 test.

---

### Task 1: Sunrise and Sunset

**Files:**
- Modify: `app/android/src/main/kotlin/com/masjidclock/wfsender/hijri/Maghrib.kt`
- Create: `app/tools/waqt_program.py` (copy of `/home/towfique/Documents/debug/waqt-kit/waqt_program.py`) and `app/tools/make_waqt_golden.py`
- Create: `app/android/src/test/resources/waqt/sun-dhaka.txt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/waqt/SunTest.kt`

**Interfaces:**
- Produces: `data class SunTimes(val sunrise: LocalTime, val sunset: LocalTime)` and `object Sun { fun dhaka(date: LocalDate): SunTimes }` (package `com.masjidclock.wfsender.hijri`). `Maghrib.dhaka(date)` = `Sun.dhaka(date).sunset` (unchanged results).

- [ ] **Step 1: Golden tool.** `make_waqt_golden.py` (run from the repository root) imports the repository's `praytimes.py` (Karachi) and writes `sun-dhaka.txt`: `<yyyy-mm-dd> <sunrise HH:MM> <sunset HH:MM>` for every day 2020-01-01..2040-12-31.
- [ ] **Step 2: Failing test** `sunriseAndSunsetMatchPraytimesFrom2020To2040`: all 7,671 lines equal `Sun.dhaka(date)`.
- [ ] **Step 3: Run; it fails** (`--tests '*SunTest*'`).
- [ ] **Step 4: Implement** `Sun.dhaka`: sunrise = `noon − t` at the first guess 6/24, sunset = `noon + t` at 18/24 (`sunAngleTime` with `'ccw'` for sunrise), the same rounding as now.
- [ ] **Step 5: Run all tests; pass** (`MaghribTest` unchanged). **Commit** "Add the Sunrise time to the Dhaka sun calculation".

### Task 2: The waqt schedule

**Files:**
- Modify: `app/tools/waqt_program.py` (add `copies(times, changes, now)` that returns the list that `make_xml` writes), `app/tools/make_waqt_golden.py`
- Create: `app/android/src/test/resources/waqt/copies.txt`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/waqt/WaqtSchedule.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/waqt/WaqtScheduleTest.kt`

**Interfaces:**
- Consumes: Task 1 `Sun.dhaka`.
- Produces (package `com.masjidclock.wfsender.waqt`):
  - `data class HourMinute(val hour: Int, val minute: Int)` with `fun text(): String` = `"$hour:${"%02d"}"` right-aligned to 5 characters (`Locale.ROOT`).
  - `data class WaqtChange(val date: LocalDate, val prayer: Int /* 0..5 */, val time: HourMinute)`
  - `data class WaqtCopy(val dateStart: LocalDate, val dateEnd: LocalDate, val timeStart: Int?, val timeEnd: Int?, val times: List<String> /* 8 texts */)`
  - `object WaqtSchedule { fun applyTime(date: LocalDate, prayer: Int, time: HourMinute): LocalDateTime; fun copies(times: List<HourMinute>, changes: List<WaqtChange>, now: LocalDateTime, desktopEnd: Boolean = false): List<WaqtCopy> }` — `desktopEnd = true` is the desktop app exactly (for the golden test); `false` applies the user's end rules (Global Constraints).

- [ ] **Step 1: Golden values.** `copies()` in the Python port; the tool writes `copies.txt`: for each case a line `# <name> <now ISO> <times> <changes>` and 1 line per copy `<dateStart> <dateEnd> <timeStart or -> <timeEnd or -> <8 texts joined by |>`. Cases: the 6 export cases (their `inputs.json`), plus: a change on the first day, a Zuhr change to 12:30, a Jumu'ah change, 2 changes on 1 date, a change 40 days ahead. Run it from the repository root.
- [ ] **Step 2: Failing tests:**
  - `matchesTheDesktopApp`: every case in `copies.txt` equals `copies(..., desktopEnd = true)`.
  - `noonTime`: `applyTime(2026-10-31, 1, HourMinute(12, 30)) == 2026-10-30T13:00`, and `HourMinute(12, 30).text() == "12:30"`, `HourMinute(4, 5).text() == " 4:05"`.
  - `endThreeDaysAfterTheLastChange`: the `w1` inputs with `desktopEnd = false`: the last copy ends on 2026-11-03 (31 Oct + 3) with `timeEnd == null` or 86,399 for the whole last day, and Sunrise/Sunset texts change daily up to that date.
  - `noChangesRunsThirtyDays`: the `w0` inputs: the last copy's `dateEnd == now.toLocalDate() + 30 days`.
  - `longScheduleHasNoOverlap`: a change 40 days ahead, `desktopEnd = false`: the copies do not overlap in time and are in order; the last ends 3 days after that change.
- [ ] **Step 3: Run; they fail.**
- [ ] **Step 4: Implement** `WaqtSchedule` as a port of `waqt_program.copies` (spec 4.1–4.2, the split at midnight, `WHOLE_DAY_IN_SEC = 86340`), plus the 2 end rules for `desktopEnd = false`.
- [ ] **Step 5: Run all tests; pass. Commit** "Add the waqt schedule of the desktop app with the 3-day end".

### Task 3: The waqt program

**Files:**
- Create: `app/android/src/main/resources/waqt/m5/base.hds` (= `w-digits/progam.hds`), `screen.hds` (= `w-digits/screen.hds`)
- Create: `app/android/src/test/resources/waqt/m5/w0.hds`, `w1.hds`, `w3.hds` (the exports' `progam.hds`)
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/waqt/WaqtProgram.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/waqt/WaqtProgramTest.kt`

**Interfaces:**
- Consumes: Task 2 `WaqtCopy`, `WaqtSchedule.copies`; `display/ProgramFile.kt` `Programs`, `Area`, `Glyph`; `HijriDataException`.
- Produces: `object WaqtM5 { val screen: ByteArray; val digits: Map<Char, Glyph> }` and `object WaqtProgram { fun make(copies: List<WaqtCopy>): ByteArray }`.

- [ ] **Step 1: Copy the data** and check with `sha256sum`.
- [ ] **Step 2: Failing tests:**
  - `rebuildsTheExports`: for `w0`, `w1`, `w3`: `WaqtProgram.make(WaqtSchedule.copies(<its inputs.json>, desktopEnd = true))` equals the export (`assertArrayEquals`). The inputs are constants in the test, copied from each `inputs.json` (`now` without microseconds).
  - `digitsAreComplete`: `WaqtM5.digits.keys == (' ' + '0'..'9')`.
  - `aDamagedBaseThrows`: a base with its time areas cleared → `HijriDataException` when the digits are cut.
- [ ] **Step 3: Run; they fail.**
- [ ] **Step 4: Implement.** The digits: cut from the first program of `base.hds`, whose 8 texts are `" 4:26"`, `"12:37"`, `" 3:48"`, `" 5:59"`, `" 7:01"`, `"10:15"`, `" 0:00"`, `" 0:00"`, at cell columns 20, 27, 38, 45 (text index 0, 1, 3, 4), rows of 16 pixels, in the 3 time areas (areas with x in {81, 209, 337}); 2 cuts of 1 character that differ → `HijriDataException`. `make`: the `HC` header = the first 27 bytes of `base.hds` with byte 4 = the copy count, then 1 absolute offset per copy; each copy = the first `HP` block of `base.hds` with (spec 8): `+5` = 1 if a time window, `+6..8` start, `+9..11` end (3-byte big-endian; no window: 0 and 86,399), `+17` = 1, `+18..21` the start date at 00:00, `+22..25` the end date at 00:00 + 86,399 (wall-clock seconds), and the 8 texts written into the digit cells of the 3 time areas. More than 255 copies → `HijriDataException` (the count is 1 byte).
- [ ] **Step 5: Run all tests; pass. Commit** "Make the m5 waqt program from the schedule".

### Task 4: The waqt inputs

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/waqt/WaqtInput.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/waqt/WaqtInputTest.kt`

**Interfaces:**
- Consumes: Task 2 types and `WaqtSchedule`; Task 1 `Sun`.
- Produces:
  - `object WaqtPrayers { val NAMES: List<String> /* 6, Global Constraints */; fun period(prayer: Int): String /* "am" for 0, else "pm" */ }`
  - `data class WaqtValues(val times: List<HourMinute?> /* 6 */, val changes: List<WaqtChange>)`
  - `object WaqtInput { fun merged(values: WaqtValues, now: LocalDateTime): WaqtValues; fun isValid(values: WaqtValues): Boolean; fun nextRow(values: WaqtValues, prayer: Int, today: LocalDate): WaqtChange; fun summary(values: WaqtValues, now: LocalDateTime): List<String> }`
  - `object WaqtCodec { fun encode(values: WaqtValues): String; fun decode(text: String?): WaqtValues? }` — used for the last values and the card values.
  - `fun waqtLogHeader(values: WaqtValues, copies: Int, programSize: Int): List<String>` → `["Display: m5, 384 × 128, progam.hds <size> bytes", "Waqt: <copies> copies, <n> changes"]`.

- [ ] **Step 1: Failing tests:**
  - `mergeMovesPastChangesToToday`: now 2026-10-21T12:00; an Asr change on 2026-10-21 (apply 2026-10-20T16:45) and one on 2026-10-31 → `merged` has Asr = the 21 Oct time and only the 31 Oct change.
  - `validity`: all 6 times needed; 2 changes of Asr on 1 date → invalid; 2 prayers on 1 date → valid.
  - `nextRow`: after the last Asr row on 31 Oct 4:30 → `WaqtChange(2026-11-01, 2, 4:30)`; with no change rows → today + 1 day and the Today time.
  - `codecRoundTripAndDamaged`: round trip; `null`, `""`, a 13th hour, an unknown prayer → null.
  - `summaryLines`: for the `w1` inputs at its now: `"Today: Sunrise 6:<mm> am, Sunset 5:<mm> pm (calculated)."` from `Sun.dhaka`, and `"The program covers <first dateStart> to <last dateEnd>."` (format `d MMM yyyy`, `Locale.US`).
- [ ] **Step 2: Run; they fail.** **Step 3: Implement.** **Step 4: Run all; pass. Commit** "Add the waqt inputs".

### Task 5: The Waqt tab

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/WaqtViewModel.kt`, `ui/WaqtScreen.kt`
- Modify: `display/DisplayTab.kt` (`WAQT("Waqt")`), `AppViewModel.kt` (`Page.WAQT` tab after HIJRI), `ui/MainScaffold.kt` (4th bar item, letter "W", title "Waqt display"), `MainActivity.kt`, `app/android/build.gradle.kts`, `app/README.md`

**Interfaces:**
- Consumes: Tasks 2–4; `DisplayViewModel`, `SendJob`, `DisplayTabScreen(model, inputsValid, inputTitle, inputStep)`.
- Produces: `class WaqtViewModel(app) : DisplayViewModel(app, DisplayTab.WAQT)` with `values: StateFlow<WaqtValues>`, `setTime(prayer: Int, time: HourMinute)`, `setChange(index: Int, change: WaqtChange)`, `addChange(prayer: Int)`, `removeChange(index: Int)`; settings in prefs "waqt" (`WaqtCodec`); `applyCardValues` = `WaqtCodec.decode` + `WaqtInput.merged`; `prepareSend` → `SendJob(makeExport = HdsExport(WaqtM5.screen, WaqtProgram.make(WaqtSchedule.copies(...)))`, `logHeader = waqtLogHeader`, `cardValues = WaqtCodec.encode`); on load, the values pass through `merged(now)`.

- [ ] **Step 1: The view model** as above; every setter calls `inputChanged()` and saves the last values.
- [ ] **Step 2: The screen** (spec 3, approved mockup v2): 6 cards; the first row "Today" + a time field; change rows with a date field (`DatePickerDialog`, `HijriInput.dateFromPicker`), a time field (Material 3 `TimePicker`, `is24Hour = false`; the result hour = `hour % 12`, 0 → 12; the am/pm label fixed by `WaqtPrayers.period`), and ✕; "+ Add a change" uses `WaqtInput.nextRow`; the summary under the list (outside the scroll) from `WaqtInput.summary`; `CARD_VALUES_NOTE` when the card values were used.
- [ ] **Step 3: Navigation and version.** The 4th tab; the card list texts work with `WAQT` ("Waqt display", "This card last got the Waqt program."); `versionCode = 21`, `versionName = "0.5.0"`; README: a section "Set the prayer times" and manual tests (the times change 30 minutes after the prayer on the day before; Sunrise/Sunset change daily; the display is blank after the end; a 12:30 Zuhr shows "12:30").
- [ ] **Step 4: Build and test.** `./gradlew clean :protocol:test :android:testDebugUnitTest :android:assembleRelease` → all pass; the APK certificate SHA-256 `2fbf779f1b860193471a14f38b7a4455c9a1fa5c4c55e6ed031c1eba019f52ec`; delete `app/build/signing`.
- [ ] **Step 5: Commit** "Add the Waqt tab"; copy the APK to `/home/towfique/Documents/debug/`.
