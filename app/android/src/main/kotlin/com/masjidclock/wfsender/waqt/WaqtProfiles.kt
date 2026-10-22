package com.masjidclock.wfsender.waqt

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** The Waqt inputs of 1 masjid (user, 2026-10-22: each masjid has different jamaat times). */
data class WaqtProfile(val name: String, val values: WaqtValues)

/**
 * All profiles, in name order, and the name of the selected profile. A change that is not permitted (a bad name,
 * an unknown name, the delete of the last profile) gives the same object back.
 */
data class WaqtProfiles(val profiles: List<WaqtProfile>, val selected: String) {
    val current: WaqtProfile get() = profiles.first { it.name == selected }

    /** The message if [name] cannot be the name of a profile, else null. [except]: the profile that is renamed. */
    fun nameError(name: String, except: String? = null): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Type a name."
            profiles.any { it.name != except && it.name.equals(trimmed, ignoreCase = true) } -> "This name is already used."
            else -> null
        }
    }

    fun select(name: String) = if (profiles.any { it.name == name }) copy(selected = name) else this

    /** A new profile with no times. It becomes the selected profile. */
    fun create(name: String) = add(name, EMPTY)

    /** A new profile with the values of the selected profile. It becomes the selected profile. */
    fun duplicate(name: String) = add(name, current.values)

    fun rename(old: String, name: String): WaqtProfiles {
        if (profiles.none { it.name == old } || nameError(name, except = old) != null) return this
        val trimmed = name.trim()
        return WaqtProfiles(
            sorted(profiles.map { if (it.name == old) it.copy(name = trimmed) else it }),
            if (selected == old) trimmed else selected,
        )
    }

    /** The last profile cannot be deleted. If the selected profile is deleted, the first profile becomes selected. */
    fun delete(name: String): WaqtProfiles {
        if (profiles.size < 2 || profiles.none { it.name == name }) return this
        val rest = profiles.filter { it.name != name }
        return WaqtProfiles(rest, if (selected == name) rest.first().name else selected)
    }

    /** The new values of the selected profile. */
    fun update(values: WaqtValues) = copy(profiles = profiles.map { if (it.name == selected) it.copy(values = values) else it })

    private fun add(name: String, values: WaqtValues): WaqtProfiles {
        if (nameError(name) != null) return this
        val trimmed = name.trim()
        return WaqtProfiles(sorted(profiles + WaqtProfile(trimmed, values)), trimmed)
    }

    companion object {
        const val DEFAULT = "Default"
        val EMPTY = WaqtValues(List(6) { null }, emptyList())

        /** The start: 1 profile "Default" with the values from before the profiles (or no times). */
        fun first(values: WaqtValues?) = WaqtProfiles(listOf(WaqtProfile(DEFAULT, values ?: EMPTY)), DEFAULT)

        private fun sorted(list: List<WaqtProfile>) = list.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.name }))
    }
}

/** The profiles as 1 text: the selected name, then 1 line for each profile: "name<TAB>values" (names URL-encoded). */
object WaqtProfileCodec {
    fun encode(profiles: WaqtProfiles): String =
        (listOf(name(profiles.selected)) + profiles.profiles.map { "${name(it.name)}\t${WaqtCodec.encode(it.values)}" })
            .joinToString("\n")

    /** The profiles, or null if [text] is missing or damaged. A missing selection becomes the first profile. */
    fun decode(text: String?): WaqtProfiles? {
        val lines = text?.split("\n")?.takeIf { it.size >= 2 } ?: return null
        val profiles = lines.drop(1).map { line ->
            val f = line.split("\t")
            if (f.size != 2) return null
            WaqtProfile(unname(f[0]) ?: return null, WaqtCodec.decode(f[1]) ?: return null)
        }
        if (profiles.map { it.name.lowercase(Locale.ROOT) }.toSet().size != profiles.size) return null
        val selected = unname(lines[0])?.takeIf { s -> profiles.any { it.name == s } } ?: profiles.first().name
        return WaqtProfiles(profiles, selected)
    }

    private fun name(text: String) = URLEncoder.encode(text, "UTF-8")

    private fun unname(text: String) = try {
        URLDecoder.decode(text, "UTF-8").takeIf { it.isNotBlank() }
    } catch (e: IllegalArgumentException) {
        null
    }
}
