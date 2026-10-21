package com.masjidclock.wfsender.waqt

import com.masjidclock.wfsender.hijri.Sun
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The expected values come from praytimes.py: app/tools/make_waqt_golden.py. */
class SunTest {

    @Test
    fun sunriseAndSunsetMatchPraytimesFrom2020To2040() {
        val rows = javaClass.getResourceAsStream("/waqt/sun-dhaka.txt")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }.map { it.split(" ") }
        assertEquals(7671, rows.size)
        val wrong = rows.filter { (date, sunrise, sunset) ->
            val sun = Sun.dhaka(LocalDate.parse(date))
            sun.sunrise.toString() != sunrise || sun.sunset.toString() != sunset
        }
        assertEquals("days that differ: ${wrong.take(5)}", 0, wrong.size)
    }
}
