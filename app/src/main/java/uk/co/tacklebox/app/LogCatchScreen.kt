/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.SpeciesSuggestion
import uk.co.tacklebox.app.ui.*
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The capture screen, matching the iOS `LogCatchView` section for section: photos, SPECIES, WEIGHT, the record
 * banner, LENGTH, Returned, WATER (picker card + `WaterSelectionSheet`), WHEN ("Caught at", date and time, never in
 * the future), RIG, BAIT, NOTES, AUTO-STAMPED CONDITIONS, the privacy line and "Save to the Vault".
 *
 * A saved record is celebrated with the iOS toast — "New {species} PB — {weight}" / "Beat your previous best by
 * {margin}" — for 2.2 seconds, and the screen then returns to the tab it was opened from. It used to push the catch
 * detail instead, which is not what the other phone does.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun LogCatch(s:AppState,vm:MainViewModel,nav:NavHostController){
    val metric=s.settings.unitSystem==UnitSystem.METRIC
    // rememberSaveable throughout: the activity is recreated on rotation and none of this survived it (TB-A-15).
    var species by rememberSaveable{mutableStateOf<Long?>(null)}
    var kilograms by rememberSaveable{mutableIntStateOf(0)}; var grams by rememberSaveable{mutableIntStateOf(0)}
    var pounds by rememberSaveable{mutableIntStateOf(0)}; var ounces by rememberSaveable{mutableIntStateOf(0)}
    var centimetres by rememberSaveable{mutableIntStateOf(0)}; var inches by rememberSaveable{mutableIntStateOf(0)}
    var rig by rememberSaveable{mutableStateOf("")}; var bait by rememberSaveable{mutableStateOf("")}
    var returned by rememberSaveable{mutableStateOf(true)}
    var notes by rememberSaveable{mutableStateOf("")}
    var caughtAt by rememberSaveable{mutableStateOf(System.currentTimeMillis())}
    var photos by rememberSaveable{mutableStateOf(listOf<String>())}
    // The most recently started session that has not been finished and began before the catch — the one it joins.
    val openSession=s.sessions.filter{it.item.endAt==null&&!it.item.startAt.isAfter(Instant.ofEpochMilli(caughtAt))}.maxByOrNull{it.item.startAt}
    var water by rememberSaveable{mutableStateOf(openSession?.water?.id)}
    var waterTouched by rememberSaveable{mutableStateOf(false)}
    // If a session is open the angler has already said where they are, so the water is pre-filled — including one
    // started from the free-session sheet a moment ago. They can still change it.
    LaunchedEffect(openSession?.item?.id){if(water==null&&!waterTouched)water=openSession?.water?.id}
    var choosingWater by rememberSaveable{mutableStateOf(false)}
    var addingSpecies by rememberSaveable{mutableStateOf(false)}
    var addingPreset by rememberSaveable{mutableStateOf<PresetKind?>(null)}
    // Both survive rotation: a recreated screen must neither forget the toast nor re-enable Save on a catch that is
    // already in the vault — that used to save the same fish twice.
    var celebration by rememberSaveable(stateSaver=PBCelebrationSaver){mutableStateOf<PBCelebration?>(null)}
    var saved by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(celebration){if(celebration!=null){delay(2200);nav.popBackStack()}}
    val needsSession by vm.showAccess.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val connection by vm.connection.state.collectAsStateWithLifecycle()
    var showingSuggestions by rememberSaveable{mutableStateOf(false)}
    var showingSetup by rememberSaveable{mutableStateOf(false)}
    val stored by vm.secrets.state.collectAsStateWithLifecycle()
    val configured=connection.connected||stored.speciesIdToken.isNotBlank()
    fun identify(){if(!configured){showingSetup=true;return};vm.identify(photos.firstOrNull());showingSuggestions=true}
    val scope=rememberCoroutineScope()

    val enteredGrams:Double=if(metric)(kilograms*1000+grams).toDouble() else (Weights.fromPoundsAndOunces(pounds.toString(),ounces.toString())?:0.0)
    val lengthCm:Double=if(metric)centimetres.toDouble() else inches*2.54
    val previousBest=species?.let{id->s.catches.filter{it.species?.id==id}.mapNotNull{it.item.weightGrams}.maxOrNull()}
    val isNewPB=enteredGrams>0 && previousBest!=null && enteredGrams>previousBest
    val isFirstOfSpecies=enteredGrams>0 && species!=null && previousBest==null
    val chosenWater=s.waters.firstOrNull{it.id==water}

    // Read once when the screen opens, and shown, so the angler can see what will be stamped on the fish and retry
    // a failed reading before saving.
    var stamped by remember{mutableStateOf<ConditionsSnapshot?>(null)}
    var conditionsAttempt by remember{mutableIntStateOf(0)}
    var reading by remember{mutableStateOf(true)}
    LaunchedEffect(conditionsAttempt){reading=true;stamped=runCatching{vm.captureConditions()}.getOrNull();reading=false}

    Box(Modifier.fillMaxSize()){
    CaptureScaffold("Log a Catch",onCancel={nav.popBackStack()}){
        // Photos, then — as soon as there is one — the identify control, above SPECIES as iOS places it.
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            PhotoStrip(photos,vm){photos=it}
            if(photos.isNotEmpty()){
                TextButton(::identify,Modifier.defaultMinSize(minHeight=44.dp).testTag("identifySpecies"),contentPadding=PaddingValues(0.dp)){
                    Icon(Icons.Default.AutoAwesome,null,tint=BrassSoft,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Identify species from photo",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
                Text("Sends the cover photo — the first one — to iNaturalist when you tap Identify. You choose whether to accept a suggestion.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}
        item{SectionLabel("Species")
            FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                val visible=s.species.filter{it.discipline.name in s.settings.activeDisciplines || it.id==species}
                visible.forEach{sp->FilterChip(species==sp.id,{species=sp.id},{Text(sp.name)},colors=brassChipColours(),shape=CircleShape)}
                FilterChip(false,{addingSpecies=true},{Text("＋ Add species")},colors=brassChipColours(),shape=CircleShape,modifier=Modifier.testTag("addSpecies"))}}
        item{SectionLabel("Weight")
            Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                if(metric){
                    ValueStepper("KG",kilograms,0..100,modifier=Modifier.weight(1f)){kilograms=it}
                    // 10 g steps: at 50 g a metric angler could not enter most real weights (TB-I-09 on iOS).
                    ValueStepper("G",grams,0..990,10,Modifier.weight(1f)){grams=it}
                }else{
                    ValueStepper("LB",pounds,0..200,modifier=Modifier.weight(1f)){pounds=it}
                    ValueStepper("OZ",ounces,0..15,modifier=Modifier.weight(1f)){ounces=it}}}}
        if(isNewPB)item{Banner("NEW PERSONAL BEST",Icons.Default.AutoAwesome,BrassSoft,"pbBanner")}
        else if(isFirstOfSpecies)item{Banner("YOUR FIRST ${s.species.firstOrNull{it.id==species}?.name?.uppercase().orEmpty()}",Icons.Default.Star,Teal,"firstOfSpeciesBanner")}
        item{SectionLabel("Length")
            Row(Modifier.padding(top=10.dp)){
                if(metric)ValueStepper("CM",centimetres,0..300,modifier=Modifier.weight(1f)){centimetres=it}
                else ValueStepper("IN",inches,0..120,modifier=Modifier.weight(1f)){inches=it}}}
        item{HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Returned",fontWeight=FontWeight.SemiBold);Text(if(returned)"Put back in the water" else "Kept",color=Muted,style=MaterialTheme.typography.bodyMedium)}
            Switch(returned,{returned=it})}}}
        item{SectionLabel("Water")
            Box(Modifier.padding(top=10.dp)){WaterPickerCard(chosenWater,showsOptionalHint=true){choosingWater=true}}
            // Changing the water here affects this fish only. Say so, rather than letting the angler assume they
            // have just moved the whole session.
            val sessionWater=openSession?.water
            if(chosenWater!=null&&sessionWater!=null&&chosenWater.id!=sessionWater.id)
                Text("This catch only — your open session stays at ${sessionWater.name}.",color=Dim,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=10.dp))}
        item{SectionLabel("When");Box(Modifier.padding(top=10.dp)){DateTimeField("Caught at",caughtAt,{caughtAt=it})}}
        item{PresetChips("Rig",rig,s.presets.filter{it.kind==PresetKind.RIG}.map{it.name},onPick={rig=it},onAdd={addingPreset=PresetKind.RIG})}
        item{PresetChips("Bait",bait,s.presets.filter{it.kind==PresetKind.BAIT}.map{it.name},onPick={bait=it},onAdd={addingPreset=PresetKind.BAIT})}
        item{SectionLabel("Notes")
            OutlinedTextField(notes,{notes=it},placeholder={Text("Took it on the drop, margin swim, three hours in…")},
                minLines=3,modifier=Modifier.fillMaxWidth().padding(top=10.dp).testTag("catchNotes"))}
        item{if(CapturePolicy.canStampCurrentWeather(Instant.ofEpochMilli(caughtAt))) ConditionsCard(reading,stamped,s.settings.unitSystem){conditionsAttempt++}
            else HeritageCard{Text("Historical weather is unavailable. Current conditions are only saved for catches from the last 15 minutes.",color=Muted)}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.Lock,null,tint=Dim,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp))
            Text("Your spot stays private",color=Dim,style=MaterialTheme.typography.bodyMedium)}}
        item{Button(onClick={
                val name=s.species.firstOrNull{it.id==species}?.name.orEmpty()
                val toast=PBCelebration.forSave(name,enteredGrams,previousBest,s.settings.unitSystem)
                saved=true
                vm.addCatch(species,enteredGrams.takeIf{it>0},lengthCm.takeIf{it>0},rig,bait,returned,water,photos,notes,Instant.ofEpochMilli(caughtAt),stamped,
                    onBlocked={saved=false}){
                    // Only celebrate an actual record. A first-of-species catch is marked on the form instead.
                    if(toast!=null)celebration=toast else nav.popBackStack()}
            },modifier=Modifier.fillMaxWidth().height(52.dp).testTag("saveCatch"),shape=RoundedCornerShape(14.dp),
            enabled=species!=null&&enteredGrams>0&&!saved){Text("Save to the Vault",fontWeight=FontWeight.Bold)}}
    }
    celebration?.let{PBCelebrationToast(it,Modifier.align(Alignment.BottomCenter))}
    }
    if(choosingWater)WaterSelectionSheet(vm,s.waters,"Choose Water","Use this water",initialWaterId=water,preselectFirst=false,onDismiss={choosingWater=false}){water=it;waterTouched=true}
    if(addingSpecies)AddSpeciesDialog(s.settings.activeDisciplines,onDismiss={addingSpecies=false}){name,discipline->vm.addSpecies(name,discipline){species=it};addingSpecies=false}
    addingPreset?.let{kind->AddPresetDialog(kind,s.presets,onDismiss={addingPreset=null}){name->vm.addPreset(name,kind);if(kind==PresetKind.RIG)rig=name else bait=name;addingPreset=null}}
    if(needsSession)FreeSessionSheet(s,vm,nav,Instant.ofEpochMilli(caughtAt),onDismiss={vm.showAccess.value=false})
    if(showingSetup)SpeciesIDSetupSheet(onOpenSettings={showingSetup=false;nav.navigate("data-services")},onDismiss={showingSetup=false})
    if(showingSuggestions)SpeciesSuggestionsSheet(suggestions,onRetry=::identify,onDismiss={showingSuggestions=false;vm.clearSuggestions()}){sug->
        vm.pickSuggestion(sug){species=it};showingSuggestions=false;vm.clearSuggestions()}
}

