package com.masjidclock.wfsender.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.masjidclock.wfsender.display.WifiSeen

/** Why the list "Displays nearby" can be empty. */
enum class ScanState { NO_PERMISSION, LOCATION_OFF, OK }

/**
 * The phone's WiFi scan results. Android 13 and later: "Nearby devices" (not for location); Android 7–12: location,
 * and the location setting must be on. A scan does not contact the cards.
 */
class WifiScanner(private val context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun state(): ScanState = when {
        ContextCompat.checkSelfPermission(context, permission()) != PackageManager.PERMISSION_GRANTED -> ScanState.NO_PERMISSION
        Build.VERSION.SDK_INT < 33 && !locationOn() -> ScanState.LOCATION_OFF
        else -> ScanState.OK
    }

    /** At most 4 scans in 2 minutes since Android 9; call it when the tab opens. */
    @Suppress("DEPRECATION")
    fun startScan() {
        if (state() == ScanState.OK) runCatching { wifi.startScan() }
    }

    @Suppress("DEPRECATION")
    fun results(): List<WifiSeen> {
        if (state() != ScanState.OK) return emptyList()
        return try {
            wifi.scanResults.orEmpty().map { WifiSeen(it.SSID.orEmpty().removeSurrounding("\""), it.level) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun locationOn(): Boolean {
        val location = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(location)
    }

    companion object {
        fun permission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
    }
}
