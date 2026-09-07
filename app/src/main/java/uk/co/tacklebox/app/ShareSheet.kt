/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.UnitSystem
import java.time.Duration
import java.time.ZoneId

/**
 * Sharing. "Share your season" and the catch card were empty lambdas — there was no Intent, no ACTION_SEND and no
 * FileProvider anywhere in the codebase (TB-A-06). iOS renders a picture card; this shares text, which is the
 * honest version of the same feature until an Android card renderer exists.
 */
object ShareSheet {
    fun catchCard(context:Context, row:CatchRow, units:UnitSystem) {
        val weight = row.item.weightGrams?.weight(units)
        val lines = listOfNotNull(
            row.species?.name,
            weight,
            row.water?.name?.let { "at $it" },
            row.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            "— logged with Tacklebox"
        )
        text(context, lines.joinToString("\n"), "Share this catch")
    }

    fun season(context:Context, state:AppState, year:Int) {
        val catches = state.catches.filter { it.item.caughtAt.atZone(ZoneId.systemDefault()).year == year }
        val biggest = catches.maxByOrNull { it.item.weightGrams ?: 0.0 }
        val hours = state.sessions.filter { it.item.endAt != null }
            .sumOf { Duration.between(it.item.startAt, it.item.endAt!!).toMinutes() } / 60
        val lines = listOfNotNull(
            "My $year on the water",
            "${catches.size} fish landed",
            "${catches.mapNotNull { it.species?.name }.distinct().size} species",
            biggest?.let { b -> "Best · ${b.species?.name.orEmpty()} ${b.item.weightGrams?.weight(state.settings.unitSystem).orEmpty()}".trim() },
            "$hours hours on the bank",
            "— logged with Tacklebox"
        )
        text(context, lines.joinToString("\n"), "Share your season")
    }

    fun file(context:Context, uri:Uri, mime:String, title:String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    private fun text(context:Context, body:String, title:String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }
        context.startActivity(Intent.createChooser(send, title))
    }
}
