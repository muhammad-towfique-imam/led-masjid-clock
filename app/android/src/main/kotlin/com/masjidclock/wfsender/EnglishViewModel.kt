package com.masjidclock.wfsender

import android.app.Application
import com.masjidclock.wfsender.display.DisplayTab
import com.masjidclock.wfsender.english.EnglishM5
import com.masjidclock.wfsender.protocol.HdsExport

/** The English tab: sends the stored m5 English program without a change. */
class EnglishViewModel(app: Application) : DisplayViewModel(app, DisplayTab.ENGLISH) {

    override fun prepareSend(): SendJob = SendJob(
        makeExport = { HdsExport(EnglishM5.screen, EnglishM5.program) },
        logHeader = { export -> listOf("Display: m5, 384 × 64, progam.hds ${export.program.size} bytes", "English program") },
    )
}
