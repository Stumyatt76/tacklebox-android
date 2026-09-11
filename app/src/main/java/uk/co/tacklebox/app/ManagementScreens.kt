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

@Composable fun GearEditor(item: GearItem?, dismiss: () -> Unit, save: (GearItem) -> Unit) {
    var name by rememberSaveable { mutableStateOf(item?.name.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(item?.notes.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(item?.category ?: GearCategory.ROD) }
    AlertDialog(onDismissRequest=dismiss,title={Text(if(item==null)"Add gear" else "Edit gear")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Name")})
            GearCategory.entries.forEach { value ->
                FilterChip(category==value,{category=value},{Text(value.name.lowercase().replaceFirstChar(Char::uppercase))})
            }
            OutlinedTextField(notes,{notes=it},label={Text("Gear notes")},minLines=3)
        }
    },confirmButton={TextButton({save((item ?: GearItem(name="",category=category)).copy(name=name.trim(),category=category,notes=notes.trim()))},enabled=name.isNotBlank()){Text("Save")}},
    dismissButton={TextButton(dismiss){Text("Cancel")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DisciplinePreferences(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    HeritageCard {
        SectionLabel("Disciplines")
        Text("Choose the species you see when logging a catch. Keep at least one selected.",color=Muted)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Discipline.entries.forEach { discipline ->
                val selected=discipline.name in settings.activeDisciplines
                FilterChip(selected,{
                    val choices=settings.activeDisciplines.toMutableSet()
                    if(selected)choices.remove(discipline.name) else choices.add(discipline.name)
                    if(choices.isNotEmpty())onSave(settings.copy(activeDisciplines=choices.toList()))
                },{Text(discipline.name.lowercase().replaceFirstChar(Char::uppercase))},colors=brassChipColours(),shape=CircleShape)
            }
        }
    }
}

@Composable fun Breakdown(title: String, data: Map<String,Int>) {
    val ordered=if(title=="Catches over time")data.entries.sortedBy { it.key }.takeLast(12) else data.entries.sortedByDescending { it.value }.take(5)
    val maximum=(ordered.maxOfOrNull { it.value } ?: 1).coerceAtLeast(1)
    HeritageCard {
        Text(title,style=MaterialTheme.typography.titleLarge)
        if(ordered.isEmpty())Text("Not enough data yet",color=Muted)
        ordered.forEach { entry ->
            Column(Modifier.padding(top=10.dp)) {
                Row { Text(entry.key,Modifier.weight(1f));Text(entry.value.toString(),color=Brass) }
                Box(Modifier.fillMaxWidth().height(6.dp).background(Inset,CircleShape)) {
                    Box(Modifier.fillMaxWidth(entry.value.toFloat()/maximum).height(6.dp).background(Teal,CircleShape))
                }
            }
        }
    }
}

@Composable fun GearPerformance(s: AppState) {
    val bests=CatchFilter.personalBests(s.catches)
    val pbGear=bests.values.flatMap { listOfNotNull(it.item.rig,it.item.bait) }.filter(String::isNotBlank).distinct()
    HeritageCard {
        SectionLabel("Gear performance");Text("What earns its place",style=MaterialTheme.typography.headlineMedium)
        if(pbGear.isNotEmpty())Text("Behind your PBs · "+pbGear.joinToString(", "),color=BrassSoft)
        if(s.catches.size<3)Text("Log more catches with rig and bait details to reveal your best gear.",color=Muted)
    }
    Breakdown("Rigs",s.catches.mapNotNull { it.item.rig?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount())
    Breakdown("Baits",s.catches.mapNotNull { it.item.bait?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount())
}
