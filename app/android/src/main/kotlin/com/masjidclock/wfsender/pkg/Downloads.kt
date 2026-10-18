package com.masjidclock.wfsender.pkg

import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Saves a file without overwriting an existing file (spec section 5.3). */
object Downloads {

    /** [fileName] if it is free in [dir], otherwise "name (1).ext", "name (2).ext", and so on. */
    fun freeFile(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.')
        val extension = if ('.' in fileName) "." + fileName.substringAfterLast('.') else ""
        var file = File(dir, fileName)
        var number = 1
        while (file.exists()) {
            file = File(dir, "$base ($number)$extension")
            number++
        }
        return file
    }

    /** Writes a new file in [dir]. On failure, deletes the partial file and throws. */
    fun save(dir: File, fileName: String, write: (OutputStream) -> Unit): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val file = freeFile(dir, fileName)
        try {
            file.outputStream().use { write(it) }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        return file
    }
}
