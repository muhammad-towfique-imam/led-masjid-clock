package com.masjidclock.wfsender.usb

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi

/** File access for the Create flow, which needs Android 11 or later (spec section 5.5). */
object StorageAccess {

    @Suppress("UNUSED_PARAMETER")
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** Opens the "All files access" settings page of this app. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun openSettings(context: Context) {
        val forApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(forApp)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e: ActivityNotFoundException) {
                // Some phones have no "All files access" screen: open the app settings instead.
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }
    }
}
