/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.data.AppSettings
import uk.co.tacklebox.app.data.Discipline
import uk.co.tacklebox.app.data.UnitSystem
import uk.co.tacklebox.app.ui.*

/**
 * Settings in the iOS order: UNITS → BACKUP (a neutral card pointing at Photo backup — Android has no cloud sync
 * and must not claim one) → DATA SERVICES → DISCIPLINES → Unlimited sessions → Photo backup → DATA → the footer.
 */
@Composable fun Settings(s:AppState,vm:MainViewModel,nav:NavHostController){
    var confirmReset by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    val exported by vm.exported.collectAsStateWithLifecycle()
    val importPlan by vm.importPlan.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let(vm::planImport)}
    // Hand the finished file straight to the share sheet, then clear it so rotating does not re-open the chooser.
    LaunchedEffect(exported){exported?.let{ShareSheet.file(context,it,"application/json","Export your Tacklebox journal");vm.clearExport()}}
    PushedScreen("Settings",onBack={nav.popBackStack()}){
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Units")
            // icon={}: Material draws a checkmark in the selected segment that iOS's control has no counterpart for.
            HeritageCard{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){UnitSystem.entries.forEachIndexed{i,u->SegmentedButton(s.settings.unitSystem==u,{vm.settings(s.settings.copy(unitSystem=u))},SegmentedButtonDefaults.itemShape(i,2),colors=SegmentedButtonDefaults.colors(activeContainerColor=Brass,activeContentColor=Background,inactiveContainerColor=Inset,inactiveContentColor=Ink),icon={}){Text(u.name.lowercase().replaceFirstChar(Char::uppercase))}}}}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Backup")
            HeritageCard(onClick={nav.navigate("backup")}){Row(Modifier.defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(44.dp).background(Inset,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Save,null,tint=BrassSoft,modifier=Modifier.size(20.dp))}
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Your vault stays on this device",fontWeight=FontWeight.SemiBold);Text("Use Photo backup below to keep a copy of your journal, photos included.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Data services")
            HeritageCard(onClick={nav.navigate("data-services")}){Row(Modifier.defaultMinSize(minHeight=44.dp).testTag("dataServices"),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(44.dp).background(Inset,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Hub,null,tint=Teal,modifier=Modifier.size(20.dp))}
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("Connect data services",style=MaterialTheme.typography.titleLarge);Text("Set up global tides and photo species ID",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                Icon(Icons.Default.ChevronRight,null,tint=BrassSoft)}}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Disciplines");DisciplinePreferences(s.settings,vm::settings)}}
        item{TextButton({nav.navigate("unlimited")},contentPadding=PaddingValues(0.dp)){Text("Unlimited sessions",color=BrassSoft)}}
        item{HeritageCard(onClick={nav.navigate("backup")}){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Storage,null,tint=Ink);Spacer(Modifier.width(8.dp));Text("Photo backup",style=MaterialTheme.typography.titleLarge)}
            Spacer(Modifier.height(6.dp));Text("Create or restore a complete copy, including catch photos.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Data");DataCard(onExport={vm.exportJson()},onImport={importPicker.launch("application/json")},onReset={confirmReset=true})}}
        item{Text("No ads · No analytics · No subscriptions",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=Dim,style=MaterialTheme.typography.bodyMedium)}
    }
    // The counts are live, as they are on iOS, so the angler is told exactly what is about to go.
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text("Reset to a fresh vault?")},
        text={Text(resetSummary(s))},
        confirmButton={TextButton({confirmReset=false;vm.resetVault{nav.popBackStack("vault",inclusive=false)}},modifier=Modifier.testTag("confirmReset")){Text("Reset and delete journal",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton({confirmReset=false}){Text("Cancel")}})
    // The plan is shown before anything is written, so the confirmation says exactly what will happen.
    importPlan?.let{plan->AlertDialog(onDismissRequest={vm.cancelImport()},title={Text("Import this journal?")},
        text={Text(importSummary(plan))},
        confirmButton={TextButton({vm.confirmImport()},modifier=Modifier.testTag("confirmImport")){Text("Import")}},
        dismissButton={TextButton({vm.cancelImport()}){Text("Cancel")}})}
    importResult?.let{r->AlertDialog(onDismissRequest={vm.clearImportResult()},title={Text("Import finished")},
        text={Text(importResultSummary(r))},
        confirmButton={TextButton({vm.clearImportResult()}){Text("OK")}})}
}

