package com.masjidclock.wfsender.protocol

import java.io.File

/** The export is not a valid HD2020 U-disk export. */
class InvalidExportException(message: String) : Exception(message)

/** One card of an HD2020 U-disk export: screen.hds + progam.hds (spec section 3.1). */
class HdsExport(val screen: ByteArray, val program: ByteArray) {

    init {
        if (screen.size != SCREEN_SIZE) {
            throw InvalidExportException("$SCREEN_FILE must be $SCREEN_SIZE bytes, but it is ${screen.size} bytes")
        }
        if (!screen.startsWith("HS")) throw InvalidExportException("$SCREEN_FILE does not start with HS")
        if (program.size < MIN_PROGRAM_SIZE) throw InvalidExportException("$PROGRAM_FILE is too short")
        if (!program.startsWith("HC")) throw InvalidExportException("$PROGRAM_FILE does not start with HC")
    }

    val width: Int get() = screen.u16(4)
    val height: Int get() = screen.u16(6)

    companion object {
        const val SCREEN_FILE = "screen.hds"
        const val PROGRAM_FILE = "progam.hds"
        const val SCREEN_SIZE = 66

        // The chunk count is written at bytes 9-10, so the program needs at least 11 bytes.
        private const val MIN_PROGRAM_SIZE = 11

        fun fromDirectory(dir: File): HdsExport {
            val screenFile = File(dir, SCREEN_FILE)
            val programFile = File(dir, PROGRAM_FILE)
            if (!screenFile.isFile) throw InvalidExportException("$SCREEN_FILE is missing")
            if (!programFile.isFile) throw InvalidExportException("$PROGRAM_FILE is missing")
            return HdsExport(screenFile.readBytes(), programFile.readBytes())
        }
    }
}

private fun ByteArray.startsWith(prefix: String): Boolean =
    size >= 2 && this[0] == prefix[0].code.toByte() && this[1] == prefix[1].code.toByte()

private fun ByteArray.u16(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
