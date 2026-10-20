package com.masjidclock.wfsender.bangla

import com.masjidclock.wfsender.hijri.HijriInput
import java.time.LocalDate

/** The texts of the Bangla tab (display-tabs spec section 5.1). */
object BanglaText {
    const val YEAR_ERROR = "Enter the Bangla year, for example 1432."
    const val CHANGE_TIME = "The date changes every day at 00:00."

    fun yearWarning(currentYear: Int) = "Check the year: today is in $currentYear."
}

/** The saved Bangla year. The first time, or if the saved value does not parse: the current Bangla year. */
class BanglaSettings(private val read: (String) -> String?, private val write: (String, String?) -> Unit) {

    fun load(today: LocalDate): String =
        read(YEAR)?.takeIf { HijriInput.parseYear(it) != null } ?: BanglaCalendar.currentYear(today).toString()

    fun saveYear(year: Int) = write(YEAR, year.toString())

    private companion object {
        const val YEAR = "year"
    }
}

/** The first lines of the send log of a Bangla program. */
fun banglaLogHeader(year: Int, programSize: Int): List<String> {
    val ranges = BanglaCalendar.ranges(year)
    return listOf(
        "Display: m5, 384 × 32, progam.hds $programSize bytes",
        "Bangla: year $year, ${ranges.first().start} to ${ranges.last().endInclusive}",
    )
}

/** What the Bangla summary box shows: the date on the display today (or null), and a warning (or null). */
data class BanglaSummary(val date: BanglaDate?, val warning: String?)

/**
 * The warning shows if [year] does not contain [today] (spec 5.1): also from 14 April to 14 May, when program 13
 * of [year] plays but today is already in the next year. Then the program of [year] stops on 14 May.
 */
fun banglaSummary(year: Int, today: LocalDate): BanglaSummary {
    val date = BanglaCalendar.dateOn(year, today)
    val warning = if (date == null || date.year != year) BanglaText.yearWarning(BanglaCalendar.currentYear(today)) else null
    return BanglaSummary(date, warning)
}

/** The Bangla year of the last successful send to a card. */
object BanglaCardValues {
    fun encode(year: Int): String = year.toString()

    /** The year text, or null if [text] is missing or not a valid year. */
    fun decode(text: String?): String? = text?.takeIf { HijriInput.parseYear(it) != null }
}
