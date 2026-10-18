package com.masjidclock.wfsender

import android.app.Application
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.masjidclock.wfsender.net.CardNetwork
import com.masjidclock.wfsender.net.ConnectionCheck
import com.masjidclock.wfsender.net.ConnectionStatus
import com.masjidclock.wfsender.protocol.CardClock
import com.masjidclock.wfsender.protocol.ClockFailure
import com.masjidclock.wfsender.protocol.ClockResult
import com.masjidclock.wfsender.send.Messages
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

data class TimeResult(val message: String, val ok: Boolean)

/** The Time tab: finds the card on the WiFi and sets its clock to the phone's local time. */
class TimeViewModel(app: Application) : AndroidViewModel(app) {

    private val network = CardNetwork(app).also { it.start() }
    private var checkJob: Job? = null
    private var check: ConnectionCheck<Network>? = null

    private val _connection = MutableStateFlow(ConnectionStatus.NOT_CONNECTED)
    val connection: StateFlow<ConnectionStatus> = _connection

    private val _cardName = MutableStateFlow<String?>(null)
    val cardName: StateFlow<String?> = _cardName

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _result = MutableStateFlow<TimeResult?>(null)
    val result: StateFlow<TimeResult?> = _result

    /** Every 2 seconds while the tab is visible. The card is asked only until it answers. */
    fun startChecks() {
        stopChecks()
        val newCheck = ConnectionCheck({ network.current }) { wifi ->
            val identity = CardClock(network.udpFor(wifi)).discover()
            _cardName.value = identity?.name
            identity != null
        }
        check = newCheck
        checkJob = viewModelScope.launch {
            while (isActive) {
                val status = try {
                    withContext(Dispatchers.IO) { newCheck.next() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ConnectionStatus.NO_DISPLAY
                } ?: break
                _connection.value = status
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

    fun setTime() {
        val wifi = network.current ?: return
        if (_busy.value) return
        _busy.value = true
        _result.value = null
        viewModelScope.launch {
            val wall = WallClock.now()
            val result = try {
                withContext(Dispatchers.IO) { CardClock(network.udpFor(wifi)).setTime(wall) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ClockResult.Failed(ClockFailure.NETWORK, null)
            }
            _result.value = TimeResult(Messages.forClock(result, WallClock.time(wall)), ok = result is ClockResult.Success)
            _busy.value = false
        }
    }

    override fun onCleared() {
        stopChecks()
        network.stop()
    }
}
