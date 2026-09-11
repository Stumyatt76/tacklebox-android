/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The pure rules behind the capture and edit screens, kept out of the composables so they can be tested. */
object CatchTiming {
    /**
     * Material's date picker reports the chosen day as midnight **UTC**. Stored as-is, a New York angler
     * back-dating to the 11th got "10 Sep · 20:00", and everyone lost the time of day. The picked calendar date is
     * read in UTC and combined with the time of day the angler already had, in the device zone.
     */
    fun combine(pickedUtcMidnightMillis: Long, previousMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = Instant.ofEpochMilli(pickedUtcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(previousMillis).atZone(zone).toLocalTime()
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** The time picker's hour and minute on the day the angler already had, in the device zone. */
    fun withTime(previousMillis: Long, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(previousMillis).atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** iOS bounds the picker to `...Date.now`; Material's pickers cannot, so the result is clamped instead. */
    fun clampToNow(millis: Long, now: Long = System.currentTimeMillis()): Long = minOf(millis, now)

    /** The UTC-midnight value Material's date picker wants as its initial selection for a local instant. */
    fun utcMidnightOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

object EditedMeasurement {
    /**
     * A stepper can only show whole units, so loading 2126.25 g into kg/g steppers reads 2 kg 120 g — and saving
     * an untouched form used to write that rounded value back. The stored value survives unless the angler moved
     * the stepper; a moved stepper that reads zero clears the measurement.
     */
    fun resolve(original: Double?, changed: Boolean, entered: Double): Double? =
        if (!changed) original else entered.takeIf { it > 0 }
}

/** A localised medium date ("11 Sept 2026" / "Sep 11, 2026"), the date-only counterpart of `LocalTime.hm()`. */
fun LocalDate.pretty(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
