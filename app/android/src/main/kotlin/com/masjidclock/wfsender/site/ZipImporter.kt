package com.masjidclock.wfsender.site

import com.masjidclock.wfsender.pkg.PackageName
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.InvalidExportException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

data class Card(val label: String, val dir: File, val width: Int, val height: Int, val programSize: Int)

data class Site(val name: String, val cards: List<Card>)

/** The ZIP cannot be used. The message is shown to the person. */
class ImportException(message: String) : Exception(message)

/** Unpacks a site ZIP with the program of 1 display into the private storage of the app (spec section 5.1). */
class ZipImporter(private val maxUnpackedBytes: Long = MAX_UNPACKED_BYTES) {

    /** Replaces the site in [siteDir] with the cards in [zip]. On failure, [siteDir] does not change. */
    fun import(zip: InputStream, zipName: String, siteDir: File): Site {
        if (!PackageName.hasExtension(zipName)) throw ImportException(NOT_PACKAGE)
        val name = siteName(zipName)
        val temp = File(siteDir.parentFile, siteDir.name + ".tmp")
        temp.deleteRecursively()
        temp.mkdirs()
        try {
            try {
                unpack(zip, temp)
            } catch (e: IOException) {
                throw ImportException("Could not read the ZIP file. Ask the sender for a new file.")
            } catch (e: IllegalArgumentException) {
                // ZipInputStream cannot decode names that are not UTF-8 (Windows "Compressed folder" with non-ASCII names).
                throw ImportException(
                    "The ZIP file has folder names that the app cannot read. " +
                        "Ask the sender to use only English letters and numbers in folder names."
                )
            }
            val site = scan(temp, name)
            if (site.cards.isEmpty()) {
                throw ImportException(
                    "The ZIP file has no display folders. Each folder must contain screen.hds and progam.hds."
                )
            }
            if (site.cards.size > 1) {
                throw ImportException(
                    "The ZIP file contains more than 1 display. Each ZIP file must contain the program of 1 display."
                )
            }
            siteDir.deleteRecursively()
            if (!temp.renameTo(siteDir)) throw ImportException("Could not save the files on the phone.")
            File(siteDir, NAME_FILE).writeText(name)
            return scan(siteDir, name)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun unpack(zip: InputStream, target: File) {
        var total = 0L
        val buffer = ByteArray(8192)
        ZipInputStream(zip).use { entries ->
            while (true) {
                val entry = entries.nextEntry ?: break
                val path = entry.name.replace('\\', '/')
                if (isUnsafe(path)) throw ImportException("The ZIP file contains an unsafe path: ${entry.name}")
                if (entry.isDirectory) continue
                val parts = path.split('/').filter { it.isNotEmpty() }
                if (parts.isEmpty() || "__MACOSX" in parts) continue
                val fileName = parts.last()
                if (fileName.startsWith("._")) continue
                val canonical = when (fileName.lowercase()) {
                    HdsExport.SCREEN_FILE -> HdsExport.SCREEN_FILE
                    HdsExport.PROGRAM_FILE -> HdsExport.PROGRAM_FILE
                    else -> continue
                }
                val dir = parts.dropLast(1).fold(target) { parent, part -> File(parent, part) }
                dir.mkdirs()
                File(dir, canonical).outputStream().use { out ->
                    while (true) {
                        val read = entries.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxUnpackedBytes) {
                            throw ImportException("The ZIP file is too large. The maximum is 20 MB.")
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private fun isUnsafe(path: String): Boolean =
        path.startsWith("/") || DRIVE.containsMatchIn(path) || path.split('/').any { it == ".." }

    companion object {
        const val MAX_UNPACKED_BYTES = 20L * 1024 * 1024
        const val NOT_PACKAGE = "This file is not an HD Sender package. Use a file that ends with .hdpkg."
        private const val NAME_FILE = ".site-name"
        private val DRIVE = Regex("^[A-Za-z]:")

        /** The site that was imported last, or null. */
        fun load(siteDir: File): Site? {
            val nameFile = File(siteDir, NAME_FILE)
            if (!nameFile.isFile) return null
            return try {
                scan(siteDir, nameFile.readText())
            } catch (e: ImportException) {
                null
            }
        }

        private fun siteName(zipName: String): String =
            PackageName.withoutExtension(zipName.substringAfterLast('/'))

        private fun scan(root: File, siteName: String): Site {
            val cards = root.walkTopDown()
                .filter { dir ->
                    dir.isDirectory &&
                        (File(dir, HdsExport.SCREEN_FILE).isFile || File(dir, HdsExport.PROGRAM_FILE).isFile)
                }
                .map { dir ->
                    val label = dir.relativeTo(root).invariantSeparatorsPath.ifEmpty { siteName }
                    val export = try {
                        HdsExport.fromDirectory(dir)
                    } catch (e: InvalidExportException) {
                        throw ImportException("The folder '$label' is not a valid HD2020 export: ${e.message}")
                    }
                    Card(label, dir, export.width, export.height, export.program.size)
                }
                .sortedBy { it.label }
                .toList()
            return Site(siteName, cards)
        }
    }
}
