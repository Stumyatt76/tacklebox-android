/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.dp

/**
 * A carp in side profile, facing left. Ported point for point from the iOS `FishGlyph` (TB-P-05).
 *
 * Single closed outline — dorsal fin, forked tail, pectoral fin — plus the eye and mirror scales as separate
 * subpaths. The even-odd fill rule is what turns those subpaths into cut-outs rather than blobs on top; with the
 * default non-zero rule the fish would have a solid eye.
 */
fun fishGlyphPath(size: Size): Path {
    val w = size.width; val h = size.height
    fun x(f: Float) = w * f
    fun y(f: Float) = h * f
    return Path().apply {
        fillType = PathFillType.EvenOdd
        // Body outline — from the mouth, over the head, along the back through the dorsal fin, out to the forked
        // tail, then back along the belly.
        moveTo(x(0.03f), y(0.55f))
        quadraticTo(x(0.15f), y(0.10f), x(0.42f), y(0.15f))   // head + back
        quadraticTo(x(0.50f), y(0.01f), x(0.58f), y(0.05f))   // dorsal fin leading edge
        quadraticTo(x(0.66f), y(0.20f), x(0.72f), y(0.27f))   // dorsal trailing edge
        lineTo(x(0.82f), y(0.31f))                            // wrist to tail
        lineTo(x(0.99f), y(0.09f))                            // upper tail tip
        quadraticTo(x(0.90f), y(0.33f), x(0.87f), y(0.50f))   // upper tail into fork notch
        quadraticTo(x(0.90f), y(0.67f), x(0.99f), y(0.91f))   // fork notch to lower tail tip
        lineTo(x(0.82f), y(0.69f))                            // lower wrist
        quadraticTo(x(0.70f), y(0.86f), x(0.50f), y(0.88f))   // belly
        quadraticTo(x(0.41f), y(0.99f), x(0.30f), y(0.93f))   // pectoral fin bump
        quadraticTo(x(0.11f), y(0.74f), x(0.03f), y(0.55f))   // throat back to mouth
        close()
        // Eye, then the mirror scales along the flank. Both sized off the width so they stay circular.
        addOval(androidx.compose.ui.geometry.Rect(x(0.11f), y(0.39f), x(0.11f) + w * 0.055f, y(0.39f) + w * 0.055f))
        listOf(0.45f to 0.40f, 0.57f to 0.32f, 0.55f to 0.55f).forEach { (sx, sy) ->
            addOval(androidx.compose.ui.geometry.Rect(x(sx), y(sy), x(sx) + w * 0.07f, y(sy) + w * 0.07f))
        }
    }
}

@Composable fun FishGlyph(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPath(fishGlyphPath(size), colour) }
}

/** The same carp, stroked rather than filled — the empty photo well on the capture screen. */
@Composable fun FishGlyphOutline(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPath(fishGlyphPath(size), colour, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())) }
}
