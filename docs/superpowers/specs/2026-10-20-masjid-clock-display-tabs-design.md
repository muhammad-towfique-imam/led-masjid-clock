# Masjid Clock: English, Bangla, and Hijri tabs — design

Date: 2026-10-20. Branch: `hijri-on-phone`. App: Masjid Clock 0.2.6 (`com.cyanwarelabs.masjidclock.app`), code in `app/`.
Builds on: `docs/superpowers/specs/2026-10-20-masjid-clock-hijri-design.md` (the Hijri tab, done and tested on the
card).

## 1. Intent

A masjid clock is a stack of separate LED displays on a pillar with 3 sides. Each display has its own Huidu card and
its own WiFi. From top to bottom (photo of 2026-01-09): a logo, **English** (date, time, weekday), **Bangla** (Bangla
date), **Hijri** (Hijri date, with a dashed divider line), and the prayer times. Each display shows its content on
3 sections of 128 pixels, 1 for each side.

The app must let a person at a masjid without a laptop set up the English, Bangla, and Hijri displays with only a
phone. The app becomes "a remote control for the stack": 1 tab for each display, in the order of the stack. The
package tools (Send, Create) and the clock tool (Time) become secondary.

## 2. Goals, non-goals, anti-goals

**Goals**

- The bottom bar has 3 tabs, in the order of the stack: **English | Bangla | Hijri**.
- Send, Create, and Time move into a side menu (≡ at the top left), with the items **Send**, **Create**, **Time**.
  (User decision; mockup option A.)
- English tab: sends the stored English program. No input. (User decision.)
- Bangla tab: 1 input, the Bangla year. (User decision.)
- Hijri tab: as now (0.2.6).

**Non-goals (this version)**

