package com.masjidclock.wfsender.display

/** A WiFi network from the phone's scan: its name and its signal level in dBm. */
data class WifiSeen(val ssid: String, val level: Int)

/** A card WiFi near the phone. [tab] is the tab of the last successful send to it, from the card list. */
data class NearbyDisplay(val name: String, val level: Int, val tab: DisplayTab?, val forThisTab: Boolean) {
    val label: String
        get() = when {
            forThisTab -> "$name — ${tab?.label} display ✓"
            tab != null -> "$name — ${tab.label} display"
            else -> "$name — Unknown display"
        }
}

/** The WiFi name of a card is its card name, for example WF1_1183677752. */
private val CARD_WIFI = Regex("^WF\\d_\\S+$")

/**
 * The card networks in [seen], for the tab [tab]: the card of this tab first, then the cards of other tabs, then
 * unknown cards; in each group the strongest signal first. A name seen twice counts once, with its strongest signal.
 */
fun nearbyDisplays(seen: List<WifiSeen>, tab: DisplayTab, tabOf: (String) -> DisplayTab?): List<NearbyDisplay> =
    seen.filter { CARD_WIFI.matches(it.ssid) }
        .groupBy { it.ssid }
        .map { (name, all) ->
            val known = tabOf(name)
            NearbyDisplay(name, all.maxOf { it.level }, known, known == tab)
        }
        .sortedWith(compareBy<NearbyDisplay>({ if (it.forThisTab) 0 else if (it.tab != null) 1 else 2 }, { -it.level }))

fun signalText(level: Int): String = when {
    level >= -60 -> "strong signal"
    level >= -75 -> "medium signal"
    else -> "weak signal"
}

/**
 * The diagnostic line under an empty list: how many networks the scan gave, how many are cards, up to 6 names
 * (an empty name shows as <hidden>), and the location setting. It shows where the data stops.
 */
fun scanSummary(seen: List<WifiSeen>, cards: Int, locationOn: Boolean): String {
    val names = seen.map { it.ssid.ifEmpty { "<hidden>" } }.distinct()
    val shown = if (names.size > 6) names.take(6) + "…" else names
    val list = if (shown.isEmpty()) "" else " Names: ${shown.joinToString(", ")}."
    return "Scan: ${seen.size} networks, $cards cards.$list Location: ${if (locationOn) "on" else "off"}"
}
