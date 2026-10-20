package com.masjidclock.wfsender.display

/**
 * The card list (display-tabs spec section 5A): card name → the tab of the last successful send to that card.
 * Stored as lines "<name>\t<TAB>". A line that does not parse is skipped.
 */
class CardList(private val read: () -> String?, private val write: (String) -> Unit) {

    fun tabOf(cardName: String?): DisplayTab? {
        if (cardName.isNullOrBlank()) return null
        return entries()[cardName]
    }

    /** After a successful send. A card without a name is not saved. */
    fun remember(cardName: String?, tab: DisplayTab) {
        if (cardName.isNullOrBlank()) return
        val all = entries().toMutableMap()
        all[cardName] = tab
        write(all.entries.joinToString("\n") { "${it.key}\t${it.value.name}" })
    }

    private fun entries(): Map<String, DisplayTab> {
        val out = LinkedHashMap<String, DisplayTab>()
        for (line in read().orEmpty().lines()) {
            val parts = line.split('\t')
            if (parts.size != 2 || parts[0].isBlank()) continue
            val tab = DisplayTab.entries.firstOrNull { it.name == parts[1] } ?: continue
            out[parts[0]] = tab
        }
        return out
    }
}

/** The text under the card name in the step "Connect". [warning] = the error color. */
data class CardNote(val text: String, val warning: Boolean)

/** [known] is the tab of the last successful send to [cardName], or null. */
fun cardNote(cardName: String?, tab: DisplayTab, known: DisplayTab?): CardNote? = when {
    cardName.isNullOrBlank() || known == null -> null
    known == tab -> CardNote("${tab.label} display", warning = false)
    else -> CardNote("This card last got the ${known.label} program.", warning = true)
}
