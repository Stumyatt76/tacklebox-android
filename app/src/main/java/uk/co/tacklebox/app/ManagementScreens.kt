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

@Composable fun Sessions(s: AppState, vm: MainViewModel, nav: NavHostController) {
    var starting by rememberSaveable { mutableStateOf(false) }
    val access by vm.store.state.collectAsState()
    val canStart=SessionAllowance.canStart(s.settings.freeSessionsStarted,access.unlimited)
    val active = s.sessions.firstOrNull { it.item.endAt == null }
    Screen("Time on the bank","Sessions") {
        item { SessionNotificationPreference(s);Text("Shows elapsed time and catch count. Water names and photos stay private.",color=Muted) }
        item { TextButton({nav.navigate("unlimited")}) { Text(if(access.unlimited)"Unlimited sessions" else SessionAllowance.label(s.settings.freeSessionsStarted)) } }
        item { HeritageCard {
            if (active == null) {
                // One button into the shared water sheet, as iOS does; the first water arrives pre-ticked.
                Button({if(canStart)starting=true else nav.navigate("unlimited")},Modifier.fillMaxWidth().height(52.dp).testTag("startSession"),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(canStart)"Start a session" else "Unlock Unlimited",fontWeight=androidx.compose.ui.text.font.FontWeight.Bold) }
            } else {
                Text("SESSION IN PROGRESS",color=Teal)
                Text(active.water?.name ?: "Open session",style=MaterialTheme.typography.headlineMedium)
                Text(active.catches.size.toString()+if(active.catches.size==1)" catch" else " catches",color=Muted)
                OutlinedButton({nav.navigate("session/"+active.item.id)}) { Text("Open session") }
                Button({vm.stopSession(active.item.id)}) { Text("End session") }
            }
        } }
        if (s.sessions.isEmpty()) item { Empty("Your time on the bank","Start a session to keep its story and catch timeline.") }
        items(s.sessions.filter { it.item.id != active?.item?.id },key={it.item.id}) { row ->
            HeritageCard(onClick={nav.navigate("session/"+row.item.id)}) {
                Text(row.water?.name ?: "Open session",style=MaterialTheme.typography.titleLarge)
                Text(row.item.startAt.pretty(),color=Muted)
                Text(row.catches.size.toString()+" catches · "+row.catches.mapNotNull { it.weightGrams }.sum().weight(s.settings.unitSystem),color=BrassSoft)
            }
        }
    }
    if (starting) WaterSelectionSheet(vm,s.waters,"Start a Session","Start session",onDismiss={starting=false}) { vm.startSession(it) }
}

@Composable fun WaterChoice(waters: List<Water>, selected: Long?, onChange: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({expanded=true}) { Text(waters.firstOrNull { it.id==selected }?.name ?: "No water assigned") }
        DropdownMenu(expanded,{expanded=false}) {
            DropdownMenuItem(text={Text("No water assigned")},onClick={onChange(null);expanded=false})
            waters.forEach { water -> DropdownMenuItem(text={Text(water.name)},onClick={onChange(water.id);expanded=false}) }
        }
    }
}

