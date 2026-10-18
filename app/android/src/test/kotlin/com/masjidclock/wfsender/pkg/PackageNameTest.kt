package com.masjidclock.wfsender.pkg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNameTest {

    @Test
    fun acceptsAValidName() {
        assertEquals("masjid-north_english 2", PackageName.clean("masjid-north_english 2"))
    }

    @Test
    fun removesSpacesAtStartAndEnd() {
        assertEquals("english", PackageName.clean("  english  "))
    }

    @Test
    fun removesATypedExtension() {
        assertEquals("english", PackageName.clean("english.hdpkg"))
        assertEquals("english", PackageName.clean("english.HDPKG "))
    }

    @Test
    fun rejectsEmptyAfterCleaning() {
        assertNull(PackageName.clean(""))
        assertNull(PackageName.clean("   "))
        assertNull(PackageName.clean(".hdpkg"))
    }

    @Test
    fun rejectsMoreThan50Characters() {
        assertEquals("a".repeat(50), PackageName.clean("a".repeat(50)))
        assertNull(PackageName.clean("a".repeat(51)))
    }

    @Test
    fun rejectsCharactersThatAreNotAllowed() {
        assertNull(PackageName.clean("a/b"))
        assertNull(PackageName.clean("a.b"))
        assertNull(PackageName.clean("mosquée"))
        assertNull(PackageName.clean("মসজিদ"))
    }

    @Test
    fun makesAndReadsFileNames() {
        assertEquals("english.hdpkg", PackageName.fileName("english"))
        assertTrue(PackageName.hasExtension("english.HdPkg"))
        assertFalse(PackageName.hasExtension("english.zip"))
        assertEquals("english", PackageName.withoutExtension("english.HDPKG"))
        assertEquals("english.zip", PackageName.withoutExtension("english.zip"))
    }
}
