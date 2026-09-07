/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import uk.co.tacklebox.app.data.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Reads a journal exported by either Tacklebox app back in.
 *
 * Export on its own is an escape hatch. Import is what turns it into a way to move between devices, recover from a
 * wiped phone, or come across from another app that can produce the same shape.
 *
 * Two rules shape the design, and both are asserted by tests:
 *
 * 1. **It never destroys anything.** Import merges; it does not replace. Nothing here deletes an existing record.
 * 2. **It never imports the same catch twice.** A catch already present — same species, same instant — is counted
 *    and skipped, so importing the same file repeatedly is harmless.
 *
 * Photos are not carried: an export references images rather than embedding them, so imported catches arrive
 * without them. The UI says so before you commit. Kept in step with the iOS `JournalImport`.
 */
object JournalImport {

    class Failure(message: String) : Exception(message)

    data class Plan(
        val catches: List<CatchRecord> = emptyList(),
        val waters: List<WaterRecord> = emptyList(),
        val sessions: List<SessionRecord> = emptyList(),
        val gear: List<GearRecord> = emptyList(),
        val presets: List<PresetRecord> = emptyList(),
        val duplicateCatches: Int = 0,
        val newWaters: Int = 0,
        val newSpecies: Int = 0
    ) {
        val newCatches: Int get() = catches.count { !it.isDuplicate }
        val isEmpty: Boolean get() = catches.isEmpty() && waters.isEmpty() && gear.isEmpty() && presets.isEmpty()
    }

    data class CatchRecord(
        val caughtAt: Instant, val species: String?, val scientificName: String?, val weightGrams: Double?,
        val lengthCm: Double?, val returned: Boolean, val water: String?, val rig: String?, val bait: String?,
        val notes: String, val sessionId: Int?, val conditions: ConditionsRecord?, var isDuplicate: Boolean = false
    )

    data class ConditionsRecord(
        val airTempC: Double?, val windDirection: String?, val windSpeedKph: Double?,
        val pressureHpa: Double?, val pressureTrend: String?, val moonPhase: String?
    )

    data class WaterRecord(val name: String, val type: WaterType, val region: String, val swimNotes: String)
    data class SessionRecord(val id: Int?, val startAt: Instant, val endAt: Instant?, val water: String?, val notes: String)
    data class GearRecord(val name: String, val category: GearCategory, val notes: String)
    data class PresetRecord(val name: String, val kind: PresetKind)

    data class Result(var catches: Int = 0, var waters: Int = 0, var sessions: Int = 0, var species: Int = 0,
                      var gear: Int = 0, var presets: Int = 0)

    /** Validates a file and works out what importing it would do, writing nothing. */
    fun plan(json: String, existing: ExistingVault): Plan {
        val root = try { JsonParser.parseString(json).asJsonObject }
                   catch (_: Exception) { throw Failure("That file could not be read as JSON.") }
        if (root.get("app")?.asString != "Tacklebox") throw Failure("That does not look like a Tacklebox journal.")
        val schema = root.get("schema")?.asInt ?: 1
        if (schema > JournalExport.SCHEMA) {
            throw Failure("That journal was written by a newer version of Tacklebox (format $schema). Update the app and try again.")
        }

        val waters = root.array("waters").mapNotNull { row ->
            val name = row.string("name")?.trim().orEmpty()
            if (name.isEmpty()) null
            else WaterRecord(name, waterType(row.string("type")), row.string("region").orEmpty(), row.string("swimNotes").orEmpty())
        }
        val sessions = root.array("sessions").mapNotNull { row ->
            val start = instant(row.string("startAt")) ?: return@mapNotNull null
            SessionRecord(row.int("id"), start, instant(row.string("endAt")), row.string("water"), row.string("notes").orEmpty())
        }
        val gear = root.array("gear").mapNotNull { row ->
            val name = row.string("name")?.trim().orEmpty()
            if (name.isEmpty()) null else GearRecord(name, gearCategory(row.string("category")), row.string("notes").orEmpty())
        }
        val presets = root.array("presets").mapNotNull { row ->
            val name = row.string("name")?.trim().orEmpty()
            if (name.isEmpty()) null else PresetRecord(name, presetKind(row.string("kind")))
        }
        val catches = root.array("catches").mapNotNull { row ->
            val caughtAt = instant(row.string("caughtAt")) ?: return@mapNotNull null
            val conditions = row.getAsJsonObject("conditions")?.let {
                ConditionsRecord(it.double("airTempC"), it.string("windDirection"), it.double("windSpeedKph"),
                                 it.double("pressureHpa"), it.string("pressureTrend"), it.string("moonPhase"))
            }
            CatchRecord(caughtAt, row.string("species")?.trim(), row.string("scientificName"),
                        row.double("weightGrams"), row.double("lengthCm"),
                        row.get("returned")?.takeIf { !it.isJsonNull }?.asBoolean ?: true,
                        row.string("water"), row.string("rig"), row.string("bait"),
                        row.string("notes").orEmpty(), row.int("sessionId"), conditions)
        }

        // Duplicates inside the file count too, so a doubled-up export cannot create doubled-up catches.
        var duplicates = 0
        val seen = mutableSetOf<String>()
        catches.forEach { record ->
            val key = fingerprint(record.species, record.caughtAt)
            if (existing.catchFingerprints.contains(key) || seen.contains(key)) { record.isDuplicate = true; duplicates++ }
            seen += key
        }

        val newWaters = waters.count { it.name.lowercase() !in existing.waterNames }
        val newSpecies = catches.mapNotNull { it.species?.lowercase() }.toSet().minus(existing.speciesNames).size

        return Plan(catches, waters, sessions, gear, presets, duplicates, newWaters, newSpecies)
    }

