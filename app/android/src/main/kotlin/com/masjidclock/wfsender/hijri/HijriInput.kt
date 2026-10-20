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
}

/** The inputs of the Hijri page. [month] is 1..12 or null; [yearText] is the text of the year field. */
data class HijriValues(val monthStart: LocalDate, val month: Int?, val yearText: String)

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
        return HijriValues(date, month, year)
    }

    fun saveDate(date: LocalDate) = write(DATE, date.toString())

    fun saveMonth(month: Int) = write(MONTH, month.toString())

    fun saveYear(year: Int) = write(YEAR, year.toString())

    private companion object {
        const val DATE = "monthStart"
        const val MONTH = "month"
        const val YEAR = "year"
    }
}

/** The first lines of the send log of a Hijri program (spec section 6). */
fun hijriLogHeader(
    month: String,
    year: Int,
    monthStart: LocalDate,
    counterStart: LocalDateTime,
    programSize: Int,
): List<String> = listOf(
    "Display: m5, 384 × 32, progam.hds $programSize bytes",
    "Hijri: $month $year, month start $monthStart, counter start " +
        counterStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
)
