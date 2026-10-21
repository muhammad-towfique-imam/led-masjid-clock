package com.masjidclock.wfsender

import android.app.Application
import android.content.Context
import com.masjidclock.wfsender.bangla.BanglaM5
import com.masjidclock.wfsender.bangla.BanglaProgram
import com.masjidclock.wfsender.bangla.BanglaSettings
import com.masjidclock.wfsender.bangla.banglaLogHeader
import com.masjidclock.wfsender.display.DisplayTab
import com.masjidclock.wfsender.hijri.HijriInput
import com.masjidclock.wfsender.protocol.HdsExport
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The Bangla tab: makes the m5 Bangla program from the Bangla year and sends it. */
class BanglaViewModel(app: Application) : DisplayViewModel(app, DisplayTab.BANGLA) {

    private val prefs = app.getSharedPreferences("bangla", Context.MODE_PRIVATE)
    private val settings = BanglaSettings(
        read = { prefs.getString(it, null) },
        write = { key, value -> prefs.edit().putString(key, value).apply() },
    )

    private val _yearText = MutableStateFlow(settings.load(LocalDate.now()))
    val yearText: StateFlow<String> = _yearText

    /** Saves the year only when it is valid. */
    fun setYearText(text: String) {
        if (!editable()) return
        _yearText.value = text
        HijriInput.parseYear(text)?.let(settings::saveYear)
    }

    override fun prepareSend(): SendJob? {
        val year = HijriInput.parseYear(_yearText.value) ?: return null
        return SendJob(
            makeExport = { HdsExport(BanglaM5.screen, BanglaProgram.make(BanglaM5.base(), BanglaM5.digits, year)) },
            logHeader = { export -> banglaLogHeader(year, export.program.size) },
        )
    }
}
