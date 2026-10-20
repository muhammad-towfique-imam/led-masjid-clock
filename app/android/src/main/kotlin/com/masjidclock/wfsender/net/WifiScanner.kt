package com.masjidclock.wfsender.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.masjidclock.wfsender.display.WifiSeen

/** Why the list "Displays nearby" can be empty. APPROXIMATE: only approximate location is allowed. */
enum class ScanState { NO_PERMISSION, APPROXIMATE, LOCATION_OFF, OK }

/**
 * The phone's WiFi scan results. They need precise location and the location setting on, on all Android versions;
 * without it, Android gives an empty list and no error. A scan does not contact the cards.
 */
class WifiScanner(private val context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun state(): ScanState = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> if (locationOn()) ScanState.OK else ScanState.LOCATION_OFF
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> ScanState.APPROXIMATE
        else -> ScanState.NO_PERMISSION
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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
        /** Android 12 and later: ask for both, so that the user can choose precise. */
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