/** The WATER picker card: "Choose a water" or the chosen name over "type · region", opening the selection sheet. */
@Composable fun WaterPickerCard(water:Water?,showsOptionalHint:Boolean,onClick:()->Unit){
    HeritageCard(onClick=onClick){
        Row(Modifier.defaultMinSize(minHeight=44.dp).testTag("waterPicker"),verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.WaterDrop,null,tint=Brass);Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                Text(water?.name?:"Choose a water",fontWeight=FontWeight.SemiBold,color=if(water==null)Muted else Ink)
                if(water!=null)Text(water.subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium)
                else if(showsOptionalHint)Text("Optional — but it powers the water filter and your passport",color=Dim,style=MaterialTheme.typography.bodyMedium)}
            Icon(Icons.Default.ChevronRight,null,tint=Dim)}}
}

data class PBCelebration(val species:String,val weight:String,val margin:String?){
    companion object {
        /** The toast for a save, or null: only a genuine record — heavier than a previous fish of the species — is celebrated. */
        fun forSave(species:String,enteredGrams:Double,previousBest:Double?,unit:UnitSystem):PBCelebration? =
            if(previousBest!=null&&enteredGrams>previousBest)PBCelebration(species,enteredGrams.weight(unit),(enteredGrams-previousBest).weight(unit)) else null
    }
}
/** Keeps the toast across rotation. */
val PBCelebrationSaver=androidx.compose.runtime.saveable.listSaver<PBCelebration?,String?>(
    save={if(it==null)emptyList() else listOf(it.species,it.weight,it.margin)},
    restore={if(it.isEmpty())null else PBCelebration(it[0]!!,it[1]!!,it[2])})

