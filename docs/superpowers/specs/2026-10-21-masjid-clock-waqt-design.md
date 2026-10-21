# Masjid Clock: the Waqt tab — design

Date: 2026-10-21. Branch: `hijri-on-phone`. App: Masjid Clock 0.4.2. Builds on
`docs/superpowers/specs/2026-10-20-masjid-clock-display-tabs-design.md` (tabs, wizard, card list, card values).

## 1. Intent

The bottom display of a masjid clock stack shows the prayer times: 8 rows in each of the 3 sections (Fajr, Zuhr,
Asr, Magrib, Isha, Jumu'ah, Sunrise, Sunset). An operator programs it about 1 time each month, with the jamaat
times for the coming weeks; the times change on some dates within that period (user, 2026-10-21). Only the times
change, never the names or the layout. Today this needs the desktop app and HD2020 on a PC. The Waqt tab lets the
operator do it with only a phone.

## 2. Goals, non-goals, anti-goals

**Goals**

- A 4th tab **Waqt** after Hijri (the bottom of the stack). The same wizard as the other tabs.
- The program is the same, byte for byte, as what the desktop app (`m5/m5_waqt.py`) + HD2020 make for the same
  inputs on the same day.

**Non-goals**

- Models other than m5 (384 × 128). Locations other than Dhaka.
- A limit on the change dates (user, 2026-10-21: 30 days is only a convention to keep the program small; 60 days may
  be used).
- The desktop app's habit of saving the calculated Sunrise/Sunset changes into its settings: the app keeps only the
  operator's inputs.

**Anti-goals**

- A wrong time on the display at any moment of the period (a wrong window, a wrong digit, a change on the wrong day).
- A change in the behavior of the other tabs.

## 3. The page (approved on a mockup, 2026-10-21)

The wizard has 4 steps: **1 Connect**, **2 Prayer times**, **3 Set the time**, **4 Send** (as the other tabs; the
inputs after Connect so that a card's saved values show first).

**Step 2 "Prayer times"** (the structure of `prayer_timings_screen.py` on the branch `uv-integration`, user
decision):

- 6 cards in a list that scrolls: Fajr, Zuhr, Asr, Magrib, Isha, Jumu'ah.
- In each card:
  - the first row: "Today" and the current time (no delete);
  - 1 row for each change of that prayer: a date (date picker) and a time (time picker), with ✕ to delete;
  - "+ Add a change": a new row with the next day after the last row, and the last row's time filled in.
- am/pm is fixed by the prayer: Fajr "am", the others "pm" (the desktop rule `get_apply_date` adds 12 hours to all
  prayers except Fajr). The time picker shows and returns 12-hour times; the hour is stored as 1–12, as the desktop
  app on `master` does (my call: `uv-integration` stores 12 as 0, and `join_time` would then show "0:30"; the apply
  time is the same either way).
- When the step opens, changes whose apply time is before now become the "Today" time of their prayer, and they
  leave the list (as `_get_prayer_schedule_data` on `uv-integration`).
- A summary at the bottom, outside the scroll: "Today: Sunrise <h:mm am>, Sunset <h:mm pm> (calculated)." and the
  period of the program (section 4.3).
- Next needs all 6 times, and no 2 changes of 1 prayer on the same date.

The inputs are remembered for each card and as the last values, as on the Hijri tab.

## 4. The program (from the desktop app)

### 4.1 Inputs → the time table

`m5/m5_waqt.py`, `waqt_utils.py`:

- `times`: the 6 jamaat times + 2 slots for Sunrise and Sunset (index 6 and 7).
- `changes`: `[date "dd/mm/yy", index, (h, m)]`.
- The apply time of a change: `get_apply_date`: the date − 1 day, at the time (+12 h for index > 0 and h < 12),
  + 30 minutes.
- Sunrise/Sunset: for each day from yesterday to the apply date of the last change (+ 1 day), the Dhaka sunrise
  and sunset (`praytimes`); a change (index 6 or 7) on each day where the value differs from the day before.
- All changes sorted by apply time; then the table of periods: each period has a start time and the 8 times.

### 4.2 The time table → programs

`m5_waqt.new_program`: 1 copy of the template program `salah` for each period, split at midnight: a first day with
a time window (start time to 23:59), whole days with a date window only, a last day from 00:00 to the end time.
The 8 times go into the 3 `waqt-time` text areas (placeholders `11:11` … `88:88`, right-aligned to 5 characters).
In the desktop app, the last period ends 30 days after today (`get_end_date`).

### 4.2a Differences from the desktop app (user decisions, 2026-10-21)

The app copies 4.1 and 4.2 exactly, with these 2 changes at the end of the schedule:

1. **The end of the program.** With operator changes: 23:59:59 on the 3rd day after the date of the last operator
   change (a 3-day buffer). Without operator changes: 30 days after today, at the current time (as the desktop app).
   Then the display is blank: a feature ("instead of showing a wrong time, it shows nothing"). Reason: the desktop
   end (today + 30) is before the start of the last period when a change is more than 30 days ahead; the desktop
   code then makes a broken last period and an extra copy on the end date that plays at the same time as the
   correct copy (seen with `get_m5_waqt_xml` and a change in 40 days: 87 programs, a copy "20.11/00:00 -- 20.11/21:08"
   with the last times). The calculated Sunrise/Sunset changes do not count as operator changes.
2. **Sunrise and Sunset** are calculated for each day up to the end of the program (the desktop app: up to the day
   after the last change), so that the buffer days show correct values.

Kept as in the desktop app: the first copy of a period that starts within a day ends at 23:59:00
(`WHOLE_DAY_IN_SEC = 86340`), so the last minute of that day has no program (user: no prayer is at 23:59).

All dates and times are wall-clock times in fixed UTC+6, as on the other tabs (my call); "today" and "now" are the
phone's local date and time.

### 4.3 What the app must learn from HD2020 exports

- How the program copies are stored (the `HC` table already holds N programs, see Bangla), and where the **time
  window** of a program is (`specifedTimeEnabled`, `timeStart`, `timeEnd`) in the `HP` block.
- The digit shapes of the `waqt-time` text (a different font and color from Hijri) and the position of each time.
- Whether the file size changes with the number of programs (it must: 1 `HP` block for each copy).

The summary's "period" line: "The program covers <first day> to <last day>." from the table (my call: the dates of
the first and the last program).

## 5. Data needed (before the plan)

The kit and the exports are in `/home/towfique/Documents/debug/waqt-kit/`. The layout (the program `salah` with
the 8 names) is the same at every masjid (user: only the times change).

A kit, as for the temperature: a script on the laptop, with only the Python standard library and its own copy of
the m5 waqt template, makes the desktop app's program for fixed inputs and registers it in HD2020; the operator
exports each one. Only the standard library: the kit ports the needed part of `m5_waqt.py`, `waqt_utils.py`, and the
Sunrise/Sunset calculation of `praytimes.py`, and the analysis checks the kit's XML against the desktop app's own XML
for the same inputs (run here with the repository's Python code).

All exports on the same day:

| Folder | Inputs | What it gives |
|---|---|---|
| `w0` | 6 times, no changes | The base: a few programs (the Sunrise/Sunset changes of yesterday and today) |
| `w0-b` | the same as `w0`, again | The bytes that change each time |
| `w-digits` | 6 times that use all digits 0–9 | The digit shapes |
| `w1` | 1 change in 10 days | More programs, with time windows |
| `w1-time` | the same as `w1`, the change time + 1 hour | The time window field |
| `w3` | 3 changes on 3 dates, 2 prayers | A longer series of programs |

Analysis (as for Bangla): rebuild every export byte for byte from the template program block and the digits before
the plan is written.

## 6. Tests

- The time table: golden values from the desktop app code (`get_m5_waqt_xml` with fixed "today" values) for a set of
  input cases: no changes, 1 change, several, a change on the first day, Fajr (am) and the others (pm), a 12:xx
  time. For the end of the schedule (4.2a), the expected values follow the 2 rules, and a test covers a change
  more than 30 days ahead (no extra copy, the end 3 days after it).
- The program: every export rebuilt byte for byte.
- The inputs: the 12-hour times, the order and the "Today" merge of past changes, the remembered values.
- Manual test on the waqt display: the times change at the right moments (a change 30 minutes after the prayer on
  the day before), and Sunrise/Sunset change each day.

## 7. Left to the builder

- The code structure (it must fit the shared DisplayViewModel and the wizard).
- How the data is stored in the app.

## 8. Results of the analysis (2026-10-21)

Exports: `/home/towfique/Documents/debug/waqt-kit/exports/` (w0, w0-b, w-digits, w1, w1-time, w3; inputs in each
`inputs.json`). Experiment: `/home/towfique/Documents/debug/waqt-kit/experiment/waqt_rebuild.py`.

- **All 6 exports are rebuilt byte for byte** from the first program block of `w0`, the digit cells, and the date
  and time windows of each copy (7, 7, 7, 29, 29, 35 programs).
- `HC`: 27 bytes + 1 absolute 4-byte offset for each program (as Bangla); the count at byte 4.
- Each copy is the same `HP` block (23,512 bytes, 6 areas) except: `HP`+5 the time window flag, `HP`+6–8 the start
  time and `HP`+9–11 the end time (3-byte big-endian seconds after midnight), `HP`+17 the date flag, `HP`+18–21 the
  start date at 00:00 and `HP`+22–25 the end date + 86,399 s (wall-clock seconds, as Bangla), and the time bitmaps.
  A copy without a time window keeps flag 0, start 0, end 86,399 (the template values).
- The 3 `waqt-time` areas (x = 81, 209, 337; 35 × 128; bitmap stride 8 bytes, the 3-plane row layout as Hijri; the
  text is red, plane 0): 8 rows of 16 pixels, right-aligned text " h:mm" (`pad`): digit cells of 7 × 16 pixels at
  columns 20 (hour tens: blank or "1"), 27 (hour units), 38 and 45 (minutes); the colon (columns 34–37) never
  changes. The names areas never change.
- The digit cells (all 10 digits and the blank) are the same bitmap at every position: 0 conflicts in 114 copies.
