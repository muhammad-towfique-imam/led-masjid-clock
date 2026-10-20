# Masjid Clock Display Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The bottom bar becomes English | Bangla | Hijri (1 tab for each display of the stack); Send, Create, and Time move into a side menu; the Bangla tab makes the m5 Bangla program from a year; the English tab sends the stored English program; a card list warns when a card got a different program last time.

**Architecture:** A shared file-format layer (`display/ProgramFile.kt`) reads the `HC` program table and the area types, for 1 program (Hijri) or 13 (Bangla). `bangla/` holds the calendar rule and the program maker; the year digits reuse the Hijri digit shapes. A shared view model base (`DisplayViewModel`) holds the connection, time, send, and card-list flow that `HijriViewModel` has now; English, Bangla, and Hijri view models extend it, and 3 small screens share the step composables.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose Material 3 (BOM 2025.02.00), JUnit 4, java.time (desugared), Gradle 8.11.1 with Java 21; Python 3 for the golden-value tool.

**Spec:** `docs/superpowers/specs/2026-10-20-masjid-clock-display-tabs-design.md` (section 9 = the confirmed format).

## Global Constraints

- Run Gradle from `app/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk`. Test: `./gradlew :protocol:test :android:testDebugUnitTest`.
- Only m5. The exports are in `/home/towfique/Documents/debug/bangla-kit/exports/<folder>/` (`english`, `bn1429`, `bn1432`, `bn1434`, `bn1436`, `bn1447`, `bn1458`).
- Bangla dates: fixed UTC+6; all program date and counter fields are wall-clock seconds (local time written as UTC), unsigned 32-bit big-endian.
- Bangla program 13 shows year + 1 (user decision; the desktop app shows "1431" there). This is the only intended difference from the desktop app.
- Bangla month names, in program order: Baishakh, Joishtha, Asharh, Shrabon, Bhadro, Ashshin, Kartik, Augrohayon, Poush, Magh, Falgun, Choitro, Baishakh.
- Year input (Bangla): exactly 4 ASCII digits, 1400 to 1499 — reuse `HijriInput.parseYear`. Error text: "Enter the Bangla year, for example 1432."
- Tabs in this order: English, Bangla, Hijri. Menu items: Send, Create, Time. First start: English; later: the last tab.
- Card list texts: same tab → "<Tab> display" (normal color); other tab → "This card last got the <Tab> program." (error color). <Tab> = English, Bangla, Hijri.
- Bangla warning: "Check the year: today is in <current Bangla year>." Bangla change line: "The date changes every day at 00:00."
- The Hijri, Send, Create, and Time behavior must not change (only where they open from). Never probe after a send.
- Version 0.3.0 (`versionCode = 11`).

## Review Focus

- The Bangla year on 13 and 14 April (the year changes on 14 April) → Task 2 tests `currentYear`.
- A Bangla year whose ranges do not contain today (for example 1440): the summary must show the warning and no date, without a crash → Task 2 tests `dateOn` returns null.
- The year 1499: program 13 must show 1500 → Task 3 test.
- A saved card list that is damaged or from an older format must give an empty list, not a crash → Task 4 test.
- A card that does not give its name (discover fails): no note, and nothing is saved after a send → Task 4 test of `cardNote(null, …)` and of `remember` with a blank name.

---

### Task 1: The shared file format

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/display/ProgramFile.kt`
- Modify: `app/android/src/main/kotlin/com/masjidclock/wfsender/hijri/HijriProgram.kt` (move `Glyph`, `Area`, `Areas` out; keep `HijriProgram`, `HijriM5`, `HijriDataException`)
- Create: `app/android/src/main/resources/bangla/m5/base.hds` (= `bn1432/progam.hds`), `app/android/src/main/resources/bangla/m5/screen.hds` (= `bn1432/screen.hds`)
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/display/ProgramFileTest.kt`