/** "New {species} PB — {weight}" / "Beat your previous best by {margin}", as the iOS toast reads. */
@Composable fun PBCelebrationToast(c:PBCelebration,modifier:Modifier=Modifier){
    Box(modifier.padding(20.dp).testTag("pbToast")){HeritageCard{
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.AutoAwesome,null,tint=Brass,modifier=Modifier.size(24.dp));Spacer(Modifier.width(14.dp))
            Column{
                Text("New ${c.species} PB — ${c.weight}",fontWeight=FontWeight.Bold)
                c.margin?.let{Text("Beat your previous best by $it",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}}
}

/** "Add Species": a name and a discipline drawn from the active ones, selected on the form as soon as it is added. */
@Composable fun AddSpeciesDialog(activeDisciplines:List<String>,onDismiss:()->Unit,onAdd:(String,Discipline)->Unit){
    val choices=Discipline.entries.filter{it.name in activeDisciplines}.ifEmpty{listOf(Discipline.CARP)}
    var name by rememberSaveable{mutableStateOf("")}
    var discipline by rememberSaveable{mutableStateOf(choices.first())}
    var expanded by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add Species")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Species name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("speciesName"))
            Box{Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp).clickable{expanded=true}.testTag("speciesDiscipline"),verticalAlignment=Alignment.CenterVertically){
                    Text("Discipline",Modifier.weight(1f))
                    Text(discipline.name.lowercase().replaceFirstChar(Char::uppercase),color=BrassSoft,fontWeight=FontWeight.SemiBold)
                    Icon(Icons.Default.ArrowDropDown,null,tint=BrassSoft)}
                DropdownMenu(expanded,{expanded=false}){choices.forEach{d->DropdownMenuItem(text={Text(d.name.lowercase().replaceFirstChar(Char::uppercase))},onClick={discipline=d;expanded=false})}}}}},
        confirmButton={TextButton({if(name.isNotBlank())onAdd(name.trim(),discipline)},enabled=name.isNotBlank()){Text("Add")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

/** "Add rig" / "Add bait": saves the preset to My Tacklebox and selects it. An existing name is reused, not duplicated. */
@Composable fun AddPresetDialog(kind:PresetKind,presets:List<TacklePreset>,onDismiss:()->Unit,onAdd:(String)->Unit){
    var name by rememberSaveable{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add ${kind.name.lowercase()}")},
        text={Column{OutlinedTextField(name,{name=it},label={Text("Name")},singleLine=true);Spacer(Modifier.height(8.dp));Text("Save it to My Tacklebox and select it for this catch.",color=Muted,style=MaterialTheme.typography.bodyMedium)}},
        confirmButton={TextButton({val trimmed=name.trim();if(trimmed.isNotEmpty()){
            val existing=presets.firstOrNull{it.kind==kind&&it.name.equals(trimmed,ignoreCase=true)}
            onAdd(existing?.name?:trimmed)}},enabled=name.isNotBlank()){Text("Add")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

/**
 * Shown when a free angler tries to save a catch with no session running — the iOS `FreeSessionSheet`.
 *
 * It offers the one thing needed: start a session (which spends an allowance) or, when both are used, Unlimited.
 * The catch form behind it keeps its entries, and "Return to catch" goes straight back to them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FreeSessionSheet(s:AppState,vm:MainViewModel,nav:NavHostController,caughtAt:Instant,onDismiss:()->Unit){
    val store by vm.store.state.collectAsStateWithLifecycle()
    val used=s.settings.freeSessionsStarted
    val canStart=SessionAllowance.canStart(used,store.unlimited)
    var starting by rememberSaveable{mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Background,dragHandle=null){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=32.dp)){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp)){TextButton(onDismiss){Text("Close",color=BrassSoft,fontWeight=FontWeight.SemiBold)}}
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                Column{Text("YOUR FREE SESSIONS",color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(if(canStart)"Start a session first" else "Your free sessions are complete",style=MaterialTheme.typography.headlineLarge)}
                Text(if(canStart)"Catches are logged inside a session. Start one now and this catch will join it — your entries are still here."
                     else "Two free sessions are complete. Unlock Unlimited to keep logging; everything you have recorded stays available.",color=Muted)
                HeritageCard{Text(if(store.unlimited)"Unlimited sessions" else SessionAllowance.label(used),fontWeight=FontWeight.SemiBold)}
                if(canStart)Button({starting=true},Modifier.fillMaxWidth().height(52.dp).testTag("startSessionFromCatch"),shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Start a session",fontWeight=FontWeight.Bold)}
                else Button({onDismiss();nav.navigate("unlimited")},Modifier.fillMaxWidth().height(52.dp).testTag("unlockFromCatch"),shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("Unlock Unlimited",fontWeight=FontWeight.Bold)}
                TextButton(onDismiss,Modifier.fillMaxWidth().height(44.dp).background(Inset,RoundedCornerShape(12.dp)).testTag("returnToCatch")){Text("Return to catch",color=BrassSoft,fontWeight=FontWeight.SemiBold)}}}
    }
    // Started at the catch's own time (never later than now), so the fish already on the form joins the session it
    // just started rather than being saved without one.
    if(starting)WaterSelectionSheet(vm,s.waters,"Start a Session","Start session",onDismiss={starting=false}){water->vm.startSession(water,caughtAt){onDismiss()}}
}

/**
 * The capture screen's frame: a Cancel action and a centred inline title, with no tab bar behind it.
 *
 * iOS presents Log a Catch as a modal sheet. Android had it as an ordinary tab destination with the bottom bar
 * still showing, so the same task looked like a different kind of thing on each platform (TB-P-04).
 */
@Composable fun CaptureScaffold(title:String,onCancel:()->Unit,content:LazyListScope.()->Unit){
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onCancel){Text("Cancel",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
            Spacer(Modifier.weight(1f));Text(title,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))}
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp),content=content)}
}

/** A record or a first, marked on the form rather than celebrated after the fact. */
@Composable fun Banner(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector,colour:Color,tag:String){
    Row(Modifier.fillMaxWidth().background(colour,RoundedCornerShape(12.dp)).padding(12.dp).testTag(tag),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
        Icon(icon,null,tint=Background,modifier=Modifier.size(16.dp));Spacer(Modifier.width(8.dp))
        Text(text,color=Background,style=MaterialTheme.typography.labelLarge,letterSpacing=1.sp)}
}

/** What is about to be stamped on the fish, and a way to try again when the reading failed. */
@Composable fun ConditionsCard(reading:Boolean,stamped:ConditionsSnapshot?,unit:UnitSystem,onRetry:()->Unit){
    HeritageCard{
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.Air,null,tint=Brass,modifier=Modifier.size(15.dp));Spacer(Modifier.width(6.dp))
            Text("AUTO-STAMPED CONDITIONS",color=Brass,style=MaterialTheme.typography.labelLarge,letterSpacing=1.3.sp)}
        Spacer(Modifier.height(8.dp))
        when{
            reading->Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(16.dp),color=BrassSoft,strokeWidth=2.dp);Spacer(Modifier.width(9.dp));Text("Reading conditions…",color=Muted)}
            stamped?.airTempC==null&&stamped?.pressureHpa==null->{
                Text("Conditions unavailable — offline or location off",color=Muted)
                TextButton(onRetry,Modifier.testTag("retryConditions")){Icon(Icons.Default.Refresh,null,tint=BrassSoft,modifier=Modifier.size(15.dp));Spacer(Modifier.width(6.dp));Text("Retry",color=BrassSoft,fontWeight=FontWeight.SemiBold)}}
            else->Text(stamped!!.summary(unit),color=Ink)}}
}

