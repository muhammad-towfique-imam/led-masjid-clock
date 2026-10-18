package com.masjidclock.wfsender.usb

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class Volume(val name: String, val root: File)

/** The mounted removable volumes: USB drives and SD cards (spec section 5.1). */
class RemovableVolumes(private val context: Context) {

    fun list(): List<Volume> {
        val storage = context.getSystemService(StorageManager::class.java)
        return storage.storageVolumes
            .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            .mapNotNull { volume -> rootOf(volume)?.let { Volume(volume.getDescription(context), it) } }
    }

    private fun rootOf(volume: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return volume.directory
        // Android 7-10: a removable volume is mounted at /storage/<UUID>.
        val uuid = volume.uuid ?: return null
        return context.getExternalFilesDirs(null).filterNotNull()
            .map { File(it.path.substringBefore("/Android/data/")) }
            .firstOrNull { it.name.equals(uuid, ignoreCase = true) }
    }
}
