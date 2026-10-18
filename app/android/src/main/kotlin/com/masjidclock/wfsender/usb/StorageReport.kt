package com.masjidclock.wfsender.usb

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

/** A text report of what the app sees on the storage volumes, for remote support. */
object StorageReport {

    fun make(context: Context, appVersion: String): String {
        val lines = mutableListOf<String>()
        lines += "HD Sender $appVersion, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
        lines += "All files access: ${StorageAccess.granted(context)}"
        val volumes = try {
            context.getSystemService(StorageManager::class.java).storageVolumes
        } catch (e: Exception) {
            lines += "storageVolumes failed: $e"
            emptyList()
        }
        lines += "Volumes: ${volumes.size}"
        volumes.forEachIndexed { i, volume ->
            lines += "[$i] ${safe { volume.getDescription(context) }}"
            lines += "    removable=${volume.isRemovable} primary=${volume.isPrimary} emulated=${volume.isEmulated}"
            lines += "    state=${volume.state} uuid=${volume.uuid}"
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) safe { volume.directory } else "n/a"
            lines += "    directory=$dir"
            if (dir is File) lines += "    " + listing(dir)
        }
        lines += "/storage: " + listing(File("/storage"))
        File("/storage").listFiles()?.filter { it.name != "self" && it.name != "emulated" }?.forEach {
            lines += "/storage/${it.name}: " + listing(it)
        }
        return lines.joinToString("\n")
    }

    private fun listing(dir: File): String = try {
        val files = dir.listFiles()
        when {
            files == null -> "exists=${dir.exists()} canRead=${dir.canRead()} listFiles=null"
            else -> "files=${files.size}: " + files.take(20).joinToString(", ") {
                if (it.isFile) "${it.name} (${it.length()} B)" else "${it.name}/"
            }
        }
    } catch (e: Exception) {
        "error: $e"
    }

    private fun safe(block: () -> Any?): Any? = try {
        block()
    } catch (e: Exception) {
        "error: $e"
    }
}
