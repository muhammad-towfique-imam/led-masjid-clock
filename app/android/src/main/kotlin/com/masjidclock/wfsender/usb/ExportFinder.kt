package com.masjidclock.wfsender.usb

import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.InvalidExportException
import java.io.File
import java.io.IOException

enum class UsbStatus { NO_PERMISSION, NO_DRIVE, NO_EXPORT, FOUND }

data class FoundExport(val root: File, val export: HdsExport)

/** Finds the HD2020 U-disk export at the root of a removable volume (spec section 4.2, step 3). */
object ExportFinder {

    fun find(roots: List<File>): FoundExport? =
        roots.firstNotNullOfOrNull { root -> load(root)?.let { FoundExport(root, it) } }

    private fun load(root: File): HdsExport? {
        val files = root.listFiles() ?: return null
        val screen = files.firstOrNull { it.isFile && it.name.equals(HdsExport.SCREEN_FILE, ignoreCase = true) }
        val program = files.firstOrNull { it.isFile && it.name.equals(HdsExport.PROGRAM_FILE, ignoreCase = true) }
        if (screen == null || program == null) return null
        return try {
            HdsExport(screen.readBytes(), program.readBytes())
        } catch (e: InvalidExportException) {
            null
        } catch (e: IOException) {
            null
        }
    }
}
