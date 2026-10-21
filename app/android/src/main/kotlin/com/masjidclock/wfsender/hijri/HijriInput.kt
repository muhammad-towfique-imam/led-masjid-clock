package com.masjidclock.wfsender.hijri

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** The Hijri month names of the desktop app (hijri_utils.get_hijri_months). */
object HijriMonths {
    val NAMES = listOf(
        "Muharram", "Safar", "Rabiul Awl", "Rabiul Akhr", "Juma Ula", "Juma Akhir",
        "Rajab", "Shaban", "Ramadan", "Shawwal", "Zul-qaadh", "Zul-hijjah",
    )
}

/** The checks of the 3 inputs of the Hijri page (spec section 4). */
object HijriInput {
    const val YEAR_ERROR = "Enter the Hijri year, for example 1448."
    const val DATE_WARNING = "Check the date: it is far from today."

    private val FOUR_DIGITS = Regex("^[0-9]{4}$")

    /** The year, or null. Only 4 ASCII digits from 1400 to 1499: toIntOrNull alone accepts other digit scripts. */
    fun parseYear(text: String): Int? {
        if (!FOUR_DIGITS.matches(text)) return null
        return text.toInt().takeIf { it in 1400..1499 }
    }

    /** True if [date] is more than 30 days before or after [today]. */
    fun dateIsFar(date: LocalDate, today: LocalDate): Boolean = abs(ChronoUnit.DAYS.between(today, date)) > 30

    /** The Material 3 date picker uses UTC midnight. The phone's time zone must not change the day. */
    fun dateFromPicker(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** When the display shows day 1: 1 day after [counterStart], on the month start date. */
    fun dayOneStart(counterStart: LocalDateTime): LocalDateTime = counterStart.plusDays(1)

    /**
     * The day number that the display shows at [now]: the whole days since [counterStart] (no "+ 1": confirmed on
     * the display on 2026-10-20). So the date changes every day at the time of [counterStart]. Null before day 1.
     */
    fun dayNumber(counterStart: LocalDateTime, now: LocalDateTime): Int? {
        val seconds = ChronoUnit.SECONDS.between(counterStart, now)
        val days = seconds / 86_400
        return if (seconds < 0 || days < 1) null else days.toInt()
    }
}

/** What the lower half of the Hijri display shows. */
enum class LowerHalf(val label: String, val logText: String) {
    LINE("Divider line", "divider line"),
    TEMPERATURE("Outside temperature", "outside temperature"),
    NOTHING("Nothing", "nothing"),
}

/** The inputs of the Hijri page. [month] is 1..12 or null; [yearText] is the text of the year field. */
data class HijriValues(
    val monthStart: LocalDate,
    val month: Int?,
    val yearText: String,
    val lowerHalf: LowerHalf = LowerHalf.LINE,
)

/** The saved inputs. A value that does not parse is ignored, and the default is used. */
class HijriSettings(private val read: (String) -> String?, private val write: (String, String?) -> Unit) {

    fun load(today: LocalDate): HijriValues {
        val date = read(DATE)?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        } ?: today
        val month = read(MONTH)?.toIntOrNull()?.takeIf { it in 1..12 }
        val year = read(YEAR)?.takeIf { HijriInput.parseYear(it) != null } ?: ""
        // Before version 0.3.2, a checkbox: showDivider "false" = nothing.
        val lower = LowerHalf.entries.firstOrNull { it.name == read(LOWER_HALF) }
            ?: if (read(DIVIDER) == "false") LowerHalf.NOTHING else LowerHalf.LINE
        return HijriValues(date, month, year, lower)
    }

    fun saveDate(date: LocalDate) = write(DATE, date.toString())

    fun saveMonth(month: Int) = write(MONTH, month.toString())

    fun saveYear(year: Int) = write(YEAR, year.toString())

    fun saveLowerHalf(lower: LowerHalf) = write(LOWER_HALF, lower.name)

    private companion object {
        const val DATE = "monthStart"
        const val MONTH = "month"
        const val YEAR = "year"
        const val DIVIDER = "showDivider"
        const val LOWER_HALF = "lowerHalf"
    }
}

/** The first lines of the send log of a Hijri program (spec section 6). */
fun hijriLogHeader(
    month: String,
    year: Int,
    monthStart: LocalDate,
    counterStart: LocalDateTime,
    programSize: Int,
    lowerHalf: LowerHalf,
): List<String> = listOf(
    "Display: m5, 384 × 32, progam.hds $programSize bytes",
    "Hijri: $month $year, month start $monthStart, counter start " +
        counterStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
        ", lower half: " + lowerHalf.logText,
)
