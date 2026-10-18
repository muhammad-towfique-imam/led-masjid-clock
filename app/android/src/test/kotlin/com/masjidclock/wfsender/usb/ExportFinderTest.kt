package com.masjidclock.wfsender.usb

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportFinderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val screen = ByteArray(66).also { it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte(); it[5] = 0x80.toByte(); it[7] = 0x40 }
    private val program = ByteArray(100).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() }

    private fun root(screenName: String? = "screen.hds", programName: String? = "progam.hds", screenBytes: ByteArray = screen): File {
        val dir = tmp.newFolder()
        screenName?.let { File(dir, it).writeBytes(screenBytes) }
        programName?.let { File(dir, it).writeBytes(program) }
        return dir
    }

    @Test
    fun noRoots() {
        assertNull(ExportFinder.find(emptyList()))
    }

    @Test
    fun rootWithoutExport() {
        assertNull(ExportFinder.find(listOf(root(screenName = null, programName = null))))
        assertNull(ExportFinder.find(listOf(root(programName = null))))
    }

    @Test
    fun rootWithInvalidExport() {
        assertNull(ExportFinder.find(listOf(root(screenBytes = ByteArray(66)))))
    }

    @Test
    fun upperCaseFileNames() {
        val found = ExportFinder.find(listOf(root("SCREEN.HDS", "PROGAM.HDS")))!!
        assertEquals(128, found.export.width)
        assertEquals(64, found.export.height)
    }

    @Test
    fun usesTheFirstValidRoot() {
        val empty = root(screenName = null, programName = null)
        val first = root()
        val second = root()
        assertEquals(first, ExportFinder.find(listOf(empty, first, second))!!.root)
    }

    @Test
    fun rootThatCannotBeListedIsSkipped() {
        val missing = File(tmp.root, "removed-drive")
        val valid = root()
        assertEquals(valid, ExportFinder.find(listOf(missing, valid))!!.root)
    }
}
