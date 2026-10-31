package pl.trailpocket

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import pl.trailpocket.data.Point

suspend fun currentLocation(context: Context): Point = withTimeout(25_000L) {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    check(fine || coarse) { "Allow the app to access your location." }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = buildList {
        if (fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }
    check(providers.isNotEmpty()) { "Turn on location in your phone settings." }
    suspendCancellableCoroutine { continuation ->
        val signals = providers.map { CancellationSignal() }
        continuation.invokeOnCancellation { signals.forEach { it.cancel() } }
        var remaining = providers.size
        fun receive(location: Location?) {
            if (!continuation.isActive) return
            if (location != null) {
                continuation.resume(Point(location.latitude, location.longitude, "My location"))
                signals.forEach { it.cancel() }
            } else if (--remaining == 0) {
                continuation.resumeWithException(IllegalStateException("Could not get your location. Try again outdoors."))
            }
        }
        providers.forEachIndexed { index, provider ->
            if (continuation.isActive) {
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, signals[index],
                        ContextCompat.getMainExecutor(context), ::receive)
                } catch (_: SecurityException) { receive(null) }
            }
        }
    }
}
