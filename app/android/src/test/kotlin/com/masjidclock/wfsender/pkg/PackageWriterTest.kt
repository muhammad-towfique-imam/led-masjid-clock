package com.masjidclock.wfsender.pkg

import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.site.ZipImporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PackageWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val screen = ByteArray(66).also {
        it[0] = 'H'.code.toByte(); it[1] = 'S'.code.toByte(); it[5] = 0x80.toByte(); it[7] = 0x40
    }
    private val program = ByteArray(2500) { (it % 251).toByte() }.also {
        it[0] = 'H'.code.toByte(); it[1] = 'C'.code.toByte()
    }
    private val export = HdsExport(screen, program)

    private fun written(): ByteArray = ByteArrayOutputStream().also { PackageWriter.write(export, it) }.toByteArray()

    @Test
    fun writesExactlyTheTwoFilesAtTheRoot() {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(written())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("screen.hds", "progam.hds"), entries.keys)
        assertArrayEquals(screen, entries["screen.hds"])
        assertArrayEquals(program, entries["progam.hds"])
    }

    @Test
    fun writtenPackageImportsWithTheSameBytes() {
        val siteDir = File(tmp.newFolder("files"), "site")
        val site = ZipImporter().import(ByteArrayInputStream(written()), "masjid-north.hdpkg", siteDir)
        val card = site.cards.single()
        assertEquals("masjid-north", card.label)
        assertEquals(128, card.width)
        assertEquals(64, card.height)
        assertArrayEquals(program, File(card.dir, "progam.hds").readBytes())
        assertArrayEquals(screen, File(card.dir, "screen.hds").readBytes())
    }
}
