/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.ui.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** "6.5 h" — the session duration as the iOS detail prints it. */
fun sessionDuration(start:Instant,end:Instant?,now:Instant=Instant.now()):String="%.1f h".format(maxOf(0.0,Duration.between(start,end?:now).seconds/3600.0))

/**
 * The Sessions tab, in the iOS order: the live-surface toggle and its note, the allowance link, the LIVE card or
 * the "Start a session" button (into the shared water sheet), the empty state, then the session rows with the
 * catch count in brass serif.
 */
@Composable fun Sessions(s:AppState,vm:MainViewModel,nav:NavHostController){
    var starting by rememberSaveable{mutableStateOf(false)}
    val access by vm.store.state.collectAsState()
    val canStart=SessionAllowance.canStart(s.settings.freeSessionsStarted,access.unlimited)
    val live=s.sessions.firstOrNull{it.item.endAt==null}
    Screen("Time on the bank","Sessions"){
        item{SessionNotificationPreference(s);Text("Shows elapsed time and catch count. Water names and photos stay private. Availability and duration depend on Android.",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        item{TextButton({nav.navigate("unlimited")},contentPadding=PaddingValues(0.dp)){Text(if(access.unlimited)"Unlimited sessions" else SessionAllowance.label(s.settings.freeSessionsStarted),color=BrassSoft)}}
        item{if(live!=null)LiveSessionCard(live,vm,nav)
            else Button({if(canStart)starting=true else nav.navigate("unlimited")},Modifier.fillMaxWidth().height(52.dp).testTag("startSession"),shape=RoundedCornerShape(14.dp)){
                Icon(Icons.Filled.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(canStart)"Start a session" else "Unlock Unlimited",fontWeight=FontWeight.Bold)}}
        if(s.sessions.isEmpty())item{Empty("Start your first session","Choose a water and keep a simple record of your time on the bank.",Icons.Default.EditCalendar)}
        items(s.sessions.filter{it.item.id!=live?.item?.id},key={it.item.id}){row->
            HeritageCard(onClick={nav.navigate("session/"+row.item.id)}){Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(row.water?.name?:"Open session",style=MaterialTheme.typography.titleLarge)
                    Text(row.item.startAt.pretty(),color=Muted,style=MaterialTheme.typography.bodyMedium)}
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text("${row.catches.size}",style=MaterialTheme.typography.headlineMedium,color=Brass)
                    Text(row.catches.mapNotNull{it.weightGrams}.sum().weight(s.settings.unitSystem),color=Muted,style=MaterialTheme.typography.bodySmall)}}}}
    }
    if(starting)WaterSelectionSheet(vm,s.waters,"Start a Session","Start session",onDismiss={starting=false}){vm.startSession(it)}
}

/** The open session: a teal "LIVE" capsule, the count, the water and "Started hh:mm" (tap for the detail), "End session". */
@Composable fun LiveSessionCard(session:SessionRow,vm:MainViewModel,nav:NavHostController){
    Column(Modifier.fillMaxWidth().border(1.5.dp,Teal.copy(alpha=.65f),RoundedCornerShape(18.dp))){HeritageCard{
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("LIVE",color=Background,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp,modifier=Modifier.background(Teal,CircleShape).padding(horizontal=9.dp,vertical=5.dp))
            Spacer(Modifier.weight(1f))
            Text("${session.catches.size} ${if(session.catches.size==1)"catch" else "catches"}",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp).clickable{nav.navigate("session/"+session.item.id)}.testTag("openLiveSession"),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(session.water?.name?:"Open session",style=MaterialTheme.typography.headlineMedium)
                Text("Started ${session.item.startAt.atZone(ZoneId.systemDefault()).toLocalTime().hm()}",color=Muted,style=MaterialTheme.typography.bodyMedium)}
            Icon(Icons.Default.ChevronRight,null,tint=Brass)}
        Spacer(Modifier.height(13.dp))
        TextButton({vm.stopSession(session.item.id)},Modifier.fillMaxWidth().height(44.dp).background(Inset,RoundedCornerShape(12.dp)).testTag("endSession")){Text("End session",color=BrassSoft,fontWeight=FontWeight.Bold)}}}
}

