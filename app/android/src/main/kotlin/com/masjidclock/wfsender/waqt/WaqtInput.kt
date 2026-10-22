package com.masjidclock.wfsender.waqt

import com.masjidclock.wfsender.hijri.Sun
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** The 6 prayers of the waqt tab, in the order of the display. */
object WaqtPrayers {
    val NAMES = listOf("Fajr", "Zuhr", "Asr", "Magrib", "Isha", "Jumu'ah")

    /** am/pm is fixed by the prayer (the desktop rule adds 12 hours to all prayers except Fajr). */
    fun period(prayer: Int): String = if (prayer == 0) "am" else "pm"
}

/** The inputs of the waqt tab: the 6 "Today" times (null = not set yet) and the changes. */
data class WaqtValues(val times: List<HourMinute?>, val changes: List<WaqtChange>)

/** The rules of the step "Prayer times" (spec section 3). */
object WaqtInput {
    private val DAY_TEXT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

    /** Changes that apply before [now] become the "Today" time of their prayer, and leave the list. */
    fun merged(values: WaqtValues, now: LocalDateTime): WaqtValues {
        val times = values.times.toMutableList()
        val later = mutableListOf<WaqtChange>()
        for (change in values.changes.sortedBy { WaqtSchedule.applyTime(it.date, it.prayer, it.time) }) {
            if (WaqtSchedule.applyTime(change.date, change.prayer, change.time).isAfter(now)) later += change
            else times[change.prayer] = change.time
        }
        return WaqtValues(times, later.sortedWith(compareBy({ it.prayer }, { it.date })))
    }

    /** All 6 times set and valid, no 2 changes of 1 prayer on 1 date, and no 2 prayers at the same time. */
    fun isValid(values: WaqtValues): Boolean =
        values.times.size == 6 && values.times.all { it != null && valid(it) } &&
            values.changes.all { it.prayer in 0..5 && valid(it.time) } &&
            values.changes.groupBy { it.prayer to it.date }.all { it.value.size == 1 } &&
            timeConflict(values) == null

    /**
     * The message if 2 prayers have the same clock time on a day (user, 2026-10-21: no 2 prayers have the same time),
     * else null: the "Today" times, then the times from each change date. am/pm as [WaqtSchedule.applyTime].
     * Zuhr and Jumu'ah can have the same time: on Friday, Jumu'ah replaces Zuhr (user, 2026-10-22).
     */
    fun timeConflict(values: WaqtValues): String? {
        if (values.times.size != 6 || values.times.any { it == null }) return null
        val current = values.times.map { it!! }.toMutableList()
        conflict(current)?.let { return "$it (today)." }
        for ((date, changes) in values.changes.groupBy { it.date }.toSortedMap()) {
            changes.forEach { if (it.prayer in 0..5) current[it.prayer] = it.time }
            conflict(current)?.let { return "$it (from ${date.format(DAY_TEXT)})." }
        }
        return null
    }

    private fun conflict(times: List<HourMinute>): String? {
        for (a in times.indices) for (b in a + 1 until times.size) {
            if (a == ZUHR && b == JUMUAH) continue
            if (clock(a, times[a]) == clock(b, times[b])) {
                return "${WaqtPrayers.NAMES[a]} and ${WaqtPrayers.NAMES[b]} have the same time"
            }
        }
        return null
    }

    private const val ZUHR = 1
    private const val JUMUAH = 5

    private fun clock(prayer: Int, time: HourMinute) = (if (prayer > 0 && time.hour < 12) time.hour + 12 else time.hour) * 60 + time.minute

    /** "+ Add a change": the day after the last row of [prayer], with its time. */
    fun nextRow(values: WaqtValues, prayer: Int, today: LocalDate): WaqtChange {
        val last = values.changes.filter { it.prayer == prayer }.maxByOrNull { it.date }
        return if (last != null) WaqtChange(last.date.plusDays(1), prayer, last.time)
        else WaqtChange(today.plusDays(1), prayer, values.times[prayer] ?: HourMinute(12, 0))
    }

    /** The summary under the step: today's Sunrise and Sunset, and the period of the schedule (if the inputs are valid). */
    fun summary(input: WaqtValues, now: LocalDateTime): List<String> {
        // The same values as the send: past changes are already "Today".
        val values = merged(input, now)
        val sun = Sun.dhaka(now.toLocalDate())
        val first = "Today: Sunrise ${sun.sunrise.hour}:%02d am, Sunset ${sun.sunset.hour - 12}:%02d pm (calculated)."
            .format(Locale.ROOT, sun.sunrise.minute, sun.sunset.minute)
        // Without changes, the period line is not shown (user, 2026-10-21).
        if (!isValid(values) || values.changes.isEmpty()) return listOf(first)
        val copies = WaqtSchedule.copies(values.times.map { it!! }, values.changes, now)
        return listOf(
            first,
            // The end is the last change date (user, 2026-10-22), not the end of the 3-day buffer.
            "The program covers ${copies.first().dateStart.format(DAY_TEXT)} to ${values.changes.maxOf { it.date }.format(DAY_TEXT)}.",
        )
    }

    private fun valid(time: HourMinute) = time.hour in 1..12 && time.minute in 0..59
}

/** The waqt inputs as 1 text, for the last values and the card values: "h:m,…;yyyy-mm-dd|prayer|h:m;…". */
object WaqtCodec {
    fun encode(values: WaqtValues): String =
        values.times.joinToString(",") { it?.let { t -> "${t.hour}:${t.minute}" } ?: "-" } + ";" +
            values.changes.joinToString(";") { "${it.date}|${it.prayer}|${it.time.hour}:${it.time.minute}" }

    /** The values, or null if [text] is missing or damaged. */
    fun decode(text: String?): WaqtValues? {
        val parts = text?.split(";") ?: return null
        val times = parts[0].split(",")
        if (times.size != 6) return null
        val parsedTimes = times.map { if (it == "-") null else time(it) ?: return null }
        val changes = parts.drop(1).filter { it.isNotEmpty() }.map {
            val f = it.split("|")
            if (f.size != 3) return null
            val date = try {
                LocalDate.parse(f[0])
            } catch (e: DateTimeParseException) {
                return null
            }
            val prayer = f[1].toIntOrNull()?.takeIf { p -> p in 0..5 } ?: return null
            WaqtChange(date, prayer, time(f[2]) ?: return null)
        }
        return WaqtValues(parsedTimes, changes)
    }

    private fun time(text: String): HourMinute? {
        val f = text.split(":")
        if (f.size != 2) return null
        val h = f[0].toIntOrNull() ?: return null
        val m = f[1].toIntOrNull() ?: return null
        return if (h in 1..12 && m in 0..59) HourMinute(h, m) else null
    }
}

/** The first lines of the send log of a waqt program. */
fun waqtLogHeader(profile: String, values: WaqtValues, copies: Int, programSize: Int): List<String> = listOf(
    "Display: m5, 384 × 128, progam.hds $programSize bytes",
    "Profile: $profile",
    "Waqt: $copies copies, ${values.changes.size} changes",
)
