/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app.services

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.*

/**
 * How good a day is for fishing, on the same four-point scale as iOS (TB-P-05).
 *
 * The old rating was an Int rendered as "n/5" on both the Vault card and the widget. It could only ever produce
 * 3, 4 or 5 — `2 + moonScore(1..2) + daylightScore(0..1)` — so the app was incapable of telling an angler a day
 * looked poor while claiming a five-point scale. Over 2026 it returned 3 on 175 days, 4 on 158 and 5 on 32, and
 * never 1 or 2.
 */
enum class SolunarRating {
    POOR, FAIR, GOOD, EXCELLENT;
    val title: String get() = name.lowercase().replaceFirstChar(Char::uppercase)
}

data class BiteWindow(val label: String, val start: LocalTime, val end: LocalTime, val major: Boolean)

data class SolunarDay(
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val moonrise: LocalTime?,
    val moonset: LocalTime?,
    val rating: SolunarRating,
    val moonPhase: String,
    val windows: List<BiteWindow>,
)

/**
 * A local solar/lunar ephemeris, ported from the iOS `SolunarService` so both apps describe the same day the same
 * way (TB-P-05).
 *
 * The previous implementation did not just present the day differently — it computed a different one. Moon age came
 * from `(epochDay + 4) % 29.53059`, an epoch roughly 19 days out: on 2026-09-08 it reported a *waxing* crescent for
 * what was in fact a waning crescent, close to the opposite point of the cycle. Moonrise was then invented from that
 * age (`age / 29.53 * 24 + 6`) rather than computed, and the two "major" windows were placed at that invented time
 * and twelve hours after it. Since the majors are the feature — the moon overhead and underfoot are the whole basis
 * of solunar theory — the bite windows were not approximations of the right answer, they were unrelated to it.
 *
 * This computes the moon's actual position (a compact Meeus series), samples its altitude across the day, and takes
 * the real rise, set and transits. Minor windows are moonrise and moonset, not dawn and dusk, which is what solunar
 * theory says and what iOS already did.
 */
object Astronomy {
    /** Central England — used when no position is available. Matches `DeviceLocation.FALLBACK_INLAND`. */
    private const val DEFAULT_LAT = 52.36
    private const val DEFAULT_LON = -1.17

    private const val SYNODIC_MONTH = 29.530588853
    /** 2000-01-06 18:14 UTC, a known new moon. The same reference iOS uses. */
    private const val KNOWN_NEW_MOON_EPOCH_SECONDS = 947_182_440.0

