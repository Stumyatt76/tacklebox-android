/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.tacklebox.app.data.Water
import uk.co.tacklebox.app.data.WaterType
import uk.co.tacklebox.app.data.subtitle
import uk.co.tacklebox.app.ui.*

/**
 * The one water picker, ported from the iOS `WaterSelectionSheet` and used everywhere a water is chosen: the Log
 * and Edit WATER card ("Choose Water" / "Use this water"), the Waters tab's "Add Water", Sessions' "Start a
 * Session" / "Start session" and the session detail's "Choose Water" / "Assign water".
 *
 * EXISTING WATER is a radio list; "Create a new water" opens the NEW WATER form inline, so an angler at a new venue
 * is never sent to the Waters tab and back. Android used to offer chips on the capture screen, a dropdown on
 * Sessions and a dialog on Waters — three controls for one question, none of which could create a water in place.
 *
 * `preselectFirst` arrives with the first water ticked, which suits assigning a session a water — picking one is
 * the whole purpose of opening the sheet. The per-catch picker passes false: that field is optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun WaterSelectionSheet(
    vm:MainViewModel, waters:List<Water>, title:String, confirmTitle:String,
    initialWaterId:Long?=null, creationOnly:Boolean=false, preselectFirst:Boolean=true,
    onDismiss:()->Unit, onConfirm:(Long)->Unit,
){
    val sheet=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    var creatingNew by rememberSaveable{mutableStateOf(creationOnly||waters.isEmpty())}
    var selected by rememberSaveable{mutableStateOf(initialWaterId ?: if(preselectFirst)waters.firstOrNull()?.id else null)}
    var name by rememberSaveable{mutableStateOf("")}
    var type by rememberSaveable{mutableStateOf(WaterType.DAY_TICKET)}
    var region by rememberSaveable{mutableStateOf("")}
    var saving by remember{mutableStateOf(false)}
    val creating=creationOnly||creatingNew||waters.isEmpty()
    val canSave=if(creating)name.isNotBlank() else selected!=null
    fun save(){
        if(!canSave||saving)return
        if(creating){saving=true;vm.addWater(name.trim(),type,region.trim()){id->onConfirm(id);onDismiss()}}
        else selected?.let{onConfirm(it);onDismiss()}
    }
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=sheet,containerColor=Background,dragHandle=null){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=32.dp)){
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                TextButton(onDismiss){Text("Cancel",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.weight(1f));Text(title,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f))
                TextButton(::save,enabled=canSave&&!saving,modifier=Modifier.testTag("confirmWater")){Text(confirmTitle,color=if(canSave)BrassSoft else Muted,fontWeight=FontWeight.Bold)}}
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                if(!creationOnly&&waters.isNotEmpty()){
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                        SectionLabel("Existing water")
                        HeritageCard{
                            waters.forEach{water->
                                val ticked=selected==water.id&&!creatingNew
                                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp).clickable{selected=water.id;creatingNew=false}.testTag("water_${water.id}"),verticalAlignment=Alignment.CenterVertically){
                                    Column(Modifier.weight(1f)){
                                        Text(water.name,fontWeight=FontWeight.SemiBold)
                                        Text(water.subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium)}
                                    Icon(if(ticked)Icons.Default.CheckCircle else Icons.Outlined.Circle,if(ticked)"Selected" else null,tint=if(ticked)Brass else Dim)}}}}
                    val active=creatingNew
                    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=48.dp).background(if(active)Brass else Inset,RoundedCornerShape(12.dp)).clickable{creatingNew=true;selected=null}.testTag("createWater"),
                        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.AddCircle,null,tint=if(active)Background else BrassSoft,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                        Text("Create a new water",color=if(active)Background else BrassSoft,fontWeight=FontWeight.SemiBold)}}
                if(creating)WaterFields(name,type,region,{name=it},{type=it},{region=it})
            }
        }
    }
}

/** The NEW WATER form: name, type and region, shared by the selection sheet and the edit dialog. */
@Composable fun WaterFields(name:String,type:WaterType,region:String,onName:(String)->Unit,onType:(WaterType)->Unit,onRegion:(String)->Unit,label:String?="New water"){
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        label?.let{SectionLabel(it)}
        HeritageCard{
            OutlinedTextField(name,onName,placeholder={Text("Water name")},label={Text("Water name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("waterName"))
            Spacer(Modifier.height(10.dp))
            WaterTypeMenu(type,onType)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(region,onRegion,placeholder={Text("Region")},label={Text("Region")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("waterRegion"))}
        Text("Disciplines use your active app settings.",color=Dim,style=MaterialTheme.typography.bodyMedium)}
}

/** `Picker("Type")` — a menu of the ten water types under their iOS display names. */
@Composable fun WaterTypeMenu(type:WaterType,onChange:(WaterType)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box{
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp).clickable{expanded=true}.testTag("waterType"),verticalAlignment=Alignment.CenterVertically){
            Text("Type",Modifier.weight(1f))
            Text(type.title,color=BrassSoft,fontWeight=FontWeight.SemiBold)
            Icon(Icons.Default.ArrowDropDown,null,tint=BrassSoft)}
        DropdownMenu(expanded,{expanded=false}){
            WaterType.entries.forEach{t->DropdownMenuItem(text={Text(t.title)},onClick={onChange(t);expanded=false})}}}
}
