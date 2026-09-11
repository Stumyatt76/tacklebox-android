/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.ui.*
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Editing a saved catch, mirroring the iOS `EditCatchView`: PHOTOS, SPECIES (every species, so a fish of a
 * discipline that has since been switched off can still be corrected), WEIGHT, LENGTH, Returned, WATER (the same
 * picker card and sheet as capture), WHEN ("Caught at", date and time), RIG, BAIT, NOTES, "Save changes".
 *
 * Capture has to fetch conditions, assign a session and celebrate a record, none of which should happen when
 * correcting a typo, which is why this is its own screen rather than a mode of the capture one.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable fun EditCatch(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val row=s.catches.firstOrNull{it.item.id==id}
    if(row==null){PushedScreen("Edit catch",onBack={nav.popBackStack()}){item{Empty("Catch not found","It may have been deleted.")}};return}
    val metric=s.settings.unitSystem==UnitSystem.METRIC
    val original=row.item
    val startPounds=original.weightGrams?.let{Weights.toPoundsAndOunces(it)}
    // The steppers show the stored values rounded to their own resolution. They are remembered so that a stepper
    // nobody touched leaves the stored value alone — saving used to rewrite 2126.25 g as 2120 g and 45.5 cm as 45 cm.
    val startKilograms=((original.weightGrams?:0.0)/1000).toInt()
    val startGrams=(((original.weightGrams?:0.0)%1000)/10).roundToInt().coerceIn(0,99)*10
    val startPoundsWhole=startPounds?.first?:0; val startOunces=startPounds?.second?:0
    val startCentimetres=(original.lengthCm?:0.0).roundToInt()
    val startInches=((original.lengthCm?:0.0)/2.54).roundToInt()
    var species by rememberSaveable{mutableStateOf(original.speciesId)}
    var kilograms by rememberSaveable{mutableIntStateOf(startKilograms)}
    var grams by rememberSaveable{mutableIntStateOf(startGrams)}
    var pounds by rememberSaveable{mutableIntStateOf(startPoundsWhole)}
    var ounces by rememberSaveable{mutableIntStateOf(startOunces)}
    var centimetres by rememberSaveable{mutableIntStateOf(startCentimetres)}
    var inches by rememberSaveable{mutableIntStateOf(startInches)}
    val weightChanged=if(metric)kilograms!=startKilograms||grams!=startGrams else pounds!=startPoundsWhole||ounces!=startOunces
    val lengthChanged=if(metric)centimetres!=startCentimetres else inches!=startInches
    var rig by rememberSaveable{mutableStateOf(original.rig.orEmpty())}
    var bait by rememberSaveable{mutableStateOf(original.bait.orEmpty())}
    var returned by rememberSaveable{mutableStateOf(original.returned)}
    var notes by rememberSaveable{mutableStateOf(original.notes)}
    // The water recorded on the fish, falling back to its session's — iOS's `resolvedWater`.
    var water by rememberSaveable{mutableStateOf(original.waterId ?: s.sessions.firstOrNull{it.item.id==original.sessionId}?.water?.id)}
    var caughtAt by rememberSaveable{mutableStateOf(original.caughtAt.toEpochMilli())}
    var photos by rememberSaveable{mutableStateOf(row.allPhotoUris)}
    var choosingWater by rememberSaveable{mutableStateOf(false)}
    var addingPreset by rememberSaveable{mutableStateOf<PresetKind?>(null)}

    val enteredGrams:Double=if(metric)(kilograms*1000+grams).toDouble() else (Weights.fromPoundsAndOunces(pounds.toString(),ounces.toString())?:0.0)
    val lengthCm:Double=if(metric)centimetres.toDouble() else inches*2.54

    CaptureScaffold("Edit Catch",onCancel={nav.popBackStack()}){
        item{SectionLabel("Photos");Box(Modifier.padding(top=10.dp)){PhotoStrip(photos,vm,saved=row.allPhotoUris.toSet()){photos=it}}}
        item{SectionLabel("Species")
            FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                s.species.forEach{sp->FilterChip(species==sp.id,{species=sp.id},{Text(sp.name)},colors=brassChipColours(),shape=CircleShape)}}}
        item{SectionLabel("Weight")
            Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                if(metric){
                    ValueStepper("KG",kilograms,0..100,modifier=Modifier.weight(1f)){kilograms=it}
                    ValueStepper("G",grams,0..990,10,Modifier.weight(1f)){grams=it}
                }else{
                    ValueStepper("LB",pounds,0..200,modifier=Modifier.weight(1f)){pounds=it}
                    ValueStepper("OZ",ounces,0..15,modifier=Modifier.weight(1f)){ounces=it}}}}
        item{SectionLabel("Length")
            Row(Modifier.padding(top=10.dp)){
                if(metric)ValueStepper("CM",centimetres,0..300,modifier=Modifier.weight(1f)){centimetres=it}
                else ValueStepper("IN",inches,0..120,modifier=Modifier.weight(1f)){inches=it}}}
        item{HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){
            Text(if(returned)"Returned" else "Kept",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
            Switch(returned,{returned=it})}}}
        item{SectionLabel("Water");Box(Modifier.padding(top=10.dp)){WaterPickerCard(s.waters.firstOrNull{it.id==water},showsOptionalHint=false){choosingWater=true}}}
        item{SectionLabel("When");Box(Modifier.padding(top=10.dp)){DateTimeField("Caught at",caughtAt,{caughtAt=it})}}
        item{PresetChips("Rig",rig,s.presets.filter{it.kind==PresetKind.RIG}.map{it.name},onPick={rig=it},onAdd={addingPreset=PresetKind.RIG})}
        item{PresetChips("Bait",bait,s.presets.filter{it.kind==PresetKind.BAIT}.map{it.name},onPick={bait=it},onAdd={addingPreset=PresetKind.BAIT})}
        item{SectionLabel("Notes")
            OutlinedTextField(notes,{notes=it},placeholder={Text("Took it on the drop, margin swim, three hours in…")},minLines=3,modifier=Modifier.fillMaxWidth().padding(top=10.dp).testTag("editNotes"))}
        item{Button({
                vm.updateCatch(original.copy(speciesId=species,
                    weightGrams=EditedMeasurement.resolve(original.weightGrams,weightChanged,enteredGrams),
                    lengthCm=EditedMeasurement.resolve(original.lengthCm,lengthChanged,lengthCm),
                    rig=rig.ifBlank{null},bait=bait.ifBlank{null},returned=returned,notes=notes.trim(),
                    waterId=water,caughtAt=Instant.ofEpochMilli(caughtAt),photoUri=photos.firstOrNull()),photos)
                nav.popBackStack()
            },Modifier.fillMaxWidth().height(52.dp).testTag("saveEdit"),shape=RoundedCornerShape(14.dp),
            enabled=species!=null){Text("Save changes",fontWeight=FontWeight.Bold)}}
    }
    if(choosingWater)WaterSelectionSheet(vm,s.waters,"Choose Water","Use this water",initialWaterId=water,preselectFirst=false,onDismiss={choosingWater=false}){water=it}
    addingPreset?.let{kind->AddPresetDialog(kind,s.presets,onDismiss={addingPreset=null}){name->vm.addPreset(name,kind);if(kind==PresetKind.RIG)rig=name else bait=name;addingPreset=null}}
}
