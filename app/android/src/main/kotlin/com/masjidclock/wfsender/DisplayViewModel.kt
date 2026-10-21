package com.masjidclock.wfsender

import android.app.Application
import android.content.Context
import android.net.Network
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.display.CardList
import com.masjidclock.wfsender.display.DisplayTab
import com.masjidclock.wfsender.display.NearbyDisplay
import com.masjidclock.wfsender.display.nearbyDisplays
import com.masjidclock.wfsender.display.scanSummary
import com.masjidclock.wfsender.hijri.HijriDataException
import com.masjidclock.wfsender.net.CardNetwork
import com.masjidclock.wfsender.net.ConnectionCheck
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.net.ScanState
import com.masjidclock.wfsender.net.WifiScanner
import com.masjidclock.wfsender.protocol.CardClock
import com.masjidclock.wfsender.protocol.CardSender
import com.masjidclock.wfsender.protocol.ClockFailure
import com.masjidclock.wfsender.protocol.ClockResult
import com.masjidclock.wfsender.protocol.FailureReason
import com.masjidclock.wfsender.protocol.HdsExport
import com.masjidclock.wfsender.protocol.SafeSender
import com.masjidclock.wfsender.protocol.SendListener
import com.masjidclock.wfsender.protocol.SendResult
import com.masjidclock.wfsender.send.Messages
import com.masjidclock.wfsender.send.SendGate
import com.masjidclock.wfsender.send.SendLog
import com.masjidclock.wfsender.send.SendState
import com.masjidclock.wfsender.send.TimeStep
import com.masjidclock.wfsender.time.WallClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a tab sends: the program is made when Send is tapped, and the log header describes it. */
class SendJob(val makeExport: () -> HdsExport, val logHeader: (HdsExport) -> List<String>)

/**
 * The flow of the English, Bangla, and Hijri tabs: the connection check, the time step, the send (normal form, then
 * the safe form), and the card list (display-tabs spec section 5A). Moved from HijriViewModel without a change.
 */
abstract class DisplayViewModel(app: Application, val tab: DisplayTab) : AndroidViewModel(app) {

    private val network = CardNetwork(app).also { it.start() }
    private var checkJob: Job? = null
    private var check: ConnectionCheck<Network>? = null
    private val scanner = WifiScanner(app)
    private var scanJob: Job? = null

    /** The card WiFi networks near the phone, for the list "Displays nearby". */
    private val _nearby = MutableStateFlow<List<NearbyDisplay>>(emptyList())
    val nearby: StateFlow<List<NearbyDisplay>> = _nearby

    /** The diagnostic line of the scan (shown when no card is in the list). */
    private val _scanSummary = MutableStateFlow("")
    val scanSummary: StateFlow<String> = _scanSummary

    private val _scanState = MutableStateFlow(ScanState.NO_PERMISSION)
    val scanState: StateFlow<ScanState> = _scanState
    protected val gate = SendGate.APP
    private val cards = app.getSharedPreferences("cards", Context.MODE_PRIVATE)
    private val locks = app.getSharedPreferences("locks", Context.MODE_PRIVATE)

    /** The lock of the inputs page: against accidental changes. Saved for each tab. */
    private val _locked = MutableStateFlow(locks.getBoolean(tab.name, false))
    val locked: StateFlow<Boolean> = _locked

    fun setLocked(locked: Boolean) {
        _locked.value = locked
        locks.edit().putBoolean(tab.name, locked).apply()
    }

    /** The inputs can change: no send runs, and the page is not locked. */
    protected fun editable(): Boolean = !gate.busy && !_locked.value
    private val cardList = CardList(read = { cards.getString(LIST, null) }, write = { cards.edit().putString(LIST, it).apply() })

    private val _connection = MutableStateFlow(ConnectionStatus.NOT_CONNECTED)
    val connection: StateFlow<ConnectionStatus> = _connection

    private val _cardName = MutableStateFlow<String?>(null)
    val cardName: StateFlow<String?> = _cardName

    /** The tab of the last successful send to the connected card, or null. */
    private val _cardTab = MutableStateFlow<DisplayTab?>(null)
    val cardTab: StateFlow<DisplayTab?> = _cardTab

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    private val _timeStep = MutableStateFlow<TimeStep>(TimeStep.Pending)
    val timeStep: StateFlow<TimeStep> = _timeStep

    /** The job of a send, or null if the inputs of the tab are not valid. Called on the main thread. */
    protected abstract fun prepareSend(): SendJob?

