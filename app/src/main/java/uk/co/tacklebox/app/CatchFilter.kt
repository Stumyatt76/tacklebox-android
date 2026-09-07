/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.UnitSystem
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How the catch list is narrowed down.
 *
 * Neither app had search or filter of any kind, so an angler with two seasons logged could not ask "what did I
 * catch at Willow Mere in May?", "every fish over 10 lb", or "when did I last use a Ronnie rig?" — even though all
 * of it is stored and indexed. This is the pure, testable core; the Catches screen only drives it.
 *
 * Kept deliberately in step with the iOS `CatchFilter`, clause for clause, so the two apps answer the same
 * question the same way.
 */
data class CatchFilter(
    val text: String = "",
    val speciesName: String? = null,
    val waterName: String? = null,
    val period: Period = Period.ALL,
    val personalBestsOnly: Boolean = false,
    /** Canonical grams, so a threshold means the same thing whichever units are on screen. */
    val minimumGrams: Double? = null
) {
    enum class Period(val title: String) {
        ALL("Any time"), THIS_YEAR("This year"), LAST_30_DAYS("Last 30 days"), LAST_12_MONTHS("Last 12 months")
    }

    private val trimmed get() = text.trim()

    /** True when something is narrowing the list — the two empty states say very different things. */
    val isActive: Boolean get() = this != CatchFilter()

    /** The active clauses as short labels, so the UI can show what is applied and offer to clear it. */
    fun activeSummary(unit: UnitSystem = UnitSystem.METRIC): List<String> = buildList {
        if (trimmed.isNotEmpty()) add("“$trimmed”")
        speciesName?.let(::add)
        waterName?.let(::add)
        if (period != Period.ALL) add(period.title)
        if (personalBestsOnly) add("Personal bests")
        minimumGrams?.let { add("Over ${it.weight(unit)}") }
    }

    /** Applies every active clause. Newest first, because a journal is read backwards. */
    fun apply(rows: List<CatchRow>, now: Instant = Instant.now()): List<CatchRow> {
        val bests = if (personalBestsOnly) personalBests(rows) else emptyMap()
        val earliest = earliest(now)
        val needle = trimmed.lowercase()

        return rows.filter { row ->
            if (earliest != null && row.item.caughtAt.isBefore(earliest)) return@filter false
            if (speciesName != null && row.species?.name != speciesName) return@filter false
            if (waterName != null && row.water?.name != waterName) return@filter false
            if (minimumGrams != null && (row.item.weightGrams ?: 0.0) < minimumGrams) return@filter false
            if (personalBestsOnly) {
                val name = row.species?.name ?: return@filter false
                if (bests[name]?.item?.id != row.item.id) return@filter false
            }
            if (needle.isNotEmpty() && !matches(row, needle)) return@filter false
            true
        }.sortedByDescending { it.item.caughtAt }
    }

    /** Free text spans everything an angler would think to type: the fish, the place, and what caught it. */
    private fun matches(row: CatchRow, needle: String) = listOfNotNull(
        row.species?.name, row.species?.scientificName, row.water?.name, row.water?.region,
        row.item.rig, row.item.bait
    ).any { it.lowercase().contains(needle) }

    private fun earliest(now: Instant): Instant? = when (period) {
        Period.ALL -> null
        Period.THIS_YEAR -> now.atZone(ZoneId.systemDefault()).toLocalDate()
            .withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        Period.LAST_30_DAYS -> now.minus(30, ChronoUnit.DAYS)
        Period.LAST_12_MONTHS -> now.atZone(ZoneId.systemDefault()).minusMonths(12).toInstant()
    }

    companion object {
        /** The heaviest weighed fish of each species. Unweighed catches cannot hold a record. */
        fun personalBests(rows: List<CatchRow>): Map<String, CatchRow> =
            rows.filter { it.item.weightGrams != null && it.species != null }
                .groupBy { it.species!!.name }
                .mapValues { (_, group) -> group.maxBy { it.item.weightGrams!! } }
    }
}
