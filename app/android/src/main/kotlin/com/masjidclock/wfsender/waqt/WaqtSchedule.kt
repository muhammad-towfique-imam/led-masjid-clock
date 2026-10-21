package com.masjidclock.wfsender.waqt

import com.masjidclock.wfsender.hijri.Sun
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A time as the desktop app stores it: a 12-hour hour (1–12, Sunrise/Sunset 0–11) and the minute. */
data class HourMinute(val hour: Int, val minute: Int) {
    /** The display text: "h:mm", right-aligned to 5 characters (waqt_utils.join_time and pad). */
    fun text(): String = String.format(Locale.ROOT, "%d:%02d", hour, minute).padStart(5)
}

/** A change of a jamaat time: from [date] (see [WaqtSchedule.applyTime]), prayer [prayer] (0..5) has [time]. */
data class WaqtChange(val date: LocalDate, val prayer: Int, val time: HourMinute)

/**
 * 1 copy of the program `salah`: it plays from [dateStart] to [dateEnd], within the time window [timeStart] to
 * [timeEnd] (seconds after midnight), or the whole days if both are null. [times] are the 8 display texts.
 */
data class WaqtCopy(
    val dateStart: LocalDate,
    val dateEnd: LocalDate,
    val timeStart: Int?,
    val timeEnd: Int?,
    val times: List<String>,
)

/** The time table and the program copies of the desktop app (m5/m5_waqt.py, waqt_utils.py; spec 4.1, 4.2, 4.2a). */
object WaqtSchedule {
    private const val WHOLE_DAY_IN_SEC = 86_340
    private const val SUNRISE = 6
    private const val SUNSET = 7

    /** get_apply_date: the date − 1 day, at the time (+12 h for prayers after Fajr before 12:00), + 30 minutes. */
    fun applyTime(date: LocalDate, prayer: Int, time: HourMinute): LocalDateTime {
        val hour = if (prayer > 0 && time.hour < 12) time.hour + 12 else time.hour
        return date.atTime(hour, time.minute).minusDays(1).plusMinutes(30)
    }

    /**
     * The copies for the 6 jamaat [times] and the operator's [changes] at [now]. [desktopEnd] true: exactly the desktop
     * app (the end 30 days after now; Sunrise/Sunset up to the day after the last change). False (the app): with
     * changes, the end is 23:59:59 on the date of the last change + 3 days; without, now + 30 days; Sunrise/Sunset are
     * calculated up to the end (user decisions, 2026-10-21).
     */
    fun copies(times: List<HourMinute>, changes: List<WaqtChange>, now: LocalDateTime, desktopEnd: Boolean = false): List<WaqtCopy> {
        require(times.size == 6) { "6 jamaat times" }
        val end = when {
            desktopEnd || changes.isEmpty() -> now.plusDays(30)
            else -> changes.maxOf { it.date }.plusDays(3).atTime(23, 59, 59)
        }
        val all = changes.sortedBy { applyTime(it.date, it.prayer, it.time) }.toMutableList()
        var baseDate = now.minusDays(1)
        val sunDays = if (desktopEnd) {
            val diff = if (all.isEmpty()) 0L else Math.floorDiv(Duration.between(baseDate, applyTime(all.last())).seconds, 86_400L)
            diff + 2
        } else {
            ChronoUnit.DAYS.between(baseDate.toLocalDate(), end.toLocalDate()) + 1
        }
        var previousRise: HourMinute? = null
        var previousSet: HourMinute? = null
        for (i in 0 until sunDays) {
            val date = baseDate.plusDays(i).toLocalDate()
            val sun = Sun.dhaka(date)
            val rise = HourMinute(sun.sunrise.hour, sun.sunrise.minute)
            val set = HourMinute(sun.sunset.hour - 12, sun.sunset.minute)
            if (rise != previousRise) {
                all += WaqtChange(date, SUNRISE, rise)
                previousRise = rise
            }
            if (set != previousSet) {
                all += WaqtChange(date, SUNSET, set)
                previousSet = set
            }
        }
        all.sortBy { applyTime(it) }
        if (all.isNotEmpty()) baseDate = applyTime(all.first().date, 0, HourMinute(0, 0)).toLocalDate().minusDays(1).atStartOfDay()

        val current = (times + HourMinute(0, 0) + HourMinute(0, 0)).toMutableList()
        val table = mutableListOf(baseDate to current.toList())
        for (change in all) {
            current[change.prayer] = change.time
            table += applyTime(change) to current.toList()
        }

        val out = mutableListOf<WaqtCopy>()
        for ((i, row) in table.withIndex()) {
            val texts = row.second.map { it.text() }
            val (dateStart, timeStart) = breakDate(row.first)
            val (dateEnd, timeEnd) = breakDate(if (i + 1 < table.size) table[i + 1].first.minusSeconds(1) else end)
            if (dateStart == dateEnd) {
                out += WaqtCopy(dateStart, dateEnd, timeStart, timeEnd, texts)
            } else {
                if (timeStart != 0) out += WaqtCopy(dateStart, dateStart, timeStart, WHOLE_DAY_IN_SEC, texts)
                if (ChronoUnit.DAYS.between(dateStart, dateEnd) > 1) {
                    out += WaqtCopy(dateStart.plusDays(1), dateEnd.minusDays(1), null, null, texts)
                }
                out += WaqtCopy(dateEnd, dateEnd, 0, timeEnd, texts)
            }
        }
        return out
    }

    private fun applyTime(change: WaqtChange) = applyTime(change.date, change.prayer, change.time)

    private fun breakDate(time: LocalDateTime): Pair<LocalDate, Int> =
        time.toLocalDate() to (time.hour * 3600 + time.minute * 60 + time.second)
}