    /** What the vault already holds, so a plan can be worked out without the importer touching the database. */
    data class ExistingVault(
        val catchFingerprints: Set<String>,
        val waterNames: Set<String>,
        val speciesNames: Set<String>
    ) {
        companion object {
            fun from(state: AppState) = ExistingVault(
                catchFingerprints = state.catches.map { fingerprint(it.species?.name, it.item.caughtAt) }.toSet(),
                waterNames = state.waters.map { it.name.lowercase() }.toSet(),
                speciesNames = state.species.map { it.name.lowercase() }.toSet()
            )
        }
    }

    /** Same species, same instant. Two catches of one species at the same moment are the same fish. */
    fun fingerprint(species: String?, caughtAt: Instant) =
        "${species?.lowercase() ?: "?"}@${caughtAt.epochSecond}"

    // --- Lenient decoding ---------------------------------------------------------------------------------------
    // The two apps spell their enums differently — iOS `dayTicket`, Android `DAY_TICKET` — and each has members the
    // other does not. Matching loosely, with a sensible fallback, is what lets a journal cross platforms.

    fun waterType(raw: String?): WaterType {
        val key = raw?.replace("_", "")?.lowercase() ?: return WaterType.LAKE
        WaterType.entries.firstOrNull { it.name.lowercase() == key }?.let { return it }
        return when (key) {
            "shore", "boat" -> WaterType.SEA
            "pond", "syndicate", "dayticket", "commercial" -> WaterType.LAKE
            else -> WaterType.LAKE
        }
    }

    fun gearCategory(raw: String?): GearCategory {
        val key = raw?.replace("_", "")?.lowercase() ?: return GearCategory.OTHER
        GearCategory.entries.firstOrNull { it.name.lowercase() == key }?.let { return it }
        return when (key) { "terminal" -> GearCategory.OTHER; else -> GearCategory.OTHER }
    }

    fun presetKind(raw: String?) = if (raw?.lowercase() == "bait") PresetKind.BAIT else PresetKind.RIG

    private fun instant(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return try { Instant.parse(text) } catch (_: Exception) {
            try { DateTimeFormatter.ISO_DATE_TIME.parse(text, Instant::from) } catch (_: Exception) { null }
        }
    }

    private fun JsonObject.array(name: String) =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject } ?: emptyList()
    private fun JsonObject.string(name: String) = get(name)?.takeIf { !it.isJsonNull }?.asString
    private fun JsonObject.double(name: String) = get(name)?.takeIf { !it.isJsonNull }?.asDouble
    private fun JsonObject.int(name: String) = get(name)?.takeIf { !it.isJsonNull }?.asInt
}
