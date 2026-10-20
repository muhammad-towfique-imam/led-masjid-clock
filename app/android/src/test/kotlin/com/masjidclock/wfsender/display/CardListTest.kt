package com.masjidclock.wfsender.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardListTest {

    private var stored: String? = null
    private fun list() = CardList(read = { stored }, write = { stored = it })

    @Test
    fun remembersAndChanges() {
        list().remember("WF1_1183677752", DisplayTab.HIJRI)
        assertEquals(DisplayTab.HIJRI, list().tabOf("WF1_1183677752"))
        list().remember("WF1_1183677752", DisplayTab.BANGLA)
        list().remember("WF2_f412faca189c", DisplayTab.ENGLISH)
        assertEquals(DisplayTab.BANGLA, list().tabOf("WF1_1183677752"))
        assertEquals(DisplayTab.ENGLISH, list().tabOf("WF2_f412faca189c"))
        assertNull(list().tabOf("WF1_1200423879"))
    }

    @Test
    fun ignoresBlankNames() {
        list().remember(null, DisplayTab.HIJRI)
        list().remember("", DisplayTab.HIJRI)
        assertNull(stored)
        assertNull(list().tabOf(null))
    }

    @Test
    fun damagedTextGivesEmptyList() {
        stored = "garbage\nWF1_x\tNOPE\n\t\t"
        assertNull(list().tabOf("WF1_x"))
        assertNull(list().tabOf("garbage"))
    }

    @Test
    fun notes() {
        assertEquals(CardNote("Hijri display", false), cardNote("WF1_1", DisplayTab.HIJRI, DisplayTab.HIJRI))
        assertEquals(
            CardNote("This card last got the Hijri program.", true),
            cardNote("WF1_1", DisplayTab.BANGLA, DisplayTab.HIJRI),
        )
        assertNull(cardNote("WF1_1", DisplayTab.HIJRI, null))
        assertNull(cardNote(null, DisplayTab.HIJRI, DisplayTab.HIJRI))
    }
}
