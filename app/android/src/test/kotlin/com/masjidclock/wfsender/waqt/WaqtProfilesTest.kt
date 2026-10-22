package com.masjidclock.wfsender.waqt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

class WaqtProfilesTest {

    private val empty = WaqtValues(List(6) { null }, emptyList())
    private val aman = WaqtValues(
        listOf(HourMinute(5, 10), HourMinute(1, 15), HourMinute(4, 15), HourMinute(5, 40), HourMinute(7, 0), HourMinute(1, 30)),
        listOf(WaqtChange(LocalDate.of(2026, 10, 31), 2, HourMinute(4, 0))),
    )
    private val start = WaqtProfiles.first(aman)

    @Test
    fun firstProfileIsDefaultWithTheOldValues() {
        assertEquals(listOf(WaqtProfile("Default", aman)), start.profiles)
        assertEquals("Default", start.selected)
        assertEquals(aman, start.current.values)
    }

    @Test
    fun copyHasTheSameValuesAndIsSelected() {
        val p = start.duplicate("Baitul Aman")
        assertEquals("Baitul Aman", p.selected)
        assertEquals(aman, p.current.values)
        // The 2 profiles are separate.
        val changed = p.update(empty)
        assertEquals(empty, changed.current.values)
        assertEquals(aman, changed.select("Default").current.values)
    }

    @Test
    fun newProfileHasNoTimes() {
        val p = start.create("Taqwa Masjid")
        assertEquals("Taqwa Masjid", p.selected)
        assertEquals(empty, p.current.values)
    }

    @Test
    fun profilesAreInNameOrder() {
        val p = start.create("taqwa").create("Baitul Aman")
        assertEquals(listOf("Baitul Aman", "Default", "taqwa"), p.profiles.map { it.name })
    }

    @Test
    fun namesAreTrimmedUniqueAndNotEmpty() {
        assertEquals("Type a name.", start.nameError("  "))
        assertEquals("This name is already used.", start.nameError(" default "))
        assertNull(start.nameError("Baitul Aman"))
        // A rename to the same name with a different case is permitted.
        assertNull(start.nameError("DEFAULT", except = "Default"))
        assertEquals("Baitul Aman", start.create("  Baitul Aman ").selected)
        // A bad name changes nothing.
        assertSame(start, start.create(" "))
        assertSame(start, start.duplicate("Default"))
    }

    @Test
    fun renameKeepsTheValuesAndTheSelection() {
        val p = start.rename("Default", "Baitul Aman")
        assertEquals(listOf(WaqtProfile("Baitul Aman", aman)), p.profiles)
        assertEquals("Baitul Aman", p.selected)
    }

    @Test
    fun deleteSelectsTheFirstProfileAndKeepsTheLastOne() {
        val p = start.create("Taqwa").delete("Taqwa")
        assertEquals(listOf("Default"), p.profiles.map { it.name })
        assertEquals("Default", p.selected)
        assertSame(p, p.delete("Default"))
        // Delete of a profile that is not selected keeps the selection.
        assertEquals("Taqwa", start.create("Taqwa").delete("Default").selected)
    }

    @Test
    fun selectOfAnUnknownNameChangesNothing() {
        assertSame(start, start.select("Nothing"))
    }

    @Test
    fun codecRoundTrip() {
        val p = start.duplicate("Masjid; \"A|B\",\tC\n%").create("Taqwa")
        assertEquals(p, WaqtProfileCodec.decode(WaqtProfileCodec.encode(p)))
        assertNull(WaqtProfileCodec.decode(null))
        assertNull(WaqtProfileCodec.decode(""))
        assertNull(WaqtProfileCodec.decode("Default\tbad"))
    }

    @Test
    fun codecRepairsAMissingSelection() {
        val text = WaqtProfileCodec.encode(start.create("Taqwa")).lines().drop(1).joinToString("\n")
        assertEquals("Default", WaqtProfileCodec.decode("Nothing\n$text")!!.selected)
    }
}
