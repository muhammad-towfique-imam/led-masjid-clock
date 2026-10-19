package com.masjidclock.wfsender.hijri

import java.time.LocalDate
import java.time.LocalDateTime

/** The start of the Hijri day counter, as the desktop app sets it (hijri_utils.py, set_month). */
object HijriStart {

    /**
     * The day before [monthStart], at the earlier Maghrib of 2 days: the later of [today] and
     * [monthStart], and [monthStart] + 30 days (get_min_margib_time). Python compares only these
     * 2 days, not each day of the month; the app keeps this behavior.
     */
    fun counterStart(monthStart: LocalDate, today: LocalDate): LocalDateTime {
        val first = Maghrib.dhaka(if (today > monthStart) today else monthStart)
        val last = Maghrib.dhaka(monthStart.plusDays(30))
        val earlier = if (first < last) first else last
        return monthStart.minusDays(1).atTime(earlier)
    }
}
