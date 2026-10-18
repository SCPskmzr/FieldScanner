package com.fieldscanner.app.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal data class FindingLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float
)

/** Obtains one best-effort location. All failures intentionally result in null. */
internal class DeviceLocationProvider(private val context: Context) {
    fun getCurrentLocation(timeoutMillis: Long = LOCATION_TIMEOUT_MILLIS): FindingLocation? {
        val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = buildList {
            if (hasFine && isEnabled(manager, LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (isEnabled(manager, LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) return null
        newestRecentLocation(manager, providers)?.let { return it }
        val thread = HandlerThread("finding-location").apply { start() }
        val latch = CountDownLatch(1)
        var location: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(value: Location) {
                location = value
                latch.countDown()
            }
        }

        return try {
            providers.forEach { provider -> manager.requestSingleUpdate(provider, listener, thread.looper) }
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            location?.let { FindingLocation(it.latitude, it.longitude, it.accuracy) }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } finally {
            try {
                manager.removeUpdates(listener)
            } catch (_: SecurityException) {
                // Permission may have been revoked while the request was active.
            }
            thread.quitSafely()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun newestRecentLocation(
        manager: LocationManager,
        providers: List<String>
    ): FindingLocation? {
        val now = System.currentTimeMillis()
        val location = providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }.filter { now - it.time <= MAX_LAST_LOCATION_AGE_MILLIS }
            .maxByOrNull { it.time }
            ?: return null
        return FindingLocation(location.latitude, location.longitude, location.accuracy)
    }

    private fun isEnabled(manager: LocationManager, provider: String): Boolean = try {
        manager.isProviderEnabled(provider)
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 8_000L
        const val MAX_LAST_LOCATION_AGE_MILLIS = 5 * 60 * 1_000L
    }
}
