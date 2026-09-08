/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import uk.co.tacklebox.app.MainActivity
import uk.co.tacklebox.app.services.Astronomy
import uk.co.tacklebox.app.services.DeviceLocation
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The bite-window widget.
 *
 * This is the one thing an angler wants to know before leaving the house, and the app already computed it — it just
 * lived behind two taps. The windows come from the same local ephemeris the Bite windows screen uses, so the widget
 * needs no journal data, no network and nothing to sync.
 *
 * Kept in step with the iOS widget: the next window, how long until it starts, the day's rating, and an honest note
 * when the position is a fallback rather than the angler's own.
 */
class BiteWindowWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Location is read here rather than in the composable: a widget's content must be ready before it draws.
        val place = DeviceLocation.current(context)
        val (latitude, longitude) = place ?: DeviceLocation.FALLBACK_INLAND
        val day = Astronomy.calculate(latitude = latitude, longitude = longitude)
        val now = LocalTime.now()
        val next = day.windows.firstOrNull { !it.end.isBefore(now) }

        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier.fillMaxSize().background(Background).padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    // fillMaxWidth, or the spacer has no room to work in and the rating butts against the label.
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("NEXT WINDOW", style = TextStyle(color = androidx.glance.unit.ColorProvider(Brass),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.defaultWeight())
                        // The word, as the iOS widget shows it — not "n/5", a scale whose lower half was
                        // unreachable (TB-P-05).
                        Text(day.rating.title.uppercase(), style = TextStyle(color = androidx.glance.unit.ColorProvider(Teal),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if (next != null) {
                        Text(if (next.major) "Major" else "Minor",
                            style = TextStyle(color = androidx.glance.unit.ColorProvider(if (next.major) Brass else Teal),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        Text(next.start.format(clock),
                            style = TextStyle(color = androidx.glance.unit.ColorProvider(Ink),
                                fontSize = 28.sp, fontWeight = FontWeight.Bold))
                        Text(countdown(now, next.start),
                            style = TextStyle(color = androidx.glance.unit.ColorProvider(Muted), fontSize = 12.sp))
                    } else {
                        Text("No more windows today",
                            style = TextStyle(color = androidx.glance.unit.ColorProvider(Ink), fontSize = 14.sp,
                                fontWeight = FontWeight.Bold))
                        Text("Tomorrow's return at dawn",
                            style = TextStyle(color = androidx.glance.unit.ColorProvider(Muted), fontSize = 12.sp))
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    // Says plainly when the times are a guess, rather than passing off central-UK times as local.
                    Text(
                        if (place != null) "↑ ${day.sunrise.format(clock)}   ↓ ${day.sunset.format(clock)}"
                        else "Showing central UK · open Tacklebox",
                        style = TextStyle(color = androidx.glance.unit.ColorProvider(Dim), fontSize = 10.sp)
                    )
                }
            }
        }
    }

    companion object {
        private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        private val Background = Color(0xFF0E1A1E)
        private val Ink = Color(0xFFF3EEE4)
        private val Muted = Color(0xFF9BB0B3)
        private val Dim = Color(0xFF6F8589)
        private val Brass = Color(0xFFC9A24B)
        private val Teal = Color(0xFF57B3A6)

        /** "in 1h 20m" while it is ahead, "Happening now" once it has started. */
        fun countdown(now: LocalTime, start: LocalTime): String {
            val minutes = java.time.Duration.between(now, start).toMinutes()
            if (minutes <= 0) return "Happening now"
            val hours = minutes / 60
            return if (hours == 0L) "in ${minutes}m" else "in ${hours}h ${minutes % 60}m"
        }
    }
}

class BiteWindowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BiteWindowWidget()
}
