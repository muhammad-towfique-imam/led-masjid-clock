package com.masjidclock.wfsender

import android.app.Application
import android.content.Context
import com.masjidclock.wfsender.display.DisplayTab
import com.masjidclock.wfsender.hijri.HijriInput
import com.masjidclock.wfsender.hijri.HijriM5
import com.masjidclock.wfsender.hijri.HijriMonths
import com.masjidclock.wfsender.hijri.HijriProgram
import com.masjidclock.wfsender.hijri.HijriSettings
import com.masjidclock.wfsender.hijri.HijriStart
import com.masjidclock.wfsender.hijri.HijriValues
import com.masjidclock.wfsender.hijri.hijriLogHeader
import com.masjidclock.wfsender.protocol.HdsExport
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Hijri tab (spec 2026-10-20-masjid-clock-hijri-design.md): makes the m5 Hijri program on the phone and sends it.
 * The connection, time, and send flow is in [DisplayViewModel].
 */
class HijriViewModel(app: Application) : DisplayViewModel(app, DisplayTab.HIJRI) {

    private val prefs = app.getSharedPreferences("hijri", Context.MODE_PRIVATE)
    private val settings = HijriSettings(
        read = { prefs.getString(it, null) },
        write = { key, value -> prefs.edit().putString(key, value).apply() },
    )

    private val _values = MutableStateFlow(settings.load(LocalDate.now()))
    val values: StateFlow<HijriValues> = _values

    fun setDate(date: LocalDate) {
        if (gate.busy) return
        _values.value = _values.value.copy(monthStart = date)
        settings.saveDate(date)
    }

    fun setMonth(month: Int) {
        if (gate.busy) return
        _values.value = _values.value.copy(month = month)
        settings.saveMonth(month)
    }

    fun setYearText(text: String) {
        if (gate.busy) return
        _values.value = _values.value.copy(yearText = text)
        HijriInput.parseYear(text)?.let(settings::saveYear)
    }

    fun setShowDivider(show: Boolean) {
        if (gate.busy) return
        _values.value = _values.value.copy(showDivider = show)
        settings.saveShowDivider(show)
    }

    override fun prepareSend(): SendJob? {
        val input = _values.value
        val month = input.month ?: return null
        val year = HijriInput.parseYear(input.yearText) ?: return null
        val counterStart = HijriStart.counterStart(input.monthStart, LocalDate.now())
        return SendJob(
            makeExport = {
                HdsExport(
                    HijriM5.screen,
                    HijriProgram.make(HijriM5.base(month), HijriM5.digits, year, counterStart, showDivider = input.showDivider),
                )
            },
            logHeader = { export ->
                hijriLogHeader(
                    HijriMonths.NAMES[month - 1], year, input.monthStart, counterStart, export.program.size,
                    input.showDivider,
                )
            },
        )
    }
}
