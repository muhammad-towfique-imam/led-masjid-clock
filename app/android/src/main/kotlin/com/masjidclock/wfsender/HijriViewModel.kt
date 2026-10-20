package com.masjidclock.wfsender

import android.app.Application
import android.content.Context
import android.net.Network
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.hijri.HijriDataException
import com.masjidclock.wfsender.hijri.HijriInput
import com.masjidclock.wfsender.hijri.HijriM5
import com.masjidclock.wfsender.hijri.HijriMonths
import com.masjidclock.wfsender.hijri.HijriProgram
import com.masjidclock.wfsender.hijri.HijriSettings
import com.masjidclock.wfsender.hijri.HijriStart
import com.masjidclock.wfsender.hijri.HijriValues
import com.masjidclock.wfsender.hijri.hijriLogHeader
import com.masjidclock.wfsender.net.CardNetwork
import com.masjidclock.wfsender.net.ConnectionCheck
import com.masjidclock.wfsender.net.ConnectionStatus
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
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Hijri tab (spec 2026-10-20-masjid-clock-hijri-design.md): makes the m5 Hijri program on the phone and sends it.
 * The connection, time, and send flow is the same as in [AppViewModel].
 */
class HijriViewModel(app: Application) : AndroidViewModel(app) {

    private val network = CardNetwork(app).also { it.start() }
    private var checkJob: Job? = null
    private var check: ConnectionCheck<Network>? = null
    private val gate = SendGate.APP
    private val prefs = app.getSharedPreferences("hijri", Context.MODE_PRIVATE)
    private val settings = HijriSettings(
        read = { prefs.getString(it, null) },
        write = { key, value -> prefs.edit().putString(key, value).apply() },
    )

    private val _values = MutableStateFlow(settings.load(LocalDate.now()))
    val values: StateFlow<HijriValues> = _values

    private val _connection = MutableStateFlow(ConnectionStatus.NOT_CONNECTED)
    val connection: StateFlow<ConnectionStatus> = _connection

    private val _cardName = MutableStateFlow<String?>(null)
    val cardName: StateFlow<String?> = _cardName

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    private val _timeStep = MutableStateFlow<TimeStep>(TimeStep.Pending)
    val timeStep: StateFlow<TimeStep> = _timeStep

    fun setDate(date: LocalDate) {
        if (gate.busy) return
        _values.value = _values.value.copy(monthStart = date)
        settings.saveDate(date)
    }

    fun setMonth(month: Int) {
        if (gate.busy) return
        _values.value = _values.value.copy(month = month)
        settings.saveMonth(month)
    }

    fun setYearText(text: String) {
        if (gate.busy) return
        _values.value = _values.value.copy(yearText = text)
        HijriInput.parseYear(text)?.let(settings::saveYear)
    }

    /**
     * While the tab is visible. Not during or after a send: after a send, each probe makes the display flicker.
     * [sendAgain] starts the checks again.
     */
    fun startChecks() {
        if (_sendState.value != SendState.Idle) return
        stopChecks()
        _connection.value = ConnectionStatus.NOT_CONNECTED
        val newCheck = ConnectionCheck({ network.current }) { wifi ->
            val found = CardSender(network.connectorFor(wifi)).probe()
            _cardName.value = if (found) CardClock(network.udpFor(wifi), timeoutMs = 1_500, tries = 1).discover()?.name else null
            found
        }
        check = newCheck
        checkJob = viewModelScope.launch {
            while (isActive) {
                val status = withContext(Dispatchers.IO) { newCheck.next() } ?: break
                _connection.value = status
                if (status != ConnectionStatus.DISPLAY_FOUND) _cardName.value = null
                if (status != ConnectionStatus.DISPLAY_FOUND && _timeStep.value != TimeStep.Busy) {
                    _timeStep.value = TimeStep.Pending
                }
                delay(2_000)
            }
        }
    }

    fun stopChecks() {
        check?.stop()
        check = null
        checkJob?.cancel()
        checkJob = null
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
        val input = _values.value
        val month = input.month ?: return
        val year = HijriInput.parseYear(input.yearText) ?: return
        val wifi = network.current ?: return
        if (_connection.value != ConnectionStatus.DISPLAY_FOUND || !_timeStep.value.done) return
        if (!gate.tryStart()) return
        stopChecks()
        _sendState.value = SendState.Sending(0, 0)
        val counterStart = HijriStart.counterStart(input.monthStart, LocalDate.now())
        viewModelScope.launch {
            try {
                val export = try {
                    withContext(Dispatchers.Default) {
                        HdsExport(
                            HijriM5.screen,
                            HijriProgram.make(HijriM5.base(month), HijriM5.digits, year, counterStart),
                        )
                    }
                } catch (e: HijriDataException) {
                    _sendState.value = SendState.Finished(false, DATA_DAMAGED, "Masjid Clock: ${e.message}")
                    return@launch
                }
                val log = SendLog(
                    header = hijriLogHeader(
                        HijriMonths.NAMES[month - 1], year, input.monthStart, counterStart, export.program.size,
                    ),
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
                    finishSend(safe, log)
                } else {
                    finishSend(result, log)
                }
            } finally {
                gate.finish()
            }
        }
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

    override fun onCleared() {
        stopChecks()
        network.stop()
    }

    companion object {
        const val DATA_DAMAGED = "The program data of the app is damaged."
    }
}
