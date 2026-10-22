package com.masjidclock.wfsender

import android.app.Application
import android.content.Context
import com.masjidclock.wfsender.display.DisplayTab
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.waqt.HourMinute
import com.masjidclock.wfsender.waqt.WaqtChange
import com.masjidclock.wfsender.waqt.WaqtCodec
import com.masjidclock.wfsender.waqt.WaqtInput
import com.masjidclock.wfsender.waqt.WaqtM5
import com.masjidclock.wfsender.waqt.WaqtProfileCodec
import com.masjidclock.wfsender.waqt.WaqtProfiles
import com.masjidclock.wfsender.waqt.WaqtProgram
import com.masjidclock.wfsender.waqt.WaqtSchedule
import com.masjidclock.wfsender.waqt.WaqtValues
import com.masjidclock.wfsender.waqt.waqtLogHeader
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The Waqt tab (spec 2026-10-21-masjid-clock-waqt-design.md): makes the m5 waqt program and sends it. */
class WaqtViewModel(app: Application) : DisplayViewModel(app, DisplayTab.WAQT) {

    private val prefs = app.getSharedPreferences("waqt", Context.MODE_PRIVATE)

    /** The profiles. Before 0.7.0 the app had 1 set of values ("values"): it becomes the profile "Default". */
    private val _profiles = MutableStateFlow(
        (WaqtProfileCodec.decode(prefs.getString(PROFILES, null)) ?: WaqtProfiles.first(WaqtCodec.decode(prefs.getString(VALUES, null))))
            .let { it.update(WaqtInput.merged(it.current.values, LocalDateTime.now())) },
    )
    val profiles: StateFlow<WaqtProfiles> = _profiles

    /** The values of the selected profile. */
    private val _values = MutableStateFlow(_profiles.value.current.values)
    val values: StateFlow<WaqtValues> = _values

    /** A different profile can be selected while the inputs are locked, but not during a send. */
    fun selectProfile(name: String) {
        if (gate.busy) return
        saveProfiles(_profiles.value.select(name))
        mergePast()
    }

    fun createProfile(name: String) = changeProfiles { it.create(name) }

    fun duplicateProfile(name: String) = changeProfiles { it.duplicate(name) }

    fun renameProfile(old: String, name: String) = changeProfiles { it.rename(old, name) }

    fun deleteProfile(name: String) = changeProfiles { it.delete(name) }

    private fun changeProfiles(change: (WaqtProfiles) -> WaqtProfiles) {
        if (!editable()) return
        saveProfiles(change(_profiles.value))
    }

    private fun saveProfiles(profiles: WaqtProfiles) {
        _profiles.value = profiles
        _values.value = profiles.current.values
        prefs.edit().putString(PROFILES, WaqtProfileCodec.encode(profiles)).apply()
    }

    fun setTime(prayer: Int, time: HourMinute) = update { it.copy(times = it.times.toMutableList().also { t -> t[prayer] = time }) }

    fun setChange(index: Int, change: WaqtChange) =
        update { it.copy(changes = it.changes.toMutableList().also { c -> c[index] = change }) }

    fun addChange(prayer: Int) = update { it.copy(changes = it.changes + WaqtInput.nextRow(it, prayer, LocalDate.now())) }

    fun removeChange(index: Int) = update { it.copy(changes = it.changes.toMutableList().also { c -> c.removeAt(index) }) }

    /** When the step opens: changes that already apply become "Today" (spec 3). Not an edit by the person. */
    fun mergePast() {
        if (gate.busy) return
        val merged = WaqtInput.merged(_values.value, LocalDateTime.now())
        if (merged != _values.value) save(merged)
    }

    private fun update(change: (WaqtValues) -> WaqtValues) {
        if (!editable()) return
        save(change(_values.value))
    }

    private fun save(values: WaqtValues) = saveProfiles(_profiles.value.update(values))

    override fun prepareSend(): SendJob? {
        val profile = _profiles.value.current.name
        val values = WaqtInput.merged(_values.value, LocalDateTime.now())
        if (!WaqtInput.isValid(values)) return null
        val now = LocalDateTime.now()
        val copies = WaqtSchedule.copies(values.times.map { it!! }, values.changes, now)
        return SendJob(
            makeExport = { HdsExport(WaqtM5.screen, WaqtProgram.make(copies)) },
            logHeader = { export -> waqtLogHeader(profile, values, copies.size, export.program.size) },
        )
    }

    private companion object {
        const val VALUES = "values"
        const val PROFILES = "profiles"
    }
}
