/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Approximate device position for the conditions, solunar, river and tide screens.
 *
 * Before this existed the app requested ACCESS_COARSE_LOCATION during onboarding, discarded the result, and then
 * used hardcoded coordinates everywhere — 52.5/-1.5 for rivers and bite windows, 50.7/-1.9 for the sea. Granting
 * the permission changed nothing an angler could see (TB-A-10).
 *
 * Coordinates are rounded to two decimal places (about a kilometre) before they leave the device, matching the iOS
 * behaviour and the promise in the privacy policy that a precise swim is never stored or transmitted.
 */
object DeviceLocation {
    /**
     * Central England / the Solent — used when permission is refused or no fix is available.
     *
     * The inland point matches iOS's `SolunarPlace.fallback` exactly. It used to be 52.5/-1.5 against iOS's
     * 52.36/-1.17, which put the two apps a minute apart on sunrise and sunset for the same unlocated user.
     */
    val FALLBACK_INLAND = 52.36 to -1.17
    val FALLBACK_COASTAL = 50.7 to -1.9

    fun hasPermission(context:Context):Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Rounded to ~1 km. Returns null when permission is missing or no fix can be obtained. */
    suspend fun current(context:Context):Pair<Double,Double>? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location:Location? = try {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    val token = CancellationTokenSource()
                    cont.invokeOnCancellation { token.cancel() }
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
        } catch (e:CancellationException) { throw e } catch (_:SecurityException) { null } catch (_:Exception) { null }
        return location?.let { coarse(it.latitude) to coarse(it.longitude) }
    }

    private fun coarse(value:Double) = (value * 100).roundToInt() / 100.0
}