@Composable fun SessionDetail(s: AppState, vm: MainViewModel, id: Long?, nav: NavHostController) {
    val row=s.sessions.firstOrNull { it.item.id==id }
    var editing by rememberSaveable { mutableStateOf(false) }
    var assigning by rememberSaveable { mutableStateOf(false) }
    PushedScreen("Session",onBack={nav.popBackStack()}) {
        if (row == null) item { Empty("Session unavailable","It may have been removed from the journal.") }
        else {
            item { HeritageCard {
                Text(row.water?.name ?: "Open session",style=MaterialTheme.typography.headlineMedium)
                // "WATER" — tap to assign or change through the shared sheet, as the iOS detail does.
                TextButton({assigning=true},Modifier.testTag("assignWater")) { Text(if(row.water==null)"Assign a water" else "Change water") }
                Text(row.item.startAt.pretty(),color=Muted)
                Text(row.item.endAt?.pretty() ?: "Still fishing",color=Muted)
                Text(row.catches.size.toString()+" catches · "+"%.1f h".format(java.time.Duration.between(row.item.startAt,row.item.endAt ?: Instant.now()).seconds.coerceAtLeast(0)/3600.0),color=BrassSoft)
                OutlinedButton({editing=true}) { Text("Edit session") }
                if (row.item.endAt==null) Button({vm.stopSession(row.item.id)}) { Text("End session") }
            } }
            item { HeritageCard { SectionLabel("Session notes");Text(row.item.notes.ifBlank { "No notes yet. Add the story of your day on the bank." }) } }
            item { SectionLabel("Catch timeline") }
            if (row.catches.isEmpty()) item { Text("No catches recorded in this session.",color=Muted) }
            items(s.catches.filter { it.item.sessionId==id }.sortedBy { it.item.caughtAt },key={it.item.id}) { fish ->
                HeritageCard(onClick={nav.navigate("catch/"+fish.item.id)}) {
                    Text(fish.species?.name ?: "Unknown species",style=MaterialTheme.typography.titleLarge)
                    Text(fish.item.caughtAt.pretty()+" · "+(fish.item.weightGrams?.weight(s.settings.unitSystem) ?: "—"),color=BrassSoft)
                }
            }
        }
    }
    if (editing && row != null) SessionEditor(row,s.waters,{editing=false}) { vm.saveSession(it) { editing=false } }
    if (assigning && row != null) WaterSelectionSheet(vm,s.waters,"Choose Water","Assign water",initialWaterId=row.item.waterId,onDismiss={assigning=false}) { vm.assignSessionWater(row.item,it) }
}

@Composable private fun SessionEditor(row: SessionRow, waters: List<Water>, dismiss: () -> Unit, save: (FishingSession) -> Unit) {
    var startMillis by rememberSaveable { mutableLongStateOf(row.item.startAt.toEpochMilli()) }
    var endMillis by rememberSaveable { mutableStateOf(row.item.endAt?.toEpochMilli()) }
    var water by rememberSaveable { mutableStateOf(row.item.waterId) }
    var notes by rememberSaveable { mutableStateOf(row.item.notes) }
    val start=Instant.ofEpochMilli(startMillis); val end=endMillis?.let(Instant::ofEpochMilli)
    val error=SessionRules.error(start,end,row.catches.map { it.caughtAt })
    AlertDialog(onDismissRequest=dismiss,title={Text("Edit session")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            WaterChoice(waters,water) { water=it }
            Text("Existing catches keep their individually recorded waters.",color=Muted,style=MaterialTheme.typography.bodySmall)
            SessionDateField("Started",start) { startMillis=it.toEpochMilli() }
            if(end!=null) SessionDateField("Finished",end) { endMillis=it.toEpochMilli() }
            OutlinedTextField(notes,{notes=it},label={Text("Session notes")},placeholder={Text("The story of your session")},minLines=3)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton({save(row.item.copy(startAt=start,endAt=end,waterId=water,notes=notes.trim()))},enabled=error==null){Text("Save")}},
    dismissButton={TextButton(dismiss){Text("Cancel")}})
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

@Composable fun SpeciesProgression(catches: List<CatchRow>, units: UnitSystem) {
    val samples=catches.filter { it.item.weightGrams!=null }.sortedBy { it.item.caughtAt }
    HeritageCard {
        Text("Your progression",style=MaterialTheme.typography.titleLarge)
        if(samples.size<2)Text("Log two measured catches to see how your record develops.",color=Muted)
        else {
            val weights=samples.map { it.item.weightGrams!! }
            val min=weights.min();val range=(weights.max()-min).coerceAtLeast(1.0)
            Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription="Catch weights over time; measurements listed below." }) {
                val first=samples.first().item.caughtAt.toEpochMilli()
                val span=(samples.last().item.caughtAt.toEpochMilli()-first).coerceAtLeast(1)
                val points=samples.mapIndexed { index,row -> Offset(8+(size.width-16)*(row.item.caughtAt.toEpochMilli()-first).toFloat()/span, size.height-8-(size.height-16)*((weights[index]-min)/range).toFloat()) }
                points.zipWithNext().forEach { (from,to)->drawLine(Teal,from,to,strokeWidth=4f) }
                points.forEach { drawCircle(Brass,5f,it) }
            }
            Text("Personal best · "+weights.max().weight(units),color=BrassSoft)
            samples.takeLast(10).forEach { Text(it.item.caughtAt.pretty()+" · "+(it.item.weightGrams?.weight(units) ?: "—"),color=Muted,style=MaterialTheme.typography.bodySmall) }
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