    fun calculate(
        date: LocalDate = LocalDate.now(),
        latitude: Double = DEFAULT_LAT,
        longitude: Double = DEFAULT_LON,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SolunarDay {
        val start = date.atStartOfDay(zone).toInstant()
        val end = start.plusSeconds(86_400)
        val solar = solarEvents(date, start, latitude, longitude, zone)
        val lunar = lunarEvents(start, end, latitude, longitude)
        val phase = moonPhase(solar.noon)

        // Majors are the moon overhead and underfoot, two hours wide; minors are moonrise and moonset, one hour.
        val majors = listOfNotNull(
            lunar.upperTransit?.let { "Moon overhead" to it },
            lunar.lowerTransit?.let { "Moon underfoot" to it },
        ).map { (label, at) -> window(label, at, 60, true, zone) }
        val minors = listOfNotNull(
            lunar.rise?.let { "Moonrise" to it },
            lunar.set?.let { "Moonset" to it },
        ).map { (label, at) -> window(label, at, 30, false, zone) }

        return SolunarDay(
            sunrise = solar.rise.local(zone),
            sunset = solar.set.local(zone),
            moonrise = lunar.rise?.local(zone),
            moonset = lunar.set?.local(zone),
            rating = phase.second,
            moonPhase = phase.first,
            windows = (majors + minors).sortedBy { it.start },
        )
    }

    private fun window(label: String, at: Instant, minutes: Long, major: Boolean, zone: ZoneId) =
        BiteWindow(label, at.minusSeconds(minutes * 60).local(zone), at.plusSeconds(minutes * 60).local(zone), major)

    private fun Instant.local(zone: ZoneId): LocalTime = LocalDateTime.ofInstant(this, zone).toLocalTime()

    private class Solar(val rise: Instant, val set: Instant, val noon: Instant)

    /**
     * NOAA's equation of time and solar declination. Above the Arctic and Antarctic circles the sun may not cross
     * the horizon at all; rather than return nothing, the day collapses to solar noon, so the screens still render.
     */
    private fun solarEvents(date: LocalDate, start: Instant, latitude: Double, longitude: Double, zone: ZoneId): Solar {
        val n = date.dayOfYear.toDouble()
        val offsetHours = zone.rules.getOffset(start).totalSeconds / 3_600.0
        val gamma = 2 * PI / 365 * (n - 1)
        val equation = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val noonMinutes = 720 - 4 * longitude - equation + 60 * offsetHours
        val noon = start.plusSeconds((noonMinutes * 60).toLong())
        val lat = Math.toRadians(latitude)
        val cosHour = cos(Math.toRadians(90.833)) / (cos(lat) * cos(declination)) - tan(lat) * tan(declination)
        if (cosHour < -1 || cosHour > 1) return Solar(noon, noon, noon)
        val halfDayMinutes = Math.toDegrees(acos(cosHour)) * 4
        return Solar(
            start.plusSeconds(((noonMinutes - halfDayMinutes) * 60).toLong()),
            start.plusSeconds(((noonMinutes + halfDayMinutes) * 60).toLong()),
            noon,
        )
    }

    private class Lunar(val rise: Instant?, val set: Instant?, val upperTransit: Instant?, val lowerTransit: Instant?)

    /** Samples the moon's altitude every ten minutes and interpolates the horizon crossings. */
    private fun lunarEvents(start: Instant, end: Instant, latitude: Double, longitude: Double): Lunar {
        val lat = Math.toRadians(latitude)
        val samples = ArrayList<Pair<Instant, Double>>(150)
        var time = start
        while (!time.isAfter(end)) {
            val position = moonPosition(time)
            val hourAngle = normalizedSigned(localSiderealTime(time, longitude) - position.first)
            val altitude = asin(sin(lat) * sin(position.second) + cos(lat) * cos(position.second) * cos(hourAngle))
            samples.add(time to altitude)
            time = time.plusSeconds(600)
        }
        // Slightly below true horizon, allowing for refraction and the moon's semi-diameter.
        val horizon = Math.toRadians(-0.3)
        var rise: Instant? = null
        var set: Instant? = null
        for (i in 0 until samples.size - 1) {
            val (t0, a0) = samples[i]
            val (t1, a1) = samples[i + 1]
            if (a0 < horizon && a1 >= horizon && rise == null) rise = interpolate(t0, a0, t1, a1, horizon)
            if (a0 >= horizon && a1 < horizon && set == null) set = interpolate(t0, a0, t1, a1, horizon)
        }
        return Lunar(rise, set, samples.maxByOrNull { it.second }?.first, samples.minByOrNull { it.second }?.first)
    }

    private fun interpolate(t0: Instant, a0: Double, t1: Instant, a1: Double, target: Double): Instant {
        val fraction = (target - a0) / (a1 - a0)
        val span = t1.epochSecond - t0.epochSecond
        return t0.plusSeconds((span * fraction).toLong())
    }

    /** Compact Meeus-style lunar orbit: principal perturbations, then ecliptic-to-equatorial conversion. */
    private fun moonPosition(at: Instant): Pair<Double, Double> {
        val d = at.toEpochMilli() / 86_400_000.0 - 10_957.5 // days from J2000.0
        val l0 = Math.toRadians(normalize(218.316 + 13.176396 * d))
        val meanAnomaly = Math.toRadians(normalize(134.963 + 13.064993 * d))
        val argumentLatitude = Math.toRadians(normalize(93.272 + 13.229350 * d))
        val sunAnomaly = Math.toRadians(normalize(357.529 + 0.98560028 * d))
        val elongation = Math.toRadians(normalize(297.850 + 12.190749 * d))
        val longitude = l0 + Math.toRadians(
            6.289 * sin(meanAnomaly) + 1.274 * sin(2 * elongation - meanAnomaly) +
                0.658 * sin(2 * elongation) + 0.214 * sin(2 * meanAnomaly) - 0.186 * sin(sunAnomaly))
        val latitude = Math.toRadians(
            5.128 * sin(argumentLatitude) + 0.280 * sin(meanAnomaly + argumentLatitude) +
                0.277 * sin(meanAnomaly - argumentLatitude) + 0.173 * sin(2 * elongation - argumentLatitude))
        val obliquity = Math.toRadians(23.4393 - 0.0000004 * d)
        val x = cos(longitude) * cos(latitude)
        val y = sin(longitude) * cos(latitude) * cos(obliquity) - sin(latitude) * sin(obliquity)
        val z = sin(longitude) * cos(latitude) * sin(obliquity) + sin(latitude) * cos(obliquity)
        return atan2(y, x) to asin(z)
    }

    private fun localSiderealTime(at: Instant, longitude: Double): Double {
        val jd = at.toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545) / 36_525
        return Math.toRadians(normalize(280.46061837 + 360.98564736629 * (jd - 2_451_545) +
            0.000387933 * t * t - t * t * t / 38_710_000 + longitude))
    }

    /**
     * The named phase, and how good the day looks.
     *
     * Solunar theory rates the new and full moon highest and the quarters lowest, so the score is how far the
     * illuminated fraction sits from half — not a step function on moon age, and with no daylight-length term,
     * which is not a solunar concept and was the reason the old scale could never reach its own floor.
     */
    private fun moonPhase(at: Instant): Pair<String, SolunarRating> {
        val age = positiveRemainder(
            (at.toEpochMilli() / 1000.0 - KNOWN_NEW_MOON_EPOCH_SECONDS) / 86_400.0, SYNODIC_MONTH)
        val index = floor(age / SYNODIC_MONTH * 8 + 0.5).toInt() % 8
        val names = listOf("New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous",
            "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent")
        val illumination = (1 - cos(2 * PI * age / SYNODIC_MONTH)) / 2
        val strength = abs(illumination - 0.5) * 2
        val rating = when {
            strength >= 0.85 -> SolunarRating.EXCELLENT
            strength >= 0.60 -> SolunarRating.GOOD
            strength >= 0.30 -> SolunarRating.FAIR
            else -> SolunarRating.POOR
        }
        return names[index] to rating
    }

    private fun normalize(degrees: Double) = positiveRemainder(degrees, 360.0)
    private fun positiveRemainder(value: Double, modulus: Double) = ((value % modulus) + modulus) % modulus
    private fun normalizedSigned(radians: Double): Double {
        var value = radians
        while (value > PI) value -= 2 * PI
        while (value < -PI) value += 2 * PI
        return value
    }
}