**Interfaces:**
- Produces (package `com.masjidclock.wfsender.display`):
  - `class Glyph` (moved unchanged) plus `fun shape(): Shape` (pixel on in any plane).
  - `class Shape(pixels: BooleanArray)` — 16 rows × 7 columns, `operator fun get(row: Int, column: Int): Boolean`, value equality.
  - `class Area(program: ByteArray, start: Int, end: Int)` (moved), plus `val type: Int` (area bytes 34–35, big-endian), `fun findShapes(year: List<Shape>): Pair<Int, Int>?` (same search as `find`, compares `cell(...).shape()`), `fun planesAt(column: Int, rowOffset: Int, count: Int): Set<Int>` (planes with any ink in `count` cells), `fun writeShape(column: Int, rowOffset: Int, shape: Shape, planes: Set<Int>)` (pixel = shape pixel and plane in `planes`, else 0).
  - `object AreaType { const val TEXT = 0x1630; const val COUNTER = 0x3d34; const val TIME = 0x6032 }`
  - `class Program(val file: ByteArray, val start: Int, val end: Int) { val areas: List<Area> }`
  - `object Programs { fun of(file: ByteArray): List<Program> }` — count at `HC` byte 4; offsets at `27 + 4*i` (4 bytes, absolute); each program ends at the next offset or the file end. Areas as now (count at `HP`+4, table at `HP`+52, relative to `HP`).
