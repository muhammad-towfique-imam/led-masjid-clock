package com.masjidclock.wfsender.time

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The card has no time zone: it gets the local wall-clock time as seconds since 1970.
 * Example: 21:09:41 in Dhaka (UTC+6) is sent as if it were 21:09:41 UTC.
 */
object WallClock {

    fun wallSeconds(utcMillis: Long, offsetMillis: Int): Long = (utcMillis + offsetMillis) / 1000

    /** The phone's local time now, as wall-clock seconds. */
    fun now(): Long {
        val millis = System.currentTimeMillis()
        return wallSeconds(millis, TimeZone.getDefault().getOffset(millis))
    }

    fun time(wallSeconds: Long): String = format("HH:mm:ss", wallSeconds)

    fun date(wallSeconds: Long): String = format("yyyy-MM-dd", wallSeconds)

    private fun format(pattern: String, wallSeconds: Long): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(wallSeconds * 1000))
}
