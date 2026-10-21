package com.masjidclock.wfsender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTest {

    @Test
    fun tabsAreTheFirst3InStackOrder() {
        assertEquals(listOf(Page.ENGLISH, Page.BANGLA, Page.HIJRI, Page.WAQT), Page.entries.filter { it.isTab })
        assertFalse(Page.SEND.isTab)
        assertTrue(Page.HIJRI.isTab)
    }

    @Test
    fun startPageIsTheSavedTab() {
        assertEquals(Page.ENGLISH, Page.start(null))
        assertEquals(Page.BANGLA, Page.start("BANGLA"))
        assertEquals(Page.HIJRI, Page.start("HIJRI"))
        assertEquals(Page.WAQT, Page.start("WAQT"))
        assertEquals(Page.ENGLISH, Page.start("SEND"))
        assertEquals(Page.ENGLISH, Page.start("garbage"))
    }
}
