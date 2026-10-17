package dev.quinton.goa.feature.sync.nearby

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The runtime permissions Nearby Connections needs on each Android version (the manifest declares
 * the full list, ADR-025). Android groups them as "Nearby devices" on 12+ and "Location" before.
 */
object NearbyPermissions {
    private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    private const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    private const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    private const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    private const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"

    /** Android 12 (S): Bluetooth permissions replace location for Nearby. */
    private const val S = 31

    /** Android 13 (T): Wi-Fi Direct needs "Nearby Wi-Fi devices". */
    private const val T = 33

    /** Android 17: Wi-Fi LAN needs "Local network" for apps targeting 37. */
    private const val LOCAL_NETWORK_SDK = 37

    fun required(sdk: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        when {
            sdk < Build.VERSION_CODES.Q -> add(COARSE_LOCATION)
            sdk <= S -> add(FINE_LOCATION)
        }
        if (sdk >= S) {
            add(BLUETOOTH_ADVERTISE)
            add(BLUETOOTH_CONNECT)
            add(BLUETOOTH_SCAN)
        }
        if (sdk >= T) add(NEARBY_WIFI_DEVICES)
        if (sdk >= LOCAL_NETWORK_SDK) add(ACCESS_LOCAL_NETWORK)
    }

    fun missing(context: Context): List<String> =
        required().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    /** What Android calls the permission the user is asked for, for the explanation. */
    fun groupName(sdk: Int = Build.VERSION.SDK_INT): String = if (sdk >= S) "Nearby devices" else "Location"

    /** The app's page in system settings, where a denied permission can be turned on. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