- `Areas.of(file)` is kept for Hijri and returns `Programs.of(file)[0].areas` (Hijri's `isText`/`isCounter`/`isDivider` stay exactly as now).

- [ ] **Step 1: Copy the 2 resources** from `bn1432/` and check them with `sha256sum`.
- [ ] **Step 2: Write the failing tests** in `ProgramFileTest`:
  - `hijriFileHasOneProgram`: `Programs.of(HijriM5.base(1)).size == 1`, and its areas equal `Areas.of(HijriM5.base(1))` by `start`.
  - `banglaFileHas13ProgramsWith9Areas`: base → 13 programs; each has 9 areas: 3 of type `TEXT`, 3 `COUNTER`, 3 `TIME`.
  - `banglaProgramOffsets`: the first program starts at 79, the last at 93187 (from the analysis).
  - `shapeIgnoresTheColor`: a Hijri digit glyph and the same digit cut from the Bangla base (program 1, first text area) have equal `shape()` (find "1432" with `findShapes` and the Hijri digit shapes; assert not null).
- [ ] **Step 3: Run; they fail.** `./gradlew :android:testDebugUnitTest --tests '*ProgramFileTest*'` → unresolved references.
- [ ] **Step 4: Implement `ProgramFile.kt`** and move the classes; update the imports of `HijriProgram.kt` and its tests.
- [ ] **Step 5: Run all tests; they pass** (the Hijri tests unchanged).
- [ ] **Step 6: Commit** — "Read the program table and the area types of a progam.hds".

### Task 2: The Bangla calendar rule

**Files:**
- Create: `app/tools/make_bangla_golden.py`, `app/android/src/test/resources/bangla/ranges.txt`
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/bangla/BanglaCalendar.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/bangla/BanglaCalendarTest.kt`

**Interfaces:**
- Produces (package `com.masjidclock.wfsender.bangla`):
  - `object BanglaCalendar { val MONTHS: List<String> /* 13 names, Global Constraints */; fun ranges(year: Int): List<ClosedRange<LocalDate>> /* 13, set_month */; fun currentYear(today: LocalDate): Int; fun dateOn(year: Int, day: LocalDate): BanglaDate? }`
  - `data class BanglaDate(val day: Int, val month: String, val year: Int)` — program 13 gives `year + 1`.

- [ ] **Step 1: The golden tool.** `make_bangla_golden.py` imports `bangla_utils` from the repository root (stub the module `bangladatetime`, as `make_hijri_golden.py` stubs `hijri_converter`), calls `set_month(year, programs)` with 13 fake program objects (a dict subclass whose `find_all` returns `[]`) for each year 1400–1499, with `TZ=Etc/GMT-6` (fixed UTC+6) set before `time.tzset()`, converts each `dateStart`/`dateEnd` back to a date in UTC+6, and writes 1 line per program: `<year> <index 1..13> <start yyyy-mm-dd> <end yyyy-mm-dd>`. Run it from the repository root.
- [ ] **Step 2: Write the failing tests:**
  - `rangesMatchTheDesktopAppFrom1400To1499`: every line of `ranges.txt` (1,300 lines) equals `ranges(year)[index-1]`.
  - `currentYear`: 2026-04-13 → 1432; 2026-04-14 → 1433; 2026-01-09 → 1432.
  - `dateOnThePhotoDay`: `dateOn(1432, 2026-01-09) == BanglaDate(25, "Poush", 1432)`.
  - `dateOnTheYearEdges`: `dateOn(1432, 2025-04-14) == BanglaDate(1, "Baishakh", 1432)`; `dateOn(1432, 2026-04-13) == BanglaDate(30, "Choitro", 1432)`; `dateOn(1432, 2026-04-14) == BanglaDate(1, "Baishakh", 1433)`.
  - `dateOnOutsideTheYear`: `dateOn(1440, 2026-01-09) == null`.
- [ ] **Step 3: Run; they fail.** `--tests '*BanglaCalendarTest*'`
- [ ] **Step 4: Implement `BanglaCalendar.kt`.** `ranges` copies `set_month` (en year = year + 593; Y = 14 Apr; N = 14 Apr of en year + 1; table in spec 5.2). `currentYear`: Gregorian year − 593 from 14 April, else − 594. `dateOn`: the first range that contains `day`; day number = days from its start + 1.
- [ ] **Step 5: Run; they pass.** Then all tests.
- [ ] **Step 6: Commit** — "Add the Bangla calendar rule of the desktop app".

### Task 3: Make the m5 Bangla program

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/bangla/BanglaProgram.kt`
- Create: `app/android/src/test/resources/bangla/m5/bn1429.hds`, `bn1434.hds`, `bn1436.hds`, `bn1447.hds`, `bn1458.hds` (copies of the exports' `progam.hds`)
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/bangla/BanglaProgramTest.kt`

**Interfaces:**
- Consumes: Task 1 `Programs`, `Area.findShapes/planesAt/writeShape`, `AreaType`, `Glyph.shape()`; Task 2 `BanglaCalendar.ranges`; `HijriM5.digits` (shapes via `shape()`); `HijriDataException`.
- Produces:
  - `object BanglaM5 { val screen: ByteArray; fun base(): ByteArray /* copy */; val digits: Map<Char, Shape> /* from HijriM5.digits */ }`
  - `object BanglaProgram { fun make(base: ByteArray, digits: Map<Char, Shape>, year: Int, baseYear: Int = 1432, correctProgram13: Boolean = true): ByteArray }` — throws `HijriDataException` when a base year is not found.

- [ ] **Step 1: Copy the 5 test resources** and check them with `sha256sum`.
- [ ] **Step 2: Write the failing tests:**
  - `rebuildsTheExportsLikeTheDesktopApp`: for 1429, 1434, 1436, 1447, 1458: `make(BanglaM5.base(), BanglaM5.digits, year, correctProgram13 = false)` equals `bn<year>.hds` (`assertArrayEquals`).
  - `keepsTheBaseForTheSameYear`: `make(base, digits, 1432, correctProgram13 = false)` equals the base.
  - `correctsProgram13`: for 1432 and 1458: the result with `correctProgram13 = true` differs from the `false` result only inside the bitmaps of program 13's 3 text areas, and `findShapes` finds `year + 1` in each of them.
  - `year1499`: `make(base, digits, 1499)`: program 13's text areas show 1500; programs 1–12 show 1499.
  - `damagedBaseThrows`: clear the bitmap of program 1's first text area → `assertThrows(HijriDataException::class.java)`.
- [ ] **Step 3: Run; they fail.** `--tests '*BanglaProgramTest*'`
- [ ] **Step 4: Implement `BanglaProgram.make`.** For each program k (0..12) with range `ranges(year)[k]`: `HP`+18 = start at 00:00, `HP`+22 = end at 23:59:59 (wall-clock seconds); each `COUNTER` area: bytes 37–40 = start − 1 day at 00:00; each `TEXT` area: programs 1–12 find `baseYear` and write `year`; program 13 finds "1431" and writes `year + 1` when `correctProgram13`, else stays unchanged. New digits go into the planes that the old digits use (`planesAt`).
- [ ] **Step 5: Run; they pass.** Then all tests.
- [ ] **Step 6: Commit** — "Make the m5 Bangla program from a year".

### Task 4: English data and the card list

**Files:**
- Create: `app/android/src/main/resources/english/m5/screen.hds`, `progam.hds` (copies of the `english` export)
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/display/DisplayTab.kt`, `display/CardList.kt`, `english/EnglishM5.kt`
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/display/CardListTest.kt`, `english/EnglishM5Test.kt`

**Interfaces:**
- Produces:
  - `enum class DisplayTab(val label: String) { ENGLISH("English"), BANGLA("Bangla"), HIJRI("Hijri") }`
  - `class CardList(private val read: () -> String?, private val write: (String) -> Unit) { fun tabOf(cardName: String?): DisplayTab?; fun remember(cardName: String?, tab: DisplayTab) }` — stored as lines `<name>\t<TAB enum name>`; a blank or null name is ignored; unknown lines are skipped.
  - `fun cardNote(cardName: String?, tab: DisplayTab, known: DisplayTab?): CardNote?` with `data class CardNote(val text: String, val warning: Boolean)`.
  - `object EnglishM5 { val screen: ByteArray; val program: ByteArray }`

- [ ] **Step 1: Copy the 2 English resources** and check them.
- [ ] **Step 2: Write the failing tests:**
  - `CardListTest.remembersAndChanges`: `remember("WF1_1183677752", HIJRI)` then `tabOf` → HIJRI; `remember(…, BANGLA)` → BANGLA; a new `CardList` on the same stored text gives the same.
  - `CardListTest.ignoresBlankNames`: `remember(null, HIJRI)` and `remember("", HIJRI)` store nothing; `tabOf(null) == null`.
  - `CardListTest.damagedTextGivesEmptyList`: stored `"garbage\nWF1_x\tNOPE\n\t\t"` → `tabOf("WF1_x") == null`, no exception.
  - `CardListTest.notes`: `cardNote("WF1_1", HIJRI, HIJRI) == CardNote("Hijri display", false)`; `cardNote("WF1_1", BANGLA, HIJRI) == CardNote("This card last got the Hijri program.", true)`; `cardNote("WF1_1", HIJRI, null) == null`; `cardNote(null, HIJRI, HIJRI) == null`.
  - `EnglishM5Test.programIsTheExport`: `EnglishM5.program.size == 34034`, starts with `HC`; `HdsExport(EnglishM5.screen, EnglishM5.program)` does not throw.
- [ ] **Step 3: Run; they fail.**
- [ ] **Step 4: Implement** the 3 files.
- [ ] **Step 5: Run; they pass.** Then all tests.
- [ ] **Step 6: Commit** — "Add the English data and the card list".

### Task 5: The 3 tabs (view models and screens)

**Files:**
- Create: `app/android/src/main/kotlin/com/masjidclock/wfsender/DisplayViewModel.kt` (abstract base)
- Modify: `HijriViewModel.kt` (extends the base; no behavior change)
- Create: `EnglishViewModel.kt`, `BanglaViewModel.kt`, `bangla/BanglaInput.kt` (settings + log header)
- Create: `ui/DisplaySteps.kt` (the shared Connect, Time, and Send step cards, moved from `HijriScreen`), `ui/EnglishScreen.kt`, `ui/BanglaScreen.kt`
- Modify: `ui/HijriScreen.kt` (uses `DisplaySteps`), `hijri/HijriInput.kt` (move the KDoc of `dayNumber` from above `dayOneStart` to `dayNumber`)
- Test: `app/android/src/test/kotlin/com/masjidclock/wfsender/bangla/BanglaInputTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4; the current `HijriViewModel` flow (connection check, `cardName`, `timeStep`, `sendState`, `SendGate.APP`, normal form then `SafeSender`, `startChecks` refuses unless `Idle`).
- Produces:
  - `abstract class DisplayViewModel(app: Application, val tab: DisplayTab) : AndroidViewModel(app)` with the flows and methods that `HijriViewModel` has now (`connection`, `cardName`, `timeStep`, `sendState`, `startChecks`, `stopChecks`, `sendAgain`, `setTime`, `skipTime`), plus `val cardTab: StateFlow<DisplayTab?>` (the list entry of the connected card), `protected abstract fun canSend(): Boolean`, `protected abstract fun makeExport(): HdsExport` (throws `HijriDataException` → the damaged-data result), `protected abstract fun logHeader(export: HdsExport): List<String>`, and `fun send()`. After `SendResult.Success`, it calls `cardList.remember(cardName, tab)`. The card list uses `getSharedPreferences("cards", MODE_PRIVATE)`, key `list`; the same `CardList` instance logic in all 3 view models.
  - `class BanglaSettings(read, write) { fun load(today: LocalDate): String /* saved year text, else currentYear(today) */; fun saveYear(year: Int) }`; `fun banglaLogHeader(year: Int, programSize: Int): List<String>` → `["Display: m5, 384 × 32, progam.hds <size> bytes", "Bangla: year <year>, <first start yyyy-mm-dd> to <last end yyyy-mm-dd>"]`; English header → `["Display: m5, 384 × 64, progam.hds <size> bytes", "English program"]`.
  - Composables `ConnectStepCard(number, state, connection, cardName, note: CardNote?, sending)`, `TimeStepCard(...)`, `SendStepCard(...)` with the current Hijri content.

- [ ] **Step 1: Write the failing test** `BanglaInputTest`: `BanglaSettings` first use on 2026-10-20 → "1433"; save 1432 → load → "1432"; damaged saved value "14x2" → "1433"; `banglaLogHeader(1432, 101330)` → `["Display: m5, 384 × 32, progam.hds 101330 bytes", "Bangla: year 1432, 2025-04-14 to 2026-05-14"]` (program 13 end = 14 Apr 2026 + 30 days).
- [ ] **Step 2: Run; it fails.** `--tests '*BanglaInputTest*'`
- [ ] **Step 3: Implement `BanglaInput.kt`; run; it passes.**
- [ ] **Step 4: The view models.** Move the flow of `HijriViewModel` into `DisplayViewModel` without a change in behavior; `HijriViewModel` keeps its inputs and overrides the 3 abstract functions with its current export and log code. `EnglishViewModel`: `canSend() = true`, export `HdsExport(EnglishM5.screen, EnglishM5.program)`. `BanglaViewModel`: `yearText` flow, `setYearText` (saves only a valid year), `canSend()` = valid year, export `HdsExport(BanglaM5.screen, BanglaProgram.make(BanglaM5.base(), BanglaM5.digits, year))`.
- [ ] **Step 5: The screens.** `DisplaySteps.kt` holds the step 2–4 content of `HijriScreen` now; `ConnectStepCard` shows `note` under the `FoundLine` (`StatusLine(note.text, ok = !note.warning)`). `EnglishScreen`: steps 1 Connect, 2 Time, 3 Send. `BanglaScreen`: step 1 "Bangla year" (an `OutlinedTextField`, number keyboard, error "Enter the Bangla year, for example 1432."; a summary box: "The display shows: <d> <month> <year>" from `BanglaCalendar.dateOn(year, today)`, "The date changes every day at 00:00.", and the warning "Check the year: today is in <currentYear>." when `dateOn` is null or its year ≠ the entered year — program 13 counts as the next year), then Connect, Time, Send. Each screen: `LifecycleResumeEffect` start/stop checks, `BackHandler`/`KeepScreenOn` during a send, steps hidden after a result, "Share log" only after a failure — as `HijriScreen` now.
- [ ] **Step 6: Run all tests; compile.** `./gradlew :protocol:test :android:testDebugUnitTest :android:compileDebugKotlin`
- [ ] **Step 7: Commit** — "Add the English and Bangla tabs on a shared display view model".

### Task 6: Navigation, version, and README

**Files:**
- Modify: `AppViewModel.kt` (`Page`), `ui/MainScaffold.kt`, `MainActivity.kt`, `app/android/build.gradle.kts`, `app/README.md`

**Interfaces:**
- Consumes: Task 5 view models and screens.
- Produces: `enum class Page { ENGLISH, BANGLA, HIJRI, SEND, CREATE, TIME }` with `val isTab: Boolean` (the first 3); `AppViewModel.openTab(page)`, `openMenuPage(page)` (SEND, CREATE, TIME), `back()` (to the last tab); the last tab is saved in `getSharedPreferences("app", MODE_PRIVATE)`, key `lastTab` (first start: ENGLISH). `importPackage` still opens SEND.

- [ ] **Step 1: Navigation.** `ModalNavigationDrawer` with the title "Masjid Clock" and the items Send, Create, Time (icons as the current bottom bar). On a tab: the top bar has ≡ (opens the drawer) and the bottom bar has English | Bangla | Hijri. On a menu page: the top bar has a back arrow (`back()`), the Android back button also calls `back()`, and there is no bottom bar. During a send on any page, ≡, the back arrow, and the bottom bar are disabled. Titles: English → "English display", Bangla → "Bangla display", Hijri → "Hijri month", Send/Create/Time as now.
- [ ] **Step 2: Version** `versionCode = 11`, `versionName = "0.3.0"`. **README:** the 3 tabs, the menu, the Bangla tab steps, the card list note, and the manual tests: (a) send each tab to its card and compare the display with the desktop app's program; (b) Bangla on 13/14 April: the summary year; (c) connect the Hijri card on the Bangla tab: the warning shows; (d) the menu pages and the back arrow; (e) the app opens on the last tab.
- [ ] **Step 3: Build and test.** `./gradlew clean :protocol:test :android:testDebugUnitTest :android:assembleRelease` → all pass; `masjid-clock-0.3.0.apk` has SHA-256 `2fbf779f1b860193471a14f38b7a4455c9a1fa5c4c55e6ed031c1eba019f52ec`; delete `app/build/signing` after the check.
- [ ] **Step 4: Commit** — "Move Send, Create, and Time into a side menu; tabs English, Bangla, Hijri". Copy the APK to `/home/towfique/Documents/debug/`.
