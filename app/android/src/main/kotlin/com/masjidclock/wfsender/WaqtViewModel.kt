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

    private val _values = MutableStateFlow(
        WaqtInput.merged(WaqtCodec.decode(prefs.getString(VALUES, null)) ?: EMPTY, LocalDateTime.now()),
    )
    val values: StateFlow<WaqtValues> = _values

    fun setTime(prayer: Int, time: HourMinute) = update { it.copy(times = it.times.toMutableList().also { t -> t[prayer] = time }) }

    fun setChange(index: Int, change: WaqtChange) =
        update { it.copy(changes = it.changes.toMutableList().also { c -> c[index] = change }) }

    fun addChange(prayer: Int) = update { it.copy(changes = it.changes + WaqtInput.nextRow(it, prayer, LocalDate.now())) }

    fun removeChange(index: Int) = update { it.copy(changes = it.changes.toMutableList().also { c -> c.removeAt(index) }) }

    private fun update(change: (WaqtValues) -> WaqtValues) {
        if (gate.busy) return
        inputChanged()
        save(change(_values.value))
    }

    private fun save(values: WaqtValues) {
        _values.value = values
        prefs.edit().putString(VALUES, WaqtCodec.encode(values)).apply()
    }

    /** The values of the last send to the connected card, with its past changes moved to "Today". */
    override fun applyCardValues(text: String): Boolean {
        val card = WaqtCodec.decode(text) ?: return false
        save(WaqtInput.merged(card, LocalDateTime.now()))
        return true
    }

    override fun prepareSend(): SendJob? {
        val values = WaqtInput.merged(_values.value, LocalDateTime.now())
        if (!WaqtInput.isValid(values)) return null
        val now = LocalDateTime.now()
        val copies = WaqtSchedule.copies(values.times.map { it!! }, values.changes, now)
        return SendJob(
            makeExport = { HdsExport(WaqtM5.screen, WaqtProgram.make(copies)) },
            logHeader = { export -> waqtLogHeader(values, copies.size, export.program.size) },
            cardValues = WaqtCodec.encode(values),
        )
    }

    private companion object {
        const val VALUES = "values"
        val EMPTY = WaqtValues(List(6) { null }, emptyList())
    }
}
