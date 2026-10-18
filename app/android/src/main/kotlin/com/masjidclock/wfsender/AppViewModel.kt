package com.masjidclock.wfsender

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.net.CardNetwork
import com.masjidclock.wfsender.protocol.CardSender
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendLog
import com.masjidclock.wfsender.site.Card
import com.masjidclock.wfsender.site.ImportException
import com.masjidclock.wfsender.site.Site
import com.masjidclock.wfsender.site.ZipImporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConnectionStatus { NOT_CONNECTED, NO_DISPLAY, DISPLAY_FOUND }

sealed interface SendState {
    data object Idle : SendState
    data class Sending(val sent: Int, val total: Int) : SendState
    data class Finished(val success: Boolean, val message: String, val log: String) : SendState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val siteDir = File(app.filesDir, "site")
    private val importer = ZipImporter()
    private val network = CardNetwork(app).also { it.start() }
    private var checkJob: Job? = null

    private val _site = MutableStateFlow(ZipImporter.load(siteDir))
    val site: StateFlow<Site?> = _site

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError

    private val _selectedCard = MutableStateFlow<Card?>(null)
    val selectedCard: StateFlow<Card?> = _selectedCard

    private val _connection = MutableStateFlow(ConnectionStatus.NOT_CONNECTED)
    val connection: StateFlow<ConnectionStatus> = _connection

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    fun importZip(uri: Uri) {
        viewModelScope.launch {
            _importError.value = null
            val resolver = getApplication<Application>().contentResolver
            try {
                val imported = withContext(Dispatchers.IO) {
                    val name = displayName(resolver, uri) ?: "site.zip"
                    val input = resolver.openInputStream(uri) ?: throw ImportException("Could not open the file.")
                    input.use { importer.import(it, name, siteDir) }
                }
                _site.value = imported
                selectCard(null)
            } catch (e: ImportException) {
                _importError.value = e.message
            } catch (e: SecurityException) {
                _importError.value = "Could not open the file."
            }
        }
    }

    fun selectCard(card: Card?) {
        _selectedCard.value = card
        _sendState.value = SendState.Idle
        checkJob?.cancel()
        if (card != null) startConnectionChecks()
    }

    private fun startConnectionChecks() {
        checkJob = viewModelScope.launch {
            while (isActive) {
                if (_sendState.value !is SendState.Sending) {
                    _connection.value = withContext(Dispatchers.IO) { checkConnection() }
                }
                delay(2_000)
            }
        }
    }

    private fun checkConnection(): ConnectionStatus {
        val wifi = network.current ?: return ConnectionStatus.NOT_CONNECTED
        val found = CardSender(network.connectorFor(wifi)).probe()
        return if (found) ConnectionStatus.DISPLAY_FOUND else ConnectionStatus.NO_DISPLAY
    }

    fun send() {
        val card = _selectedCard.value ?: return
        val wifi = network.current ?: return
        if (_sendState.value is SendState.Sending) return
        _sendState.value = SendState.Sending(0, 0)
        viewModelScope.launch {
            val log = SendLog(
                siteName = _site.value?.name ?: "",
                card = card,
                appVersion = BuildConfig.VERSION_NAME,
                androidVersion = Build.VERSION.RELEASE,
            )
            val progress = object : SendListener by log {
                override fun onFrameSent(index: Int, total: Int, frame: ByteArray) {
                    log.onFrameSent(index, total, frame)
                    _sendState.value = SendState.Sending(index, total)
                }
            }
            val result = withContext(Dispatchers.IO) {
                val sender = CardSender(network.connectorFor(wifi), isNetworkLost = { network.current != wifi })
                sender.send(HdsExport.fromDirectory(card.dir), progress)
            }
            log.finish(result)
            _sendState.value = SendState.Finished(result == SendResult.Success, Messages.forResult(result), log.text())
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    override fun onCleared() {
        network.stop()
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment
}
