/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
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
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.ui.*

/** What earns its place — the iOS `performance(_:)` ranking, case- and space-insensitive. */
object GearRules {
    data class Item(val name:String,val count:Int,val proportion:Float)
    fun performance(values:List<String?>):List<Item> {
        val names=LinkedHashMap<String,Pair<String,Int>>()
        values.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }.forEach { v -> val key=v.lowercase(); val current=names[key] ?: (v to 0); names[key]=current.first to current.second+1 }
        val ranked=names.values.sortedWith(compareByDescending<Pair<String,Int>> { it.second }.thenBy { it.first.lowercase() })
        val maximum=(ranked.firstOrNull()?.second ?: 1).coerceAtLeast(1)
        return ranked.map { Item(it.first, it.second, it.second.toFloat()/maximum) }
    }
    /** "{rig} · {bait}", either alone, or nothing — what sits behind the personal bests. */
    fun pbSummary(bests:Collection<CatchRow>):String? {
        val rig=performance(bests.map { it.item.rig }).firstOrNull()?.name; val bait=performance(bests.map { it.item.bait }).firstOrNull()?.name
        return listOfNotNull(rig,bait).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/**
 * My Tacklebox in the iOS order: GEAR (+ "Add gear") with category icons and notes, PRESETS (+ "Add preset") as a
 * RIG card and a BAIT card, then PERFORMANCE with BEST RIG / TOP BAIT tiles, BEHIND YOUR PBS and the ranked bars.
 */
@Composable fun Tacklebox(s:AppState,vm:MainViewModel,nav:NavHostController){
    var editingGear by remember{mutableStateOf<GearItem?>(null)}
    var showGearForm by rememberSaveable{mutableStateOf(false)}
    var showPresetForm by rememberSaveable{mutableStateOf(false)}
    val rigs=GearRules.performance(s.catches.map{it.item.rig}); val baits=GearRules.performance(s.catches.map{it.item.bait})
    val pbSummary=GearRules.pbSummary(CatchFilter.personalBests(s.catches).values)
    PushedScreen("",onBack={nav.popBackStack()},actions={TextButton({nav.popBackStack()}){Text("Done",color=BrassSoft,fontWeight=FontWeight.SemiBold)}},eyebrow="Ready for the bank",heading="My Tacklebox"){
        item{SectionHeader("Gear","Add gear","addGear"){editingGear=null;showGearForm=true}}
        item{HeritageCard{
            if(s.gear.isEmpty())Text("Add rods, reels and terminal tackle to your inventory.",color=Muted,modifier=Modifier.defaultMinSize(minHeight=44.dp))
            else s.gear.forEachIndexed{i,g->
                Row(Modifier.defaultMinSize(minHeight=60.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(gearIcon(g.category),null,tint=Teal,modifier=Modifier.width(28.dp));Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(g.name,fontWeight=FontWeight.SemiBold);Text(g.category.title,color=BrassSoft,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
                        if(g.notes.isNotBlank())Text(g.notes,color=Muted,style=MaterialTheme.typography.bodyMedium)}
                    IconButton({editingGear=g;showGearForm=true}){Icon(Icons.Default.Edit,"Edit ${g.name}",tint=BrassSoft)}
                    IconButton({vm.deleteGear(g)}){Icon(Icons.Outlined.Delete,"Delete ${g.name}",tint=Muted)}}
                if(i<s.gear.size-1)Rule()}}}
        item{SectionHeader("Presets","Add preset","addPreset"){showPresetForm=true}}
        PresetKind.entries.forEach{kind->item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel(kind.name)
            HeritageCard{val matching=s.presets.filter{it.kind==kind}
                if(matching.isEmpty())Text("No ${kind.name.lowercase()} presets yet.",color=Muted,modifier=Modifier.defaultMinSize(minHeight=44.dp))
                else matching.forEachIndexed{i,p->Row(Modifier.defaultMinSize(minHeight=52.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(p.name,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));IconButton({vm.deletePreset(p)}){Icon(Icons.Outlined.Delete,"Delete ${p.name}",tint=Muted)}}
                    if(i<matching.size-1)Rule()}}}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Performance")
            HeritageCard{Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Verified,null,tint=Brass,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text("GEAR PERFORMANCE",color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)}
                Text("What earns its place",style=MaterialTheme.typography.headlineMedium)
                val bestRig=rigs.firstOrNull(); val topBait=baits.firstOrNull()
                if(bestRig!=null&&topBait!=null)Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){PerformanceTile("Best rig",bestRig,Modifier.weight(1f));PerformanceTile("Top bait",topBait,Modifier.weight(1f))}
                pbSummary?.let{Column(verticalArrangement=Arrangement.spacedBy(4.dp)){SectionLabel("Behind your PBs");Text(it,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}}
                if(rigs.isNotEmpty())Ranked("Rigs",rigs)
                if(baits.isNotEmpty())Ranked("Baits",baits)
                if(s.catches.size<3||(rigs.isEmpty()&&baits.isEmpty()))Text("Log more catches with rig and bait details to reveal your best gear.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}
    }
    if(showGearForm)GearForm(editingGear,onDismiss={showGearForm=false}){vm.saveGear(it){showGearForm=false}}
    if(showPresetForm)PresetForm(s.presets,onDismiss={showPresetForm=false}){name,kind->vm.addPreset(name,kind);showPresetForm=false}
}

@Composable private fun SectionHeader(title:String,action:String,tag:String,onClick:()->Unit)=Row(verticalAlignment=Alignment.CenterVertically){
    SectionLabel(title);Spacer(Modifier.weight(1f))
    TextButton(onClick,Modifier.testTag(tag),contentPadding=PaddingValues(horizontal=8.dp)){Icon(Icons.Default.Add,null,tint=BrassSoft,modifier=Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text(action,color=BrassSoft,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}}

@Composable private fun PerformanceTile(label:String,item:GearRules.Item,modifier:Modifier=Modifier)=Column(modifier.background(Inset,RoundedCornerShape(13.dp)).padding(12.dp).defaultMinSize(minHeight=88.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(label.uppercase(),color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.1.sp)
    Text(item.name,style=MaterialTheme.typography.titleLarge,maxLines=2)
    Text("${item.count} ${if(item.count==1)"catch" else "catches"}",color=BrassSoft,style=MaterialTheme.typography.bodySmall)}

@Composable private fun Ranked(title:String,items:List<GearRules.Item>)=Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
    SectionLabel(title)
    items.take(5).forEach{item->Column(Modifier.defaultMinSize(minHeight=36.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Row{Text(item.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f));Text("${item.count}",color=BrassSoft,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodySmall)}
        Box(Modifier.fillMaxWidth().height(4.dp).background(Inset,CircleShape)){Box(Modifier.fillMaxWidth(item.proportion).height(4.dp).background(Teal,CircleShape))}}}}

private fun gearIcon(c:GearCategory)=when(c){GearCategory.ROD->Icons.Default.Timeline;GearCategory.REEL->Icons.Default.RadioButtonUnchecked;GearCategory.LINE->Icons.Default.Gesture;GearCategory.HOOK->Icons.Default.Link;GearCategory.TERMINAL->Icons.Default.GridView;GearCategory.LURE->Icons.Default.SetMeal;GearCategory.NET->Icons.Default.Hexagon;GearCategory.CLOTHING->Icons.Default.Checkroom;GearCategory.OTHER->Icons.Default.Inventory2}

/** "Add Gear" / "Edit Gear": Gear name, Category (iOS titles) and "Notes (optional)". */
@Composable fun GearForm(item:GearItem?,onDismiss:()->Unit,onSave:(GearItem)->Unit){
    var name by rememberSaveable{mutableStateOf(item?.name.orEmpty())}
    var notes by rememberSaveable{mutableStateOf(item?.notes.orEmpty())}
    var category by rememberSaveable{mutableStateOf(item?.category?:GearCategory.ROD)}
    var expanded by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(item==null)"Add Gear" else "Edit Gear")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Gear name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("gearName"))
            Box{Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp).clickable{expanded=true},verticalAlignment=Alignment.CenterVertically){Text("Category",Modifier.weight(1f));Text(category.title,color=BrassSoft,fontWeight=FontWeight.SemiBold);Icon(Icons.Default.ArrowDropDown,null,tint=BrassSoft)}
                DropdownMenu(expanded,{expanded=false}){GearCategory.entries.forEach{c->DropdownMenuItem(text={Text(c.title)},onClick={category=c;expanded=false})}}}
            OutlinedTextField(notes,{notes=it},label={Text("Notes (optional)")},minLines=3,modifier=Modifier.fillMaxWidth())}},
        confirmButton={TextButton({onSave((item?:GearItem(name="",category=category)).copy(name=name.trim(),category=category,notes=notes.trim()))},enabled=name.isNotBlank()){Text("Save")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

/** "Add Preset": Preset name and a Rig / Bait segment; a duplicate name for the same kind cannot be added. */
@Composable fun PresetForm(presets:List<TacklePreset>,onDismiss:()->Unit,onAdd:(String,PresetKind)->Unit){
    var name by rememberSaveable{mutableStateOf("")}
    var kind by rememberSaveable{mutableStateOf(PresetKind.RIG)}
    val duplicate=presets.any{it.kind==kind&&it.name.equals(name.trim(),ignoreCase=true)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add Preset")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(name,{name=it},label={Text("Preset name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("presetName"))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){PresetKind.entries.forEachIndexed{i,k->SegmentedButton(kind==k,{kind=k},SegmentedButtonDefaults.itemShape(i,2),colors=SegmentedButtonDefaults.colors(activeContainerColor=Brass,activeContentColor=Background,inactiveContainerColor=Inset,inactiveContentColor=Ink),icon={}){Text(k.name.lowercase().replaceFirstChar(Char::uppercase))}}}}},
        confirmButton={TextButton({onAdd(name.trim(),kind)},enabled=name.isNotBlank()&&!duplicate){Text("Add")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
