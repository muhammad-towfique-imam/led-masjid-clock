package com.masjidclock.wfsender.bangla

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A date on the Bangla display: for example 25 Poush 1432. */
data class BanglaDate(val day: Int, val month: String, val year: Int)

/** The Bangla calendar rule of the desktop app (bangla_utils.set_month). */
object BanglaCalendar {
    /** The 13 programs of the m5 Bangla template; the 13th is Baishakh of the next year. */
    val MONTHS = listOf(
        "Baishakh", "Joishtha", "Asharh", "Shrabon", "Bhadro", "Ashshin", "Kartik",
        "Augrohayon", "Poush", "Magh", "Falgun", "Choitro", "Baishakh",
    )

    /** The 13 date ranges of [year], as set_month calculates them (the ranges include both ends). */
    fun ranges(year: Int): List<ClosedRange<LocalDate>> {
        val yearStart = LocalDate.of(year + 593, 4, 14)
        val nextStart = yearStart.plusYears(1)
        val out = ArrayList<ClosedRange<LocalDate>>(13)
        var end = yearStart
        for (i in 1..13) {
            val start: LocalDate
            when (i) {
                1 -> {
                    start = yearStart
                    end = start.plusDays(30)
                }
                in 2..6, 13 -> {
                    start = end.plusDays(1)
                    end = start.plusDays(30)
                }
                in 7..10 -> {
                    start = end.plusDays(1)
                    end = start.plusDays(29)
                }
                11 -> {
                    start = end.plusDays(1)
                    end = nextStart.minusDays(31)
                }
                else -> {
                    end = nextStart.minusDays(1)
                    start = end.minusDays(29)
                }
            }
            out += start..end
        }
        return out
    }

    /** The Bangla year that contains [today]: it starts on 14 April. */
    fun currentYear(today: LocalDate): Int =
        if (today < LocalDate.of(today.year, 4, 14)) today.year - 594 else today.year - 593

    /** The date that the program of [year] shows on [day], or null if no program of [year] plays on [day]. */
    fun dateOn(year: Int, day: LocalDate): BanglaDate? {
        val all = ranges(year)
        val k = all.indexOfFirst { day in it }
        if (k < 0) return null
        val number = ChronoUnit.DAYS.between(all[k].start, day).toInt() + 1
        return BanglaDate(number, MONTHS[k], if (k == 12) year + 1 else year)
    }
}
