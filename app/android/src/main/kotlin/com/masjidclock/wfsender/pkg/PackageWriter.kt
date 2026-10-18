package com.masjidclock.wfsender.pkg

import com.masjidclock.wfsender.protocol.HdsExport
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes an HD2020 export as an .hdpkg package (spec section 5.2). Closes the stream. */
object PackageWriter {
    fun write(export: HdsExport, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(HdsExport.SCREEN_FILE))
            zip.write(export.screen)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(HdsExport.PROGRAM_FILE))
            zip.write(export.program)
            zip.closeEntry()
        }
    }
}
