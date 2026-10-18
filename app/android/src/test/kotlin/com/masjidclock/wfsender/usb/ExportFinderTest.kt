package com.masjidclock.wfsender.usb

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportFinderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val screen = ByteArray(66).also { it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte(); it[5] = 0x80.toByte(); it[7] = 0x40 }
    private val program = ByteArray(100).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() }

    private fun root(
        screenName: String? = "screen.hds",
        programName: String? = "progam.hds",
        screenBytes: ByteArray = screen,
        programBytes: ByteArray = program,
    ): File {
        val dir = tmp.newFolder()
        screenName?.let { File(dir, it).writeBytes(screenBytes) }
        programName?.let { File(dir, it).writeBytes(programBytes) }
        return dir
    }

    private fun found(result: ExportSearch): FoundExport {
        assertTrue("expected Found, got $result", result is ExportSearch.Found)
        return (result as ExportSearch.Found).found
    }

    @Test
    fun noRoots() {
        assertEquals(ExportSearch.None, ExportFinder.find(emptyList()))
    }

    @Test
    fun rootWithoutExport() {
        assertEquals(ExportSearch.None, ExportFinder.find(listOf(root(screenName = null, programName = null))))
        assertEquals(ExportSearch.None, ExportFinder.find(listOf(root(programName = null))))
    }

    @Test
    fun rootWithInvalidExport() {
        assertEquals(ExportSearch.None, ExportFinder.find(listOf(root(screenBytes = ByteArray(66)))))
    }

    @Test
    fun upperCaseFileNames() {
        val export = found(ExportFinder.find(listOf(root("SCREEN.HDS", "PROGAM.HDS")))).export
        assertEquals(128, export.width)
        assertEquals(64, export.height)
    }

    @Test
    fun usesTheFirstValidRoot() {
        val empty = root(screenName = null, programName = null)
        val first = root()
        val second = root()
        assertEquals(first, found(ExportFinder.find(listOf(empty, first, second))).root)
    }

    @Test
    fun rootThatCannotBeListedIsSkipped() {
        val missing = File(tmp.root, "removed-drive")
        val valid = root()
        assertEquals(valid, found(ExportFinder.find(listOf(missing, valid))).root)
    }

    @Test
    fun exportLargerThanTheLimitIsTooLarge() {
        // The Send flow rejects packages over the unpacked limit, so Create must not make them.
        val big = root(programBytes = ByteArray(2_000).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() })
        assertEquals(ExportSearch.TooLarge(big), ExportFinder.find(listOf(big), maxBytes = 1_000))
    }

    @Test
    fun defaultLimitIsTheImportLimit() {
        assertEquals(20L * 1024 * 1024, ExportFinder.DEFAULT_MAX_BYTES)
    }

    @Test
    fun reusesThePreviousExportWhenTheFilesDidNotChange() {
        val dir = root()
        val first = found(ExportFinder.find(listOf(dir)))
        assertSame(first, found(ExportFinder.find(listOf(dir), previous = first)))

        val changed = program.copyOf(120).also { it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte() }
        File(dir, "progam.hds").writeBytes(changed)
        val second = found(ExportFinder.find(listOf(dir), previous = first))
        assertNotSame(first, second)
        assertEquals(120, second.export.program.size)
    }
}
