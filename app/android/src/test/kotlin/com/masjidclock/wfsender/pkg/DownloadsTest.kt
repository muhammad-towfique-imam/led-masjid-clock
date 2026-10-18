package com.masjidclock.wfsender.pkg

import java.io.File
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun usesTheNameWhenItIsFree() {
        val dir = tmp.newFolder()
        assertEquals(File(dir, "english.hdpkg"), Downloads.freeFile(dir, "english.hdpkg"))
    }

    @Test
    fun addsANumberWhenTheNameExists() {
        val dir = tmp.newFolder()
        File(dir, "english.hdpkg").writeText("x")
        assertEquals(File(dir, "english (1).hdpkg"), Downloads.freeFile(dir, "english.hdpkg"))
        File(dir, "english (1).hdpkg").writeText("x")
        assertEquals(File(dir, "english (2).hdpkg"), Downloads.freeFile(dir, "english.hdpkg"))
    }

    @Test
    fun savesTheBytesAndNeverOverwrites() {
        val dir = File(tmp.root, "Download")
        val first = Downloads.save(dir, "english.hdpkg") { it.write(byteArrayOf(1, 2, 3)) }
        val second = Downloads.save(dir, "english.hdpkg") { it.write(byteArrayOf(4)) }
        assertEquals("english.hdpkg", first.name)
        assertEquals("english (1).hdpkg", second.name)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertArrayEquals(byteArrayOf(4), second.readBytes())
    }

    @Test
    fun deletesThePartialFileWhenTheWriteFails() {
        val dir = tmp.newFolder()
        assertThrows(IOException::class.java) {
            Downloads.save(dir, "english.hdpkg") { it.write(1); throw IOException("disk full") }
        }
        assertFalse(File(dir, "english.hdpkg").exists())
    }
}
