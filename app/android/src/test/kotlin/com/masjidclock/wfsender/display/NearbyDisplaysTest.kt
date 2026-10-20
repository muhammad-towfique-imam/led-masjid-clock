package com.masjidclock.wfsender.display

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyDisplaysTest {

    private val known = mapOf(
        "WF1_1183677752" to DisplayTab.HIJRI,
        "WF1_1200423879" to DisplayTab.BANGLA,
        "WF2_f412faca189c" to DisplayTab.ENGLISH,
    )

    private fun list(vararg seen: WifiSeen, tab: DisplayTab = DisplayTab.HIJRI) =
        nearbyDisplays(seen.toList(), tab) { known[it] }

    @Test
    fun onlyCardNetworks() {
        val result = list(
            WifiSeen("HomeWiFi", -40), WifiSeen("WF1_1183677752", -70), WifiSeen("WF4_c04e301c49d4", -60),
            WifiSeen("WFX_1", -50), WifiSeen("", -30), WifiSeen("wf1_lower", -30), WifiSeen("WF2_", -30),
        )
        assertEquals(listOf("WF1_1183677752", "WF4_c04e301c49d4"), result.map { it.name })
    }

    @Test
    fun thisTabFirstThenOtherTabsThenUnknownEachByStrongestSignal() {
        val result = list(
            WifiSeen("WF4_c04e301c49d4", -40),
            WifiSeen("WF2_f412faca189c", -80),
            WifiSeen("WF1_1200423879", -50),
            WifiSeen("WF1_1183677752", -85),
            WifiSeen("WF1_9999999999", -45),
        )
        assertEquals(
            listOf("WF1_1183677752", "WF1_1200423879", "WF2_f412faca189c", "WF4_c04e301c49d4", "WF1_9999999999"),
            result.map { it.name },
        )
    }

    @Test
    fun theStrongestOfTheSameNameCounts() {
        val result = list(WifiSeen("WF1_1183677752", -80), WifiSeen("WF1_1183677752", -50))
        assertEquals(1, result.size)
        assertEquals(-50, result[0].level)
    }

    @Test
    fun labels() {
        val result = list(WifiSeen("WF1_1183677752", -50), WifiSeen("WF1_1200423879", -50), WifiSeen("WF4_c04e301c49d4", -50))
        assertEquals(
            listOf("WF1_1183677752 — Hijri display ✓", "WF1_1200423879 — Bangla display", "WF4_c04e301c49d4 — Unknown display"),
            result.map { it.label },
        )
    }

    @Test
    fun signalText() {
        assertEquals("strong signal", signalText(-60))
        assertEquals("medium signal", signalText(-61))
        assertEquals("medium signal", signalText(-75))
        assertEquals("weak signal", signalText(-76))
    }

    @Test
    fun scanSummaryForTheDiagnosticLine() {
        assertEquals(
            "Scan: 3 networks, 0 cards. Names: HomeNet, WF1 1183677752, <hidden>. Location: off",
            scanSummary(listOf(WifiSeen("HomeNet", -40), WifiSeen("WF1 1183677752", -50), WifiSeen("", -60)), 0, locationOn = false),
        )
        assertEquals("Scan: 0 networks, 0 cards. Location: on", scanSummary(emptyList(), 0, locationOn = true))
        val many = (1..9).map { WifiSeen("N$it", -40 - it) }
        assertEquals("Scan: 9 networks, 0 cards. Names: N1, N2, N3, N4, N5, N6, …. Location: on", scanSummary(many, 0, true))
    }
}
