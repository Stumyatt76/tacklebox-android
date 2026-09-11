/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import uk.co.tacklebox.app.data.CatchRow
import java.time.ZoneId

/**
 * The Insights "conditions insight" was a fixed sentence — "Your catches favour stable pressure around dawn and
 * dusk." — printed for every user regardless of their data, presented as if it were analysis. This derives the
 * claim from the catches actually recorded, and says nothing when there is nothing to say.
 */
object Insight {
    private const val MINIMUM = 3

    fun conditions(catches:List<CatchRow>):String {
        if (catches.size < MINIMUM) return "Log three or more catches to reveal weather patterns."
        val withWeather = catches.filter { it.conditions?.airTempC != null || it.conditions?.pressureHpa != null }
        val parts = mutableListOf<String>()

        timeOfDay(catches)?.let { parts += it }
        if (withWeather.size >= MINIMUM) {
            // Both apps stamp Open-Meteo's surface_pressure, which depends on the water's altitude, so a fixed
            // "settled above 1020 hPa" threshold called every catch on an upland reservoir a low-pressure fish.
            // iOS reads the recorded trend instead — the direction of the glass is what anglers act on — so this
            // reports the most common trend and quotes the mean reading only as context.
            withWeather.mapNotNull { it.conditions?.pressureHpa }.takeIf { it.size >= MINIMUM }?.let { pressures ->
                val mean = pressures.average().toInt()
                val trend = withWeather.mapNotNull { it.conditions?.pressureTrend?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                parts += when (trend) {
                    "falling" -> "on a falling glass (around $mean hPa)"
                    "rising" -> "on a rising glass (around $mean hPa)"
                    "steady" -> "on steady pressure (around $mean hPa)"
                    else -> "around $mean hPa"
                }
            }
            withWeather.mapNotNull { it.conditions?.windDirection }.takeIf { it.isNotEmpty() }
                ?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }
                ?.takeIf { it.value > 1 }?.let { parts += "with a ${it.key} wind" }
        }

        if (parts.isEmpty()) return "Your catches don’t show a weather pattern yet — keep logging."
        return "Your catches come " + parts.joinToString(", ") + "."
    }

    /** The dominant part of the day, only reported when it is genuinely dominant rather than merely first. */
    private fun timeOfDay(catches:List<CatchRow>):String? {
        val buckets = catches.groupingBy { row ->
            when (row.item.caughtAt.atZone(ZoneId.systemDefault()).hour) {
                in 4..10 -> "at dawn"; in 11..15 -> "in the middle of the day"
                in 16..21 -> "at dusk"; else -> "after dark"
            }
        }.eachCount()
        val top = buckets.maxByOrNull { it.value } ?: return null
        return if (top.value * 2 > catches.size) top.key else null
    }
}
