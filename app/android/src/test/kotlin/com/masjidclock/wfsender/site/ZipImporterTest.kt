package com.masjidclock.wfsender.site

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val siteDir by lazy { File(tmp.newFolder("files"), "site") }

    private fun screen(width: Int, height: Int) = ByteArray(66).also {
        it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte()
        it[4] = (width shr 8).toByte(); it[5] = width.toByte()
        it[6] = (height shr 8).toByte(); it[7] = height.toByte()
    }

    private fun program(size: Int = 100) = ByteArray(size).also {
        it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte()
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun import(zip: ByteArray, name: String = "site-a.hdpkg", importer: ZipImporter = ZipImporter()) =
        importer.import(ByteArrayInputStream(zip), name, siteDir)

    private fun importError(zip: ByteArray, importer: ZipImporter = ZipImporter()): String =
        assertThrows(ImportException::class.java) { import(zip, importer = importer) }.message!!

    @Test
    fun importsOneDisplayFolderAndIgnoresOtherFiles() {
        val site = import(
            zipOf(
                "english/screen.hds" to screen(128, 64),
                "english/progam.hds" to program(200),
                "english/set.hds" to ByteArray(1024),
                "english/smart.ss" to ByteArray(118),
                "english/font.hds" to ByteArray(10),
            )
        )
        assertEquals("site-a", site.name)
        assertEquals(listOf("english"), site.cards.map { it.label })
        val english = site.cards[0]
        assertEquals(128, english.width)
        assertEquals(64, english.height)
        assertEquals(200, english.programSize)
        assertTrue(File(english.dir, "progam.hds").isFile)
        assertFalse(File(english.dir, "set.hds").exists())
        assertFalse(File(english.dir, "smart.ss").exists())
        assertFalse(File(english.dir, "font.hds").exists())
        assertTrue(english.dir.canonicalPath.startsWith(siteDir.canonicalPath))
    }

    @Test
    fun filesAtTheZipRootUseTheZipName() {
        val site = import(zipOf("screen.hds" to screen(128, 64), "progam.hds" to program()), name = "masjid-x.hdpkg")
        assertEquals(listOf("masjid-x"), site.cards.map { it.label })
    }

    @Test
    fun rejectsZipWithTwoDisplays() {
        val message = importError(
            zipOf(
                "english/screen.hds" to screen(128, 64),
                "english/progam.hds" to program(),
                "hijri/screen.hds" to screen(128, 32),
                "hijri/progam.hds" to program(),
            )
        )
        assertEquals(
            "The ZIP file contains more than 1 display. Each ZIP file must contain the program of 1 display.",
            message,
        )
    }

    @Test
    fun rejectsZipWithTwoDisplaysWithTheSameFolderName() {
        val message = importError(
            zipOf(
                "m2/english/screen.hds" to screen(128, 64),
                "m2/english/progam.hds" to program(),
                "m3/english/screen.hds" to screen(128, 32),
                "m3/english/progam.hds" to program(),
            )
        )
        assertTrue(message, message.contains("more than 1 display"))
    }

    @Test
    fun keepsTheNestedFolderPathAsTheLabel() {
        val site = import(zipOf("m2/english/screen.hds" to screen(128, 64), "m2/english/progam.hds" to program()))
        assertEquals(listOf("m2/english"), site.cards.map { it.label })
    }

    @Test
    fun acceptsZipFromMacAndWindowsTools() {
        val site = import(
            zipOf(
                "__MACOSX/english/._screen.hds" to ByteArray(10),
                "english\\SCREEN.HDS" to screen(128, 64),
                "english\\Progam.hds" to program(),
                "english/._progam.hds" to ByteArray(10),
            )
        )
        assertEquals(listOf("english"), site.cards.map { it.label })
        assertTrue(File(site.cards[0].dir, "screen.hds").isFile)
    }

    @Test
    fun rejectsZipWithoutCardFolders() {
        assertTrue(importError(zipOf("readme.txt" to ByteArray(3))).contains("no display folders"))
    }

    @Test
    fun rejectsFolderWithOnlyScreenFile() {
        val message = importError(zipOf("english/screen.hds" to screen(128, 64)))
        assertTrue(message, message.contains("'english'") && message.contains("progam.hds is missing"))
    }

    @Test
    fun rejectsInvalidExport() {
        val bad = screen(128, 64).also { it[0] = 'X'.code.toByte() }
        val message = importError(zipOf("english/screen.hds" to bad, "english/progam.hds" to program()))
        assertTrue(message, message.contains("not a valid HD2020 export"))
    }

    @Test
    fun rejectsDotDotPath() {
        val message = importError(zipOf("../evil/screen.hds" to screen(128, 64), "../evil/progam.hds" to program()))
        assertTrue(message, message.contains("unsafe path"))
        assertFalse(File(siteDir.parentFile!!.parentFile, "evil").exists())
        assertFalse(File(siteDir.parentFile, "evil").exists())
    }

    @Test
    fun rejectsAbsolutePath() {
        val message = importError(zipOf("/etc/screen.hds" to screen(128, 64)))
        assertTrue(message, message.contains("unsafe path"))
    }

    @Test
    fun rejectsZipLargerThanTheLimit() {
        val small = ZipImporter(maxUnpackedBytes = 1_000)
        val message = importError(zipOf("a/screen.hds" to screen(128, 64), "a/progam.hds" to program(2_000)), small)
        assertTrue(message, message.contains("too large"))
    }

    @Test
    fun rejectsWindowsZipWithNamesThatAreNotUtf8() {
        // Windows "Send to > Compressed folder" writes non-ASCII names in the OEM code page, without the UTF-8 flag.
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes, java.nio.charset.Charset.forName("IBM437")).use { zip ->
            zip.putNextEntry(ZipEntry("Mosquée/screen.hds")); zip.write(screen(128, 64)); zip.closeEntry()
            zip.putNextEntry(ZipEntry("Mosquée/progam.hds")); zip.write(program()); zip.closeEntry()
        }
        val message = importError(bytes.toByteArray())
        assertTrue(message, message.contains("English letters and numbers"))
    }

    @Test
    fun rejectsZipExtension() {
        val zip = zipOf("screen.hds" to screen(128, 64), "progam.hds" to program())
        val error = assertThrows(ImportException::class.java) { import(zip, name = "english.zip") }
        assertEquals(ZipImporter.NOT_PACKAGE, error.message)
        assertEquals("This file is not an HD Sender package. Use a file that ends with .hdpkg.", error.message)
    }

    @Test
    fun acceptsUpperCaseExtension() {
        val site = import(zipOf("screen.hds" to screen(128, 64), "progam.hds" to program()), name = "North-English.HDPKG")
        assertEquals("North-English", site.name)
        assertEquals(listOf("North-English"), site.cards.map { it.label })
    }

    @Test
    fun rejectsFileThatIsNotAZip() {
        importError(ByteArray(500) { 7 })
    }

    @Test
    fun newImportReplacesTheEarlierSite() {
        import(zipOf("a/screen.hds" to screen(128, 64), "a/progam.hds" to program()), name = "one.hdpkg")
        import(zipOf("b/screen.hds" to screen(128, 64), "b/progam.hds" to program()), name = "two.hdpkg")
        val loaded = ZipImporter.load(siteDir)!!
        assertEquals("two", loaded.name)
        assertEquals(listOf("b"), loaded.cards.map { it.label })
    }

    @Test
    fun failedImportKeepsTheEarlierSite() {
        import(zipOf("a/screen.hds" to screen(128, 64), "a/progam.hds" to program()), name = "one.hdpkg")
        importError(zipOf("readme.txt" to ByteArray(3)))
        val loaded = ZipImporter.load(siteDir)!!
        assertEquals("one", loaded.name)
        assertEquals(listOf("a"), loaded.cards.map { it.label })
    }

    @Test
    fun loadReturnsNullWhenNothingWasImported() {
        assertNull(ZipImporter.load(siteDir))
    }
}
