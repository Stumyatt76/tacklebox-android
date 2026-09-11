/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import java.io.File
import java.time.Instant

/**
 * Writes the journal to a JSON file the user can share out of the app.
 *
 * Settings offered an "Export JSON" button whose onClick was empty — no file, no share sheet, no feedback (TB-A-05).
 * For a product sold on "your data stays on your device", an export is the user's escape hatch, so it should be
 * complete: every catch with its species, water, session, tackle and conditions, plus the waters, sessions, gear and
 * presets. Photos are referenced by URI rather than embedded — the images stay wherever the user already keeps them.
 */
object JournalExport {
    /** Bumped only when the shape changes in a way an older importer could misread. */
    const val SCHEMA = 1
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun write(context:Context, state:AppState):Uri {
        val dir = File(context.cacheDir,"exports").apply { mkdirs() }
        // One stable filename: repeated exports replace the previous file rather than filling the cache.
        val file = File(dir,"tacklebox-journal.json")
        file.writeText(gson.toJson(payload(state)))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** The document itself, separated from the file so its shape can be tested without a device. */
    fun payload(state:AppState):Map<String,Any?> = mapOf(
            "app" to "Tacklebox",
            "schema" to SCHEMA,
            "exportedAt" to Instant.now().toString(),
            "units" to state.settings.unitSystem.name,
            "catches" to state.catches.map { row -> mapOf(
                "caughtAt" to row.item.caughtAt.toString(),
                "species" to row.species?.name,
                "scientificName" to row.species?.scientificName,
                "weightGrams" to row.item.weightGrams,
                "lengthCm" to row.item.lengthCm,
                "returned" to row.item.returned,
                "water" to row.water?.name,
                "sessionId" to row.item.sessionId,
                "rig" to row.item.rig,
                "bait" to row.item.bait,
                "notes" to row.item.notes.ifBlank { null },
                // `hasPhoto`, as iOS writes it. The old `photoUri` leaked a device-specific path into the file and
                // meant nothing on any other device.
                "hasPhoto" to (row.item.photoUri!=null),
                "photoCount" to row.allPhotoUris.size,
                "conditions" to row.conditions?.let { mapOf(
                    "airTempC" to it.airTempC, "windDirection" to it.windDirection, "windSpeedKph" to it.windSpeedKph,
                    "pressureHpa" to it.pressureHpa, "pressureTrend" to it.pressureTrend, "moonPhase" to it.moonPhase) }
            ) },
            "waters" to state.waters.map { mapOf("name" to it.name,"type" to it.type.name,"region" to it.region,"swimNotes" to it.swimNotes) },
            // The id must be published: catches carry "sessionId", and without the matching id here an importer cannot
            // reconnect a catch to its session. Round-tripping a real export into the iOS app is how this was found.
            "sessions" to state.sessions.map { mapOf("id" to it.item.id,"startAt" to it.item.startAt.toString(),"endAt" to it.item.endAt?.toString(),"water" to it.water?.name,"catches" to it.catches.size,"notes" to it.item.notes) },
            "gear" to state.gear.map { mapOf("name" to it.name,"category" to it.category.name,"notes" to it.notes) },
            "presets" to state.presets.map { mapOf("name" to it.name,"kind" to it.kind.name) }
        )
}
