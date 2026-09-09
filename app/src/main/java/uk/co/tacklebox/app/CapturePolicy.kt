/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app
import java.time.Instant
import java.time.Duration
object CapturePolicy {
    fun canStampCurrentWeather(caughtAt: Instant, now: Instant = Instant.now()): Boolean {
        val age = Duration.between(caughtAt, now)
        return !age.isNegative && age <= Duration.ofMinutes(15)
    }
}
