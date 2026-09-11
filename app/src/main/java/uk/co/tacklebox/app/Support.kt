/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt

/**
 * Weight conversion between the stored canonical grams and the imperial pounds-and-ounces the UI shows.
 *
 * The Log screen used to be labelled "Weight (oz)" while the rest of the app rendered "X lb Y oz", so typing 64
 * produced "4 lb 0 oz" and nobody could tell whether it had worked (TB-A-13). Input and display now use the same
 * units, and grams remain the single stored representation.
 */
object Weights {
    const val GRAMS_PER_OUNCE = 28.349523125
    const val OUNCES_PER_POUND = 16

    /** Null when both fields are blank, so an unweighed catch stays unweighed rather than becoming 0 g. */
    fun fromPoundsAndOunces(pounds:String, ounces:String):Double? {
        if (pounds.isBlank() && ounces.isBlank()) return null
        val lb = pounds.toDoubleOrNull() ?: 0.0
        val oz = ounces.toDoubleOrNull() ?: 0.0
        return (lb * OUNCES_PER_POUND + oz) * GRAMS_PER_OUNCE
    }

    /** Splits grams into whole pounds and ounces, carrying 16 oz up to a pound so "3 lb 16 oz" can never appear. */
    fun toPoundsAndOunces(grams:Double):Pair<Int,Int> {
        val totalOunces = (grams / GRAMS_PER_OUNCE).roundToInt()
        return (totalOunces / OUNCES_PER_POUND) to (totalOunces % OUNCES_PER_POUND)
    }
}

/**
 * Camera capture. The manifest declared CAMERA but nothing ever used it — only the document picker was wired, so the
 * app asked for a permission it could not exercise and lacked the capture path iOS offers (TB-A-18).
 */
object CapturePhoto {
    fun permitted(context:Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun destination(context:Context):Uri {
        val dir = File(context.cacheDir,"photos").apply { mkdirs() }
        val file = File(dir,"catch-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
    }

    /** Removes the camera's cache copy once the capture has been imported into the photo store. */
    fun discard(context:Context, capture:Uri) {
        val name = capture.lastPathSegment ?: return
        File(File(context.cacheDir,"photos"), name).delete()
    }
}
