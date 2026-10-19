package com.masjidclock.wfsender.hijri

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The Maghrib time as the desktop app calculates it: praytimes.py (PrayTimes.org, ver 2.3,
 * by Hamid Zarrabi-Zadeh, http://praytimes.org), Karachi method, Dhaka, UTC+6, no DST.
 * Only the sunset path is ported. The order of the operations is the same as in Python,
 * so that the results agree to the minute.
 */
object Maghrib {
    private const val LATITUDE = 23.777176
    private const val LONGITUDE = 90.399452
    private const val TIME_ZONE = 6.0

    /** The sun angle at sunset for elevation 0: 0.833 + 0.0347 * sqrt(0). */
    private const val SUNSET_ANGLE = 0.833

    fun dhaka(date: LocalDate): LocalTime {
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - LONGITUDE / (15 * 24.0)
        // 1 iteration from the first guess 18:00, as numIterations = 1 in praytimes.py.
        val portion = 18 / 24.0
        val decl = sunPosition(jDate + portion).first
        val noon = fixHour(12 - sunPosition(jDate + portion).second)
        val t = 1 / 15.0 * arccos((-dsin(SUNSET_ANGLE) - dsin(decl) * dsin(LATITUDE)) / (dcos(decl) * dcos(LATITUDE)))
        val sunset = noon + t + (TIME_ZONE - LONGITUDE / 15.0)
        // Maghrib = sunset - 0 min. Round to the minute as getFormattedTime does.
        val time = fixHour(sunset + 0.5 / 60)
        val hours = floor(time)
        val minutes = floor((time - hours) * 60)
        return LocalTime.of(hours.toInt(), minutes.toInt())
    }

    /** The declination and the equation of time. */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = arctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = arcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun dsin(degrees: Double): Double = sin(Math.toRadians(degrees))
    private fun dcos(degrees: Double): Double = cos(Math.toRadians(degrees))
    private fun arcsin(x: Double) = Math.toDegrees(asin(x))
    private fun arccos(x: Double) = Math.toDegrees(acos(x))
    private fun arctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun fixAngle(angle: Double) = fix(angle, 360.0)
    private fun fixHour(hour: Double) = fix(hour, 24.0)

    private fun fix(a: Double, mode: Double): Double {
        val r = a - mode * floor(a / mode)
        return if (r < 0) r + mode else r
    }
}
