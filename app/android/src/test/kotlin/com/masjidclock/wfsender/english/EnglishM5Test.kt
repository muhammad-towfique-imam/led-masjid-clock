package com.masjidclock.wfsender.english

import com.masjidclock.wfsender.protocol.HdsExport
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishM5Test {

    @Test
    fun programIsTheExport() {
        assertEquals(34034, EnglishM5.program.size)
        assertEquals("HC", String(EnglishM5.program, 0, 2, Charsets.US_ASCII))
        val export = HdsExport(EnglishM5.screen, EnglishM5.program)
        assertEquals(384, export.width)
        assertEquals(64, export.height)
    }
}
