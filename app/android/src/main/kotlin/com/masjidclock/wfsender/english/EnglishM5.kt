package com.masjidclock.wfsender.english

import com.masjidclock.wfsender.hijri.HijriDataException

/** The m5 English program (384 × 64): the HD2020 export, sent without a change. The card draws the date and time. */
object EnglishM5 {
    val screen: ByteArray by lazy { read("screen.hds") }
    val program: ByteArray by lazy { read("progam.hds") }

    private fun read(name: String): ByteArray =
        EnglishM5::class.java.getResourceAsStream("/english/m5/$name")?.use { it.readBytes() }
            ?: throw HijriDataException("$name is missing")
}