    /**
     * While the tab is visible. Not during or after a send: after a send, each probe makes the display flicker.
     * [sendAgain] starts the checks again.
     */
    fun startChecks() {
        if (_sendState.value != SendState.Idle) return
        stopChecks()
        // The phone's WiFi scan results: they do not contact the cards.
        scanner.startScan()
        scanJob = viewModelScope.launch {
            while (isActive) {
                refreshNearby()
                delay(3_000)
            }
        }
        _connection.value = ConnectionStatus.NOT_CONNECTED
        val newCheck = ConnectionCheck({ network.current }) { wifi ->
            val found = CardSender(network.connectorFor(wifi)).probe()
            _cardName.value = if (found) CardClock(network.udpFor(wifi), timeoutMs = 1_500, tries = 1).discover()?.name else null
            _cardTab.value = cardList.tabOf(_cardName.value)
            found
        }
        check = newCheck
        checkJob = viewModelScope.launch {
            while (isActive) {
                val status = withContext(Dispatchers.IO) { newCheck.next() } ?: break
                _connection.value = status
                if (status != ConnectionStatus.DISPLAY_FOUND) {
                    _cardName.value = null
                    _cardTab.value = null
                }
                if (status != ConnectionStatus.DISPLAY_FOUND && _timeStep.value != TimeStep.Busy) {
                    _timeStep.value = TimeStep.Pending
                }
                delay(2_000)
            }
        }
    }

    fun stopChecks() {
        scanJob?.cancel()
        scanJob = null
        check?.stop()
        check = null
        checkJob?.cancel()
        checkJob = null
    }

    /** After the person answers the permission question. */
    fun permissionChanged() {
        scanner.startScan()
        refreshNearby()
    }

    private fun refreshNearby() {
        _scanState.value = scanner.state()
        val seen = scanner.results()
        _nearby.value = nearbyDisplays(seen, tab) { cardList.tabOf(it) }
        _scanSummary.value = scanSummary(seen, _nearby.value.size, scanner.locationOn())
    }

    /** After a result: back to step 2 for the same display. */
    fun sendAgain() {
        if (gate.busy) return
        _sendState.value = SendState.Idle
        _timeStep.value = TimeStep.Pending
        startChecks()
    }

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
        val wifi = network.current ?: return
        if (_connection.value != ConnectionStatus.DISPLAY_FOUND || !_timeStep.value.done) return
        val job = prepareSend() ?: return
        if (!gate.tryStart()) return
        stopChecks()
        _sendState.value = SendState.Sending(0, 0)
        viewModelScope.launch {
            try {
                val export = try {
                    withContext(Dispatchers.Default) { job.makeExport() }
                } catch (e: HijriDataException) {
                    _sendState.value = SendState.Finished(false, DATA_DAMAGED, "Masjid Clock: ${e.message}")
                    return@launch
                }
                val log = SendLog(
                    header = job.logHeader(export),
                    appVersion = BuildConfig.VERSION_NAME,
                    androidVersion = Build.VERSION.RELEASE,
                )
                val result = withContext(Dispatchers.IO) {
                    val sender = CardSender(network.connectorFor(wifi), isNetworkLost = { network.current != wifi })
                    sender.send(export, progress(log))
                }
                if (result is SendResult.Failed && result.reason == FailureReason.SAFE_REQUIRED) {
                    log.note("The card refused the normal form at the start packet (firmware V4.45). Safe form.")
                    _sendState.value = SendState.Sending(0, 0)
                    val safe = withContext(Dispatchers.IO) { SafeSender(network.udpFor(wifi)).send(export, progress(log)) }
                    finishSend(safe, log, job)
                } else {
                    finishSend(result, log, job)
                }
            } finally {
                gate.finish()
            }
        }
    }

    private fun finishSend(result: SendResult, log: SendLog, job: SendJob) {
        log.finish(result)
        val name = _cardName.value
        if (result == SendResult.Success) {
            cardList.remember(name, tab)
            // After a successful send, the inputs lock by themselves (user, 2026-10-21).
            setLocked(true)
            _cardTab.value = cardList.tabOf(name)
        }
        _sendState.value = SendState.Finished(result == SendResult.Success, Messages.forResult(result), log.text())
    }

    private fun progress(log: SendLog) = object : SendListener by log {
        override fun onFrameSent(index: Int, total: Int, frame: ByteArray) {
            log.onFrameSent(index, total, frame)
            _sendState.value = SendState.Sending(index, total)
        }
    }

    override fun onCleared() {
        stopChecks()
        network.stop()
    }

    companion object {
        const val DATA_DAMAGED = "The program data of the app is damaged."
        private const val LIST = "list"
    }
}