- The logo and the prayer-time (waqt) displays. (User decision: later.)
- Display models other than m5. (Decided for Hijri; the same here.)
- Bangla month or day inputs: the 13 date ranges come from the year (the desktop app's rule).

**Anti-goals**

- A Bangla program that differs from what the desktop app + HD2020 make for the same year (wrong month ranges, wrong
  day number, wrong year text). The date on a masjid display must be correct.
- A change in the behavior of the Send, Create, Time, or Hijri pages, except where they open from.
- A probe after a send (flicker rule, `docs/protocol.md` section 4.1).

## 3. Navigation

- The top app bar has a ≡ button at the left. It opens a side panel (Material 3 modal navigation drawer) with the
  title "Masjid Clock" and 3 items: **Send**, **Create**, **Time** (the current pages, unchanged).
- A page from the menu opens as a full page with a back arrow in the top bar. The back arrow and the Android back
  button return to the tab that was open.
- The bottom bar shows only English, Bangla, and Hijri. It is hidden while a menu page is open (my call: a menu page
  is a separate tool).
- The app opens on the last tab that was used; the first time, on English (my call).
- "Open with" an `.hdpkg` file opens the Send page, as now.
- While a send runs (on any page), the bottom bar, the ≡ button, and the back arrow are disabled, as the bottom bar
  is now.

## 4. The English tab

3 steps, with the same content and behavior as steps 2–4 of the Hijri tab:

1. Connect to the display WiFi.
2. Set the time (optional). The English display shows the card clock, so this step matters most here.
3. Send.

The program is the HD2020 export of `m5-english.xml` (384 × 64, 1 program, only `time` elements that the card
draws). The desktop app sends this template without a change (`m5/m5_english.py`), so the app stores 1 export and
sends it without a change.

## 5. The Bangla tab

### 5.1 The page

4 steps, like the Hijri tab:

1. **Bangla year:** a number field. The summary box under it shows the date that the display shows now, for
   example "The display shows: 25 Poush 1432", and "The date changes every day at 00:00." (my guess from the
   template: the counters start at 00:00; to confirm with the exports).
2. Connect to the display WiFi. 3. Set the time. 4. Send. (The same as the Hijri tab.)

- **Year:** 4 ASCII digits (the same check as the Hijri year; my call: 1400 to 1499).
- **Default:** the last saved year; the first time, the current Bangla year by the desktop app's rule: the Bangla
  year starts on 14 April, and Bangla year = Gregorian year − 593 from 14 April, else − 594 (`bangla_utils.set_month`:
  `en_year = bn_year + 593`, year start = 14 April of `en_year`).
- **A warning** if the year does not contain today (my call): "Check the year: today is in <current year>."
  The person can still send.
- The app remembers the year, like the Hijri inputs.

### 5.2 What the program contains (from `m5-bangla.xml` and `bangla_utils.py`)

- 13 programs: Baishakh, Joishtha, Asharh, Shrabon, Bhadro, Ashshin, Kartik, Augrohayon, Poush, Magh, Falgun,
  Choitro, then Baishakh of the next year. Each program plays only between its `dateStart` and `dateEnd`
  (`specifedDateEnabled="1"`).
- Each program has, in each of the 3 sections: a month-and-year text area (`s1-month-year`…), a weekday area
  (`s1-week`, a `time` element that the card draws), and a day counter (`s1-day`, a `timing` element).
- The date ranges (`set_month`), with `en_year = bn_year + 593`, `Y = 14 April en_year`, `N = 14 April en_year + 1`:

  | Program | Start | End |
  |---|---|---|
  | 1 | Y | start + 30 days |
  | 2–6 | previous end + 1 day | start + 30 days |
  | 7–10 | previous end + 1 day | start + 29 days |
  | 11 | previous end + 1 day | N − 31 days |
  | 12 | N − 30 days | N − 1 day |
  | 13 | previous end (program 12's end, N − 1 day) + 1 day = N | start + 30 days |

  The Python code applies this as written, with 1 known oddity: it calculates program 13 after program 12 and uses
  program 12's end. The app copies the Python behavior exactly.
- The counter start of each program: its start date − 1 day, at the template time (00:00 in the template).
- The year: the text "1430" in the template becomes the year; in the 13th program, the year + 1.
- `dateStart`/`dateEnd` are written as `int(start_date.timestamp())` on a PC in Bangladesh: local midnight
  (UTC+6) as Unix seconds. How HD2020 writes these into `progam.hds` (the `HP` block) must be found from the
  exports.

### 5.3 How the app makes the program

The same method as Hijri: a stored HD2020 export is the base; the app writes the year digits into the 39 text areas
(13 programs × 3), the date range into each of the 13 `HP` blocks, and the counter start into the 39 day counters.
The exact byte positions come from the exports (section 6). If the format is not a simple patch (for example, if the
date range changes the file size), the builder stops and reports.

## 5A. Which display gets which program (the card list)

The app operates many clocks, so it keeps a list of cards: card name → the tab (English, Bangla, or Hijri) of the
last successful send to that card. (User decision.)

- **After a successful send** on a tab, the app saves "this card name → this tab" (a new entry, or a change of an
  existing entry). The list stays after the app closes. A failed send does not change the list.
- **In the step "Connect"**, under the card name, the app shows what the list knows about the connected card
  (user decision, 2026-10-20: a warning there is sufficient; no question before the send):
  - The card is in the list for this tab: "Hijri display" (my call: a short confirmation in the normal color).
  - The card is in the list for a different tab: a warning in the error color, for example
    "This card last got the Hijri program." The person can still send. After a successful send, the entry
    changes to the new tab.
  - The card is not in the list, or the card did not give its name: nothing more shows.
- The list has no page of its own in this version (my call). The Send, Create, and Time pages do not use it.

## 6. Data needed from HD2020 (before the plan)

**The order of the work:** (1) the export kit, (2) the user makes the exports, (3) the analysis: rebuild every
Bangla export from 1 base byte for byte, (4) the implementation plan, (5) the code. The plan is written only after
step 3 confirms the method; if step 3 fails, the work stops and the user decides.

**The cards (user, 2026-10-20):** English `WF2_f412faca189c`, Bangla `WF1_1200423879`, Hijri `WF1_1183677752`
(names at 1 masjid). The card type in the templates (139, 116) does not matter: the m5 Hijri program (type 139)
also worked on a WF4 card (`WF4_c04e301c49d4`), and the app sent a program to `WF2_f412faca189c` in the first field
test. So the exports are made from the templates as they are.

**The time zone of the Bangla date tests (my call):** fixed UTC+6, as for Hijri. The only Bangladesh summer time
(2009) is in the Bangla year 1416, which nobody will send again.

The user makes these exports with the desktop app and HD2020, on 1 day, with the export kit. The kit must copy the
XML by the template file name, not "the newest XML" (the fault of the Hijri kit):
`m5-english` → `2024_09_10_11_55_39_{5be52456-54c5-4b78-a494-8af3b9e4b388}.xml`,
`m5-bangla` → `2023_12_30_21_45_32_{5c7f2d72-d6bf-4fb8-9175-c171a88d2299}.xml`.

| Folder | Program | What it gives |
|---|---|---|
| `english` | English (m5) | The English program |
| `bn1429` | Bangla 1429 | Digits 1, 4, 2, 9 (+ 1430 in program 13: 3, 0) |
| `bn1432` | Bangla 1432 | The year of the photo; digits 3, 2 |
| `bn1434` | Bangla 1434 | Digits 4, 5 (1435); a range across 29 Feb 2028 |
| `bn1436` | Bangla 1436 | Digits 6, 7 (1437) |
| `bn1447` | Bangla 1447 | Digits 4, 7, 8 (1448) |
| `bn1458` | Bangla 1458 | Digits 5, 8, 9 (1459); a range across 29 Feb 2052 |

Analysis (my call, as for Hijri): compare the exports byte by byte, find the year, date, and counter fields, and
rebuild every Bangla export from 1 base byte for byte before the plan is written. The plan then only implements a
confirmed method.

## 7. Tests

- English: the sent program equals the stored export.
- Bangla: rebuild all 6 Bangla exports byte for byte from 1 base; the 13 date ranges match the Python rule for
  every year from 1400 to 1499 (golden values from `bangla_utils.set_month`, as for Maghrib); the current Bangla
  year on 13 and 14 April; the summary date on sample days (for example 9 Jan 2026 → "25 Poush 1432", from the
  photo).
- Navigation: the existing tests still pass; manual check of the menu, the back arrow, and the last tab.
- The card list: save after a success only; the 3 cases of the text under the card name (same tab, other tab, unknown or no name); the list stays after a restart; a damaged saved list gives an empty list.
- Manual test on the Bangla and English cards before a release.

## 8. Left to the builder

- The code structure, and how to share the WiFi, time, and send steps between the 3 tabs (they are the same;
  a shared composable and a shared helper are welcome, if the Hijri behavior does not change).
- How the English and Bangla data are stored (as for Hijri).
- Fix on the way: in `hijri/HijriInput.kt`, the KDoc of `dayNumber` is above `dayOneStart`; move it to `dayNumber`.