/**
 * A session, in the iOS order: long-date eyebrow over the water name, the conditions line, the WATER card
 * (tap to assign through the shared sheet; "End session" while open), Catches / Total weight / Duration tiles,
 * the auto-saving SESSION NOTES field, the CATCH TIMELINE with thumbnails, times and PB pills, and "Delete this
 * session". "Edit session" sits in the top bar.
 */
@Composable fun SessionDetail(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val row=s.sessions.firstOrNull{it.item.id==id}
    var editing by rememberSaveable{mutableStateOf(false)}
    var assigning by rememberSaveable{mutableStateOf(false)}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    val unit=s.settings.unitSystem
    val catches=s.catches.filter{it.item.sessionId==id}.sortedBy{it.item.caughtAt}
    val bests=remember(s.catches){CatchFilter.personalBests(s.catches)}
    var notes by remember(id){mutableStateOf(row?.item?.notes.orEmpty())}
    // Saved as the angler types, as the iOS field does, after a short pause rather than on every keystroke.
    LaunchedEffect(notes){if(row!=null&&notes!=row.item.notes){delay(400);vm.saveSession(row.item.copy(notes=notes))}}
    PushedScreen("",onBack={nav.popBackStack()},actions={if(row!=null)TextButton({editing=true},Modifier.testTag("editSession")){Text("Edit session",color=BrassSoft)}},
        eyebrow=row?.item?.startAt?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),heading=row?.let{it.water?.name?:"Open Session"}){
        if(row==null){item{Empty("Session unavailable","It may have been removed from the journal.")};return@PushedScreen}
        item{Text(catches.firstNotNullOfOrNull{it.conditions?.summary(unit,includeMoonWord=false)?.takeIf{l->l.isNotBlank()}}?:"Conditions not recorded",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        item{HeritageCard{
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp).clickable{assigning=true}.testTag("assignWater"),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){SectionLabel("Water");Text(row.water?.name?:"Assign a water",fontWeight=FontWeight.SemiBold)}
                Icon(Icons.Default.ChevronRight,null,tint=Brass)}
            if(row.item.endAt==null){Spacer(Modifier.height(10.dp));Rule();Spacer(Modifier.height(4.dp))
                TextButton({vm.stopSession(row.item.id)},Modifier.fillMaxWidth().height(44.dp).testTag("endSession")){Text("End session",color=BrassSoft,fontWeight=FontWeight.Bold)}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Stat("${catches.size}","Catches",Modifier.weight(1f))
            Stat(catches.mapNotNull{it.item.weightGrams}.sum().weight(unit),"Total weight",Modifier.weight(1f))
            Stat(sessionDuration(row.item.startAt,row.item.endAt),"Duration",Modifier.weight(1f))}}
        item{SectionLabel("Session notes")}
        item{HeritageCard{OutlinedTextField(notes,{notes=it},placeholder={Text("Add notes from the bank…")},minLines=4,modifier=Modifier.fillMaxWidth().testTag("sessionNotes"))}}
        item{SectionLabel("Catch timeline")}
        items(catches,key={it.item.id}){fish->HeritageCard(onClick={nav.navigate("catch/"+fish.item.id)}){Row(verticalAlignment=Alignment.CenterVertically){
            CatchThumbnail(fish);Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(fish.species?.name?:"Unknown",fontWeight=FontWeight.SemiBold)
                Text(fish.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalTime().hm(),color=Muted,style=MaterialTheme.typography.bodyMedium)}
            if(fish.species!=null&&bests[fish.species.name]?.item?.id==fish.item.id){Text("PB",color=Background,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.background(Brass,CircleShape).padding(horizontal=8.dp,vertical=4.dp));Spacer(Modifier.width(8.dp))}
            Text(fish.item.weightGrams?.weight(unit)?:"—",color=BrassSoft,style=MaterialTheme.typography.titleMedium);Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight,null,tint=Dim,modifier=Modifier.size(16.dp))}}}
        item{TextButton({confirmDelete=true},Modifier.fillMaxWidth().height(44.dp).testTag("deleteSession"),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete this session",fontWeight=FontWeight.SemiBold)}}
    }
    if(editing&&row!=null)SessionEditor(row,s.waters,{editing=false}){vm.saveSession(it){editing=false}}
    if(assigning&&row!=null)WaterSelectionSheet(vm,s.waters,"Choose Water","Assign water",initialWaterId=row.item.waterId,onDismiss={assigning=false}){vm.assignSessionWater(row.item,it)}
    if(confirmDelete&&row!=null)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this session?")},
        text={Text("Its ${catches.size} ${if(catches.size==1)"catch is" else "catches are"} kept in your vault and simply lose this session. The free-session allowance is not returned. This cannot be undone.")},
        confirmButton={TextButton({confirmDelete=false;vm.deleteSession(row.item.id);nav.popBackStack()}){Text("Delete session",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

/**
 * "Edit session": WATER (a menu with "No water assigned"), TIME ON THE BANK ("Started" / "Finished", date and
 * time, never in the future), SESSION NOTES, and the iOS validation messages — presented as a sheet with
 * Cancel / Save, as the iOS form is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SessionEditor(row:SessionRow,waters:List<Water>,dismiss:()->Unit,save:(FishingSession)->Unit){
    var startMillis by rememberSaveable{mutableLongStateOf(row.item.startAt.toEpochMilli())}
    var endMillis by rememberSaveable{mutableStateOf(row.item.endAt?.toEpochMilli())}
    var water by rememberSaveable{mutableStateOf(row.item.waterId)}
    var notes by rememberSaveable{mutableStateOf(row.item.notes)}
    var expanded by remember{mutableStateOf(false)}
    val start=Instant.ofEpochMilli(startMillis);val end=endMillis?.let(Instant::ofEpochMilli)
    val error=SessionRules.error(start,end,row.catches.map{it.caughtAt})
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Background,dragHandle=null){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=32.dp)){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(dismiss){Text("Cancel",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.weight(1f));Text("Edit session",fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f))
                TextButton({save(row.item.copy(startAt=start,endAt=end,waterId=water,notes=notes.trim()))},enabled=error==null,modifier=Modifier.testTag("saveSession")){Text("Save",color=if(error==null)BrassSoft else Muted,fontWeight=FontWeight.Bold)}}
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                SectionLabel("Water")
                HeritageCard{
                    Box{Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp).clickable{expanded=true},verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Default.WaterDrop,null,tint=Brass);Spacer(Modifier.width(10.dp))
                            Text(waters.firstOrNull{it.id==water}?.name?:"No water assigned",color=BrassSoft,fontWeight=FontWeight.SemiBold);Icon(Icons.Default.ArrowDropDown,null,tint=BrassSoft)}
                        DropdownMenu(expanded,{expanded=false}){
                            DropdownMenuItem(text={Text("No water assigned")},onClick={water=null;expanded=false})
                            waters.forEach{w->DropdownMenuItem(text={Text(w.name)},onClick={water=w.id;expanded=false})}}}
                    Text("Existing catches keep their individually recorded waters.",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                SectionLabel("Time on the bank")
                DateTimeField("Started",startMillis,{startMillis=it},tag="started")
                if(end!=null)DateTimeField("Finished",endMillis!!,{endMillis=it},tag="finished")
                SectionLabel("Session notes")
                HeritageCard{OutlinedTextField(notes,{notes=it},placeholder={Text("The story of your session")},minLines=4,modifier=Modifier.fillMaxWidth())}
                error?.let{Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Warning,null,tint=BrassSoft,modifier=Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text(it,color=BrassSoft,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}}}}
    }
}
