package com.masjidclock.wfsender.protocol

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HdsExportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fixture = File(javaClass.getResource("/export-128x64")!!.toURI())

    private fun dirWith(screen: ByteArray?, program: ByteArray?): File {
        val dir = tmp.newFolder()
        screen?.let { File(dir, HdsExport.SCREEN_FILE).writeBytes(it) }
        program?.let { File(dir, HdsExport.PROGRAM_FILE).writeBytes(it) }
        return dir
    }

    private val goodScreen get() = File(fixture, HdsExport.SCREEN_FILE).readBytes()
    private val goodProgram get() = File(fixture, HdsExport.PROGRAM_FILE).readBytes()

    @Test
    fun readsTheSpikeExport() {
        val export = HdsExport.fromDirectory(fixture)
        assertEquals(128, export.width)
        assertEquals(64, export.height)
        assertEquals(66, export.screen.size)
        assertEquals(12586, export.program.size)
    }

    @Test
    fun missingScreenFile() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport.fromDirectory(dirWith(null, goodProgram))
        }
        assertTrue(e.message!!.contains("screen.hds is missing"))
    }

    @Test
    fun missingProgramFile() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport.fromDirectory(dirWith(goodScreen, null))
        }
        assertTrue(e.message!!.contains("progam.hds is missing"))
    }

    @Test
    fun screenWithWrongSize() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport(goodScreen.copyOf(65), goodProgram)
        }
        assertTrue(e.message!!.contains("66 bytes"))
    }

    @Test
    fun screenWithWrongPrefix() {
        val screen = goodScreen.also { it[1] = 'X'.code.toByte() }
        val e = assertThrows(InvalidExportException::class.java) { HdsExport(screen, goodProgram) }
        assertTrue(e.message!!.contains("does not start with HS"))
    }

    @Test
    fun programWithWrongPrefix() {
        val program = goodProgram.also { it[0] = 'X'.code.toByte() }
        val e = assertThrows(InvalidExportException::class.java) { HdsExport(goodScreen, program) }
        assertTrue(e.message!!.contains("does not start with HC"))
    }

    @Test
    fun programTooShort() {
        val e = assertThrows(InvalidExportException::class.java) {
            HdsExport(goodScreen, byteArrayOf('H'.code.toByte(), 'C'.code.toByte(), 0, 0))
        }
        assertTrue(e.message!!.contains("too short"))
    }

    @Test
    fun readsWidthAndHeightAsBigEndian() {
        val screen = goodScreen.also { it[4] = 0x01; it[5] = 0x80.toByte(); it[6] = 0x00; it[7] = 0x40 }
        val export = HdsExport(screen, goodProgram)
        assertEquals(384, export.width)
        assertEquals(64, export.height)
    }
}
