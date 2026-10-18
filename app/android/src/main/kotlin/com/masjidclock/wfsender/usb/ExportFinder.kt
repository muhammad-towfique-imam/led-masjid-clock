package com.masjidclock.wfsender.usb

import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.InvalidExportException
import com.masjidclock.wfsender.site.ZipImporter
import java.io.File
import java.io.IOException

enum class UsbStatus { NO_PERMISSION, NO_DRIVE, NO_EXPORT, TOO_LARGE, FOUND }

/** [fingerprint] is the size and time of both files, to find out if they changed. */
data class FoundExport(val root: File, val export: HdsExport, val fingerprint: List<Long>)

sealed interface ExportSearch {
    data object None : ExportSearch
    data class TooLarge(val root: File) : ExportSearch
    data class Found(val found: FoundExport) : ExportSearch
}

/** Finds the HD2020 U-disk export at the root of a removable volume (spec section 4.2, step 3). */
object ExportFinder {

    /** The Send flow rejects packages larger than this, so Create must not make them. */
    const val DEFAULT_MAX_BYTES = ZipImporter.MAX_UNPACKED_BYTES

    /**
     * The first root with an export. The files are read only if they are not larger than [maxBytes],
     * and only if they changed since [previous] (the check runs every 2 seconds).
     */
    fun find(roots: List<File>, maxBytes: Long = DEFAULT_MAX_BYTES, previous: FoundExport? = null): ExportSearch {
        for (root in roots) {
            val result = search(root, maxBytes, previous)
            if (result != ExportSearch.None) return result
        }
        return ExportSearch.None
    }

    private fun search(root: File, maxBytes: Long, previous: FoundExport?): ExportSearch {
        val files = root.listFiles() ?: return ExportSearch.None
        val screen = files.firstOrNull { it.isFile && it.name.equals(HdsExport.SCREEN_FILE, ignoreCase = true) }
        val program = files.firstOrNull { it.isFile && it.name.equals(HdsExport.PROGRAM_FILE, ignoreCase = true) }
        if (screen == null || program == null) return ExportSearch.None
        if (screen.length() + program.length() > maxBytes) return ExportSearch.TooLarge(root)
        val fingerprint = listOf(screen.length(), screen.lastModified(), program.length(), program.lastModified())
        if (previous != null && previous.root == root && previous.fingerprint == fingerprint) {
            return ExportSearch.Found(previous)
        }
        return try {
            ExportSearch.Found(FoundExport(root, HdsExport(screen.readBytes(), program.readBytes()), fingerprint))
        } catch (e: InvalidExportException) {
            ExportSearch.None
        } catch (e: IOException) {
            ExportSearch.None
        }
    }
}
