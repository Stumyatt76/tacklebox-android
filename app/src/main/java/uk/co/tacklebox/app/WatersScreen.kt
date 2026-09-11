/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * The Waters tab in the iOS order: the tappable "Add your first water" state when there are none, the RIVER
 * CONDITIONS and TIDES & SEA entry cards, then a row per water with "type · region" and a chevron.
 */
@Composable fun Waters(s:AppState,vm:MainViewModel,nav:NavHostController){
    var adding by rememberSaveable{mutableStateOf(false)}
    Screen("Private places","Waters",actions={IconButton({adding=true},modifier=Modifier.testTag("addWater")){Icon(Icons.Default.Add,"Add water",tint=BrassSoft)}}){
        if(s.waters.isEmpty())item{Empty("Add your first water","Keep the places you fish, your sessions and private swim notes together.",Icons.Default.WaterDrop,onClick={adding=true})}
        item{LiveEntryCard(Icons.Default.Waves,"RIVER CONDITIONS","Nearby gauge levels & flow"){nav.navigate("rivers")}}
        item{LiveEntryCard(Icons.Default.Sailing,"TIDES & SEA","Coastal tides, waves & temperature"){nav.navigate("tides")}}
        items(s.waters){w->HeritageCard(onClick={nav.navigate("water/${w.id}")}){Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(w.name,style=MaterialTheme.typography.titleLarge,maxLines=2);Text(w.subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium,maxLines=2)}
            Icon(Icons.Default.ChevronRight,null,tint=Brass)}}}
    }
    if(adding)WaterSelectionSheet(vm,s.waters,"Add Water","Add water",creationOnly=true,onDismiss={adding=false}){}
}

/** The teal-bordered entry to a live screen: an icon in an inset circle, a brass label and the one-line promise. */
@Composable fun LiveEntryCard(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,promise:String,onClick:()->Unit){
    Box(Modifier.fillMaxWidth().border(1.dp,Teal.copy(alpha=.3f),RoundedCornerShape(18.dp))){HeritageCard(onClick=onClick){
        Row(Modifier.defaultMinSize(minHeight=52.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(44.dp).background(Inset,CircleShape),contentAlignment=Alignment.Center){Icon(icon,null,tint=Teal,modifier=Modifier.size(22.dp))}
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                if(label.isNotEmpty())Text(label,color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp)
                Text(promise,fontWeight=FontWeight.SemiBold)}
            Icon(Icons.Default.ChevronRight,null,tint=Brass)}}}
}

/**
 * The water passport: "type · region" with "Edit water", Sessions / Fish / Best here tiles (fish resolved through
 * their session as iOS does), the auto-saving "Private spot notes" card and "Delete this water".
 */
@Composable fun WaterPassport(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val w=s.waters.firstOrNull{it.id==id}
    val catches=s.catches.filter{s.resolvedWater(it)?.id==id}
    val sessions=s.sessions.count{it.item.waterId==id}
    val best=catches.mapNotNull{it.item.weightGrams}.maxOrNull()
    var notes by remember(w?.id){mutableStateOf(w?.swimNotes.orEmpty())}
    LaunchedEffect(notes){if(w!=null&&notes!=w.swimNotes){delay(400);vm.updateWater(w.copy(swimNotes=notes))}}
    var editing by rememberSaveable{mutableStateOf(false)}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    PushedScreen("",onBack={nav.popBackStack()},eyebrow="Water passport",heading=w?.name?:"Water"){
        if(w==null){item{Empty("Water not found","It may have been deleted.")};return@PushedScreen}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text(w.subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f));TextButton({editing=true},Modifier.testTag("editWater")){Text("Edit water",color=BrassSoft,fontWeight=FontWeight.SemiBold)}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("$sessions","Sessions",Modifier.weight(1f));Stat("${catches.size}","Fish",Modifier.weight(1f));Stat(best?.weight(s.settings.unitSystem)?:"—","Best here",Modifier.weight(1f))}}
        item{HeritageCard{
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Lock,null,tint=Teal);Spacer(Modifier.width(10.dp))
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)){Text("Private spot notes",fontWeight=FontWeight.SemiBold);Text("Kept in your private vault",color=Dim,style=MaterialTheme.typography.bodyMedium)}}
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(notes,{notes=it},placeholder={Text("Peg details, access notes, what worked…")},minLines=4,modifier=Modifier.fillMaxWidth().testTag("spotNotes"))}}
        item{TextButton({confirmDelete=true},Modifier.fillMaxWidth().height(44.dp).testTag("deleteWater"),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete this water",fontWeight=FontWeight.SemiBold)}}
    }
    if(editing&&w!=null)WaterFormDialog(onDismiss={editing=false},onSave={name,type,region->vm.updateWater(w.copy(name=name,type=type,region=region)){editing=false}},initial=w)
    if(confirmDelete&&w!=null)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete ${w.name}?")},
        text={Text("Only this water and its spot notes are removed. Your catches, photos and sessions are kept without this water. This cannot be undone.")},
        confirmButton={TextButton({confirmDelete=false;nav.popBackStack();vm.deleteWater(w.id)}){Text("Delete",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

/** "Edit Water": the same fields as the creation form, in a dialog. */
@Composable fun WaterFormDialog(onDismiss:()->Unit,onSave:(String,WaterType,String)->Unit,initial:Water?=null){
    var name by rememberSaveable{mutableStateOf(initial?.name.orEmpty())}; var region by rememberSaveable{mutableStateOf(initial?.region.orEmpty())}
    var type by rememberSaveable{mutableStateOf(initial?.type ?: WaterType.DAY_TICKET)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"Add Water" else "Edit Water")},
        text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())){WaterFields(name,type,region,{name=it},{type=it},{region=it},label=null)}},
        confirmButton={TextButton({if(name.isNotBlank())onSave(name.trim(),type,region.trim())},enabled=name.isNotBlank()){Text("Save")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}
