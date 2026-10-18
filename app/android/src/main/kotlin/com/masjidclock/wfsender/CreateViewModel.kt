package com.masjidclock.wfsender

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.pkg.Downloads
import com.masjidclock.wfsender.pkg.PackageName
import com.masjidclock.wfsender.pkg.PackageWriter
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.usb.ExportFinder
import com.masjidclock.wfsender.usb.ExportSearch
import com.masjidclock.wfsender.usb.FoundExport
import com.masjidclock.wfsender.usb.RemovableVolumes
import com.masjidclock.wfsender.usb.StorageAccess
import com.masjidclock.wfsender.usb.StorageReport
import com.masjidclock.wfsender.usb.UsbStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsbState(val status: UsbStatus, val volumeName: String = "", val export: HdsExport? = null)

/** The Create page (spec section 4.2). */
class CreateViewModel(app: Application) : AndroidViewModel(app) {

    private val volumes = RemovableVolumes(app)
    private var checkJob: Job? = null
    private var lastFound: FoundExport? = null

    private val _usb = MutableStateFlow(UsbState(UsbStatus.NO_PERMISSION))
    val usb: StateFlow<UsbState> = _usb

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result

    /** Checks the permission and the USB drive every 2 seconds while the page is visible. */
    fun startChecks() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            while (isActive) {
                _usb.value = try {
                    withContext(Dispatchers.IO) { check() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // For example a storage API error while the drive is removed. Never crash the app.
                    UsbState(UsbStatus.NO_DRIVE)
                }
                delay(2_000)
            }
        }
    }

    fun stopChecks() {
        checkJob?.cancel()
        checkJob = null
    }

    private fun check(): UsbState {
        if (!StorageAccess.granted(getApplication())) return UsbState(UsbStatus.NO_PERMISSION)
        val list = volumes.list()
        if (list.isEmpty()) return UsbState(UsbStatus.NO_DRIVE)
        return when (val search = ExportFinder.find(list.map { it.root }, previous = lastFound)) {
            ExportSearch.None -> UsbState(UsbStatus.NO_EXPORT)
            is ExportSearch.TooLarge -> UsbState(UsbStatus.TOO_LARGE)
            is ExportSearch.Found -> {
                lastFound = search.found
                val name = list.first { it.root == search.found.root }.name
                UsbState(UsbStatus.FOUND, name, search.found.export)
            }
        }
    }

    /** [name] is a clean name (PackageName.clean). */
    fun save(name: String) {
        val export = _usb.value.export ?: return
        viewModelScope.launch {
            _result.value = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = Downloads.save(dir, PackageName.fileName(name)) { PackageWriter.write(export, it) }
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(file.absolutePath), null, null)
                    Messages.saved(file.name)
                } catch (e: IOException) {
                    Messages.SAVE_FAILED
                } catch (e: SecurityException) {
                    Messages.SAVE_FAILED
                }
            }
        }
    }

    /** Writes the package to the cache folder and returns a shareable Uri, or null on failure. */
    suspend fun shareFile(name: String): Uri? {
        val export = _usb.value.export ?: return null
        val app = getApplication<Application>()
        return withContext(Dispatchers.IO) {
            try {
                val dir = File(app.cacheDir, "share")
                dir.deleteRecursively()
                dir.mkdirs()
                val file = File(dir, PackageName.fileName(name))
                PackageWriter.write(export, file.outputStream())
                FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            } catch (e: IOException) {
                _result.value = Messages.SAVE_FAILED
                null
            }
        }
    }

    /** A text report of the storage volumes, for remote support. */
    suspend fun storageReport(): String =
        withContext(Dispatchers.IO) { StorageReport.make(getApplication(), BuildConfig.VERSION_NAME) }

    override fun onCleared() {
        stopChecks()
    }
}
