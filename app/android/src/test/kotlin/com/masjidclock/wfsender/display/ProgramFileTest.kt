package com.masjidclock.wfsender.display

import com.masjidclock.wfsender.hijri.HijriM5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProgramFileTest {

    private val bangla: ByteArray = javaClass.getResourceAsStream("/bangla/m5/base.hds")!!.use { it.readBytes() }

    @Test
    fun hijriFileHasOneProgram() {
        val base = HijriM5.base(1)
        val programs = Programs.of(base)
        assertEquals(1, programs.size)
        assertEquals(Areas.of(base).map { it.start }, programs[0].areas.map { it.start })
    }

    @Test
    fun banglaFileHas13ProgramsWith9Areas() {
        val programs = Programs.of(bangla)
        assertEquals(13, programs.size)
        for ((k, program) in programs.withIndex()) {
            assertEquals("program ${k + 1}", 9, program.areas.size)
            assertEquals("program ${k + 1} text", 3, program.areas.count { it.type == AreaType.TEXT })
            assertEquals("program ${k + 1} counter", 3, program.areas.count { it.type == AreaType.COUNTER })
            assertEquals("program ${k + 1} time", 3, program.areas.count { it.type == AreaType.TIME })
        }
    }

    @Test
    fun banglaProgramOffsets() {
        val programs = Programs.of(bangla)
        assertEquals(79, programs.first().start)
        assertEquals(93187, programs.last().start)
        assertEquals(bangla.size, programs.last().end)
    }

    @Test
    fun shapeIgnoresTheColor() {
        val shapes = "1432".map { HijriM5.digits.getValue(it).shape() }
        val text = Programs.of(bangla)[0].areas.first { it.type == AreaType.TEXT }
        assertNotNull(text.findShapes(shapes))
    }
}