/** DISCIPLINES as iOS lays them out: the helper line, then a toggle per discipline; the last active one cannot be switched off. */
@Composable fun DisciplinePreferences(settings:AppSettings,onSave:(AppSettings)->Unit){
    HeritageCard{
        Text("Choose the species you see when logging a catch. Keep at least one selected.",color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(bottom=8.dp))
        Discipline.entries.forEachIndexed{i,discipline->
            val selected=discipline.name in settings.activeDisciplines
            val last=settings.activeDisciplines.filter{d->Discipline.entries.any{it.name==d}}==listOf(discipline.name)
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
                Text(discipline.name.lowercase().replaceFirstChar(Char::uppercase),Modifier.weight(1f))
                Switch(selected,{on->val choices=settings.activeDisciplines.toMutableList();if(on){if(discipline.name !in choices)choices+=discipline.name} else choices.remove(discipline.name)
                    if(choices.isNotEmpty())onSave(settings.copy(activeDisciplines=choices))},enabled=!last,modifier=Modifier.testTag("discipline_${discipline.name}"))}
            if(i<Discipline.entries.size-1)Rule()}}
}

/** The DATA card: export, import and "Reset to a fresh vault", with the iOS labels and sub-copy. */
@Composable fun DataCard(onExport:()->Unit,onImport:()->Unit,onReset:()->Unit){
    HeritageCard{
        DataRow(Icons.Default.FileUpload,"Export your journal","A JSON file of every catch, water, session and preset. Photos stay on your device.","exportJson",onExport)
        HorizontalDivider(Modifier.padding(vertical=10.dp),color=Muted.copy(alpha=.16f))
        // Export alone is an escape hatch; import is what makes the journal portable — between devices, after
        // a wiped phone, or in from another app.
        DataRow(Icons.Default.FileDownload,"Import a journal","Adds catches from a Tacklebox export. Nothing you already have is changed or removed.","importJournal",onImport)
        HorizontalDivider(Modifier.padding(vertical=10.dp),color=Muted.copy(alpha=.16f))
        DataRow(Icons.Default.Refresh,"Reset to a fresh vault",null,"resetVault",onReset,destructive=true)}
}

@Composable private fun DataRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String?,tag:String,onClick:()->Unit,destructive:Boolean=false){
    val colour=if(destructive)MaterialTheme.colorScheme.error else BrassSoft
    TextButton(onClick,Modifier.fillMaxWidth().testTag(tag),contentPadding=PaddingValues(0.dp)){
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=colour,modifier=Modifier.size(20.dp));Spacer(Modifier.width(12.dp))
            Text(title,color=colour,fontWeight=FontWeight.SemiBold)}}
    subtitle?.let{Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium)}
}

/** The reset confirmation, with live counts — the iOS body word for word. */
fun resetSummary(s:AppState):String=
    "Delete ${s.catches.size} catches and their photos, ${s.sessions.size} sessions, ${s.waters.size} waters, ${s.gear.size} gear items, your presets and any species you added? Units and disciplines return to defaults. Export your journal first if you want to keep a copy; JSON does not include photos. This cannot be undone."

/** Says exactly what an import will do, in the iOS order and wording: catches, waters, sessions, gear, presets, species. */
fun importSummary(plan:JournalImport.Plan):String{
    val parts=mutableListOf("${plan.newCatches} new ${if(plan.newCatches==1)"catch" else "catches"}")
    if(plan.newWaters>0)parts+="${plan.newWaters} new ${if(plan.newWaters==1)"water" else "waters"}"
    parts+="${plan.newSessions} new sessions"
    parts+="${plan.newGear} new gear items"
    parts+="${plan.newPresets} new presets"
    if(plan.newSpecies>0)parts+="${plan.newSpecies} new species"
    var text="This adds ${parts.joinToString(", ")}."
    if(plan.duplicateCatches>0)text+=" ${plan.duplicateCatches} ${if(plan.duplicateCatches==1)"catch is" else "catches are"} already in your vault and will be skipped."
    return text+" Nothing you already have is changed or removed. Photos are not included in an export, so imported catches arrive without them."
}

fun importResultSummary(r:JournalImport.Result):String{
    val parts=mutableListOf("${r.catches} ${if(r.catches==1)"catch" else "catches"}")
    if(r.waters>0)parts+="${r.waters} waters"
    if(r.sessions>0)parts+="${r.sessions} sessions"
    if(r.gear>0)parts+="${r.gear} gear items"
    if(r.presets>0)parts+="${r.presets} presets"
    if(r.species>0)parts+="${r.species} species"
    return "Added ${parts.joinToString(", ")}."
}