/**
 * Rig and bait, as a grid of saved presets plus an Add. Anything new is added as a preset, so it is there next time.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun PresetChips(label:String,value:String,presets:List<String>,onPick:(String)->Unit,onAdd:(()->Unit)?){
    SectionLabel(label)
    FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        presets.forEach{p->FilterChip(value==p,{onPick(if(value==p)"" else p)},{Text(p)},colors=brassChipColours(),shape=CircleShape)}
        if(onAdd!=null)FilterChip(false,onAdd,{Text("＋ Add")},colors=brassChipColours(),shape=CircleShape)}
}

/** "Species identification" — shown when neither a sign-in nor a token is set up. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SpeciesIDSetupSheet(onOpenSettings:()->Unit,onDismiss:()->Unit){
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Background,dragHandle=null){
        Column(Modifier.fillMaxWidth().padding(bottom=32.dp)){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(onDismiss){Text("Close",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.weight(1f));Text("Species identification",fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Spacer(Modifier.width(64.dp))}
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                Icon(Icons.Default.Key,null,tint=Brass,modifier=Modifier.size(34.dp))
                Text("Species identification isn't set up yet",style=MaterialTheme.typography.headlineMedium)
                Text("Connect iNaturalist in Settings to identify a species from your catch photo. Manual species selection is always available.",color=Muted)
                Button(onOpenSettings,Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp)){Text("Open Settings",fontWeight=FontWeight.Bold)}}}}
}

/** "Suggested species": the spinner, up to five rows with a thumbnail and "NN% match", or the failure and "Try again". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SpeciesSuggestionsSheet(state:LiveState<List<SpeciesSuggestion>>,onRetry:()->Unit,onDismiss:()->Unit,onSelect:(SpeciesSuggestion)->Unit){
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Background,dragHandle=null){
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f)){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(onDismiss){Text("Close",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.weight(1f));Text("Suggested species",fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Spacer(Modifier.width(64.dp))}
            when(state){
                is LiveState.Data->Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    state.value.forEach{sug->HeritageCard(onClick={onSelect(sug)}){Row(Modifier.defaultMinSize(minHeight=58.dp),verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).background(Inset),contentAlignment=Alignment.Center){
                            if(sug.thumbnailUrl!=null)coil.compose.AsyncImage(sug.thumbnailUrl,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                            else FishGlyph(Teal,Modifier.fillMaxSize().padding(8.dp))}
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)){
                            Text(sug.displayName,fontWeight=FontWeight.Bold)
                            Text(sug.scientificName,color=Muted,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic,style=MaterialTheme.typography.bodyMedium)
                            Text("${sug.score.roundToInt()}% match",color=BrassSoft,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}
                        Icon(Icons.Default.ChevronRight,null,tint=Dim)}}}}
                is LiveState.Error->Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                    Icon(Icons.Default.WifiOff,null,tint=Brass,modifier=Modifier.size(32.dp))
                    Text(state.message,color=Muted,textAlign=TextAlign.Center)
                    Button(onRetry,Modifier.defaultMinSize(minWidth=140.dp,minHeight=44.dp),shape=RoundedCornerShape(12.dp)){Text("Try again",fontWeight=FontWeight.Bold)}}
                else->Column(Modifier.fillMaxWidth().padding(40.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
                    CircularProgressIndicator(color=Brass);Text("Asking iNaturalist…",color=Muted)}}}}
}
