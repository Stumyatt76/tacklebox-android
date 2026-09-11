/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.ui.*
import java.time.*
import java.time.format.DateTimeFormatter

object SessionRules {
    fun error(start: Instant, end: Instant?, catches: List<Instant>, now: Instant = Instant.now()): String? = when {
        start > now || (end != null && end > now) -> "Session dates cannot be in the future."
        end != null && end < start -> "The finish must be after the start."
        catches.any { it < start || (end != null && it > end) } -> "Keep the session dates around its recorded catches."
        else -> null
    }
}

@Composable fun SessionDateField(label: String, value: Instant, onChange: (Instant) -> Unit) {
    val context = LocalContext.current
    val local = value.atZone(ZoneId.systemDefault())
    Column {
        Text(label, color=Muted)
        OutlinedButton({
            DatePickerDialog(context, { _, y, m, d ->
                TimePickerDialog(context, { _, hour, minute ->
                    onChange(LocalDateTime.of(y,m+1,d,hour,minute).atZone(ZoneId.systemDefault()).toInstant())
                },local.hour,local.minute,true).show()
            },local.year,local.monthValue-1,local.dayOfMonth).show()
        }) { Text(value.pretty()) }
    }
}
