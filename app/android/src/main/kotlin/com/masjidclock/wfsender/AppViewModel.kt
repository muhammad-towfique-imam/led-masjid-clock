package com.masjidclock.wfsender

import android.app.Application
import android.content.ContentResolver
import android.net.Network
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.net.CardNetwork
import com.masjidclock.wfsender.net.ConnectionCheck
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.protocol.CardClock
import com.masjidclock.wfsender.protocol.CardSender
import com.masjidclock.wfsender.protocol.ClockFailure
import com.masjidclock.wfsender.protocol.ClockResult
import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.SafeSender
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendGate
import com.masjidclock.wfsender.send.SendLog
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.send.TimeStep
import com.masjidclock.wfsender.send.sendCard
import com.masjidclock.wfsender.send.sendCardSafe
import com.masjidclock.wfsender.site.Card
import com.masjidclock.wfsender.site.ImportException
import com.masjidclock.wfsender.site.Site
import com.masjidclock.wfsender.site.ZipImporter
import com.masjidclock.wfsender.time.WallClock
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The 2 tabs of the bottom navigation bar. */
enum class Page { SEND, CREATE, TIME }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val siteDir = File(app.filesDir, "site")
    private val importer = ZipImporter()
    private val network = CardNetwork(app).also { it.start() }
    private var checkJob: Job? = null
    private var check: ConnectionCheck<Network>? = null
    private val gate = SendGate()

    private val _page = MutableStateFlow(Page.SEND)
    val page: StateFlow<Page> = _page

    private val _site = MutableStateFlow(ZipImporter.load(siteDir))
    val site: StateFlow<Site?> = _site

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError

    private val _selectedCard = MutableStateFlow<Card?>(null)
    val selectedCard: StateFlow<Card?> = _selectedCard

    private val _connection = MutableStateFlow(ConnectionStatus.NOT_CONNECTED)
    val connection: StateFlow<ConnectionStatus> = _connection

    private val _cardName = MutableStateFlow<String?>(null)
    val cardName: StateFlow<String?> = _cardName

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    private val _timeStep = MutableStateFlow<TimeStep>(TimeStep.Pending)
    val timeStep: StateFlow<TimeStep> = _timeStep

    init {
        // The app opens on the Send tab. A package holds 1 display, so it is selected without an input.
        _site.value?.cards?.firstOrNull()?.let(::selectCard)
    }

    /** Opens the Send tab. Nothing happens if it is already open, so a result stays on the screen. */
    fun openSend() {
        if (_page.value == Page.SEND) return
        _page.value = Page.SEND
        // selectCard does nothing during a send, so an "Open with" during a send cannot change the card.
        _site.value?.cards?.firstOrNull()?.let(::selectCard)
    }

    fun openTime() {
        if (gate.busy) return
        stopConnectionChecks()
        _page.value = Page.TIME
    }

    fun openCreate() {
        if (gate.busy) return
        stopConnectionChecks()
        _page.value = Page.CREATE
    }

    fun importPackage(uri: Uri) {
        if (gate.busy) {
            _importError.value = Messages.WAIT_FOR_SEND
            return
        }
        openSend()
        viewModelScope.launch {
            _importError.value = null
            val resolver = getApplication<Application>().contentResolver
            try {
                val imported = withContext(Dispatchers.IO) {
                    val name = displayName(resolver, uri) ?: ""
                    val input = resolver.openInputStream(uri) ?: throw ImportException("Could not open the file.")
                    input.use { importer.import(it, name, siteDir) }
                }
                _site.value = imported
                selectCard(imported.cards.first())
            } catch (e: ImportException) {
                _importError.value = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // For example FileNotFoundException when the sharing app no longer gives access to the file.
                _importError.value = "Could not open the file."
            }
        }
    }

    private fun selectCard(card: Card) {
        if (gate.busy) return
        _selectedCard.value = card
        _sendState.value = SendState.Idle
        _timeStep.value = TimeStep.Pending
        stopConnectionChecks()
        startConnectionChecks()
    }

    /** After a result: back to step 2 for the same display. The connection check starts again. */
    fun sendAgain() {
        _selectedCard.value?.let(::selectCard)
    }

    private fun startConnectionChecks() {
        _connection.value = ConnectionStatus.NOT_CONNECTED
        val newCheck = ConnectionCheck({ network.current }) { wifi ->
            val found = CardSender(network.connectorFor(wifi)).probe()
            // The card name for the screen: 1 short UDP request. A card that does not answer keeps "Display found".
            _cardName.value = if (found) CardClock(network.udpFor(wifi), timeoutMs = 1_500, tries = 1).discover()?.name else null
            found
        }
        check = newCheck
        checkJob = viewModelScope.launch {
            while (isActive) {
                val status = withContext(Dispatchers.IO) { newCheck.next() } ?: break
                _connection.value = status
                if (status != ConnectionStatus.DISPLAY_FOUND) _cardName.value = null
                // A time result belongs to the display on this WiFi only.
                if (status != ConnectionStatus.DISPLAY_FOUND && _timeStep.value != TimeStep.Busy) {
                    _timeStep.value = TimeStep.Pending
                }
                delay(2_000)
            }
        }
    }

    /** No more probes: after a send, each probe makes the display flicker. */
    private fun stopConnectionChecks() {
        check?.stop()
        check = null
        checkJob?.cancel()
        checkJob = null
    }

    /** Step 3: sets the display clock to the phone's local time. */
    fun setTime() {
        val wifi = network.current ?: return
        if (_timeStep.value == TimeStep.Busy || gate.busy) return
        _timeStep.value = TimeStep.Busy
        viewModelScope.launch {
            val wall = WallClock.now()
            val result = try {
                withContext(Dispatchers.IO) { CardClock(network.udpFor(wifi)).setTime(wall) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClockResult.Failed(ClockFailure.NETWORK, null)
            }
            val message = Messages.forClock(result, WallClock.time(wall))
            _timeStep.value = if (result is ClockResult.Success) TimeStep.Set(message) else TimeStep.Failed(message)
        }
    }

    fun skipTime() {
        if (_timeStep.value != TimeStep.Busy) _timeStep.value = TimeStep.Skipped
    }

    fun send() {
        val card = _selectedCard.value ?: return
        val wifi = network.current ?: return
        if (!_timeStep.value.done) return
        if (!gate.tryStart()) return
        stopConnectionChecks()
        _sendState.value = SendState.Sending(0, 0)
        val log = SendLog(
            siteName = _site.value?.name ?: "",
            card = card,
            appVersion = BuildConfig.VERSION_NAME,
            androidVersion = Build.VERSION.RELEASE,
        )
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val sender = CardSender(network.connectorFor(wifi), isNetworkLost = { network.current != wifi })
                    sendCard(card, sender, progress(log))
                }
                if (result is SendResult.Failed && result.reason == FailureReason.SAFE_REQUIRED) {
                    log.note("The card refused the normal form at the start packet (firmware V4.45). Safe form.")
                    sendSafe(card, wifi, log)
                } else {
                    finishSend(result, log)
                }
            } finally {
                gate.finish()
            }
        }
    }

    private suspend fun sendSafe(card: Card, wifi: Network, log: SendLog) {
        _sendState.value = SendState.Sending(0, 0)
        val result = withContext(Dispatchers.IO) {
            sendCardSafe(card, SafeSender(network.udpFor(wifi)), progress(log))
        }
        finishSend(result, log)
    }

    private fun finishSend(result: SendResult, log: SendLog) {
        log.finish(result)
        _sendState.value = SendState.Finished(result == SendResult.Success, Messages.forResult(result), log.text())
    }

    private fun progress(log: SendLog) = object : SendListener by log {
        override fun onFrameSent(index: Int, total: Int, frame: ByteArray) {
            log.onFrameSent(index, total, frame)
            _sendState.value = SendState.Sending(index, total)
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    override fun onCleared() {
        stopConnectionChecks()
        network.stop()
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment
}
