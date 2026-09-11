/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.ui.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The water this catch belongs to: the one recorded on the fish, falling back to its session's — iOS's `resolvedWater`. */
fun AppState.resolvedWater(row:CatchRow):Water? = row.water ?: sessions.firstOrNull{it.item.id==row.item.sessionId}?.water

/** A long date and a short time, as `formatted(date: .long, time: .shortened)` prints them. */
fun java.time.Instant.longDateShortTime():String{val z=atZone(ZoneId.systemDefault());return "${z.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))} · ${z.toLocalTime().hm()}"}

/**
 * A catch, in the iOS `CatchDetailView` order: gallery hero → species + PB pill → weight → the detail card
 * (WATER, CAUGHT, LENGTH, RETURNED, RIG, BAIT) → CONDITIONS → NOTES → "Share this catch". Edit and delete live in
 * the top bar as `pencil` and `trash`.
 */
@Composable fun CatchDetail(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val c=s.catches.firstOrNull{it.item.id==id}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var sharing by remember{mutableStateOf(false)}
    val bests=remember(s.catches){CatchFilter.personalBests(s.catches)}
    val isPB=c!=null&&c.species!=null&&bests[c.species.name]?.item?.id==c.item.id
    val water=c?.let{s.resolvedWater(it)}
    PushedScreen("Catch",onBack={nav.popBackStack()},actions={
        IconButton({c?.let{nav.navigate("edit/${it.item.id}")}},Modifier.testTag("editCatch")){Icon(Icons.Default.Edit,"Edit this catch",tint=BrassSoft)}
        IconButton({confirmDelete=true},Modifier.testTag("deleteCatch")){Icon(Icons.Outlined.Delete,"Delete this catch",tint=BrassSoft)}}){
        if(c==null){item{Empty("Catch not found","It may have been deleted.")};return@PushedScreen}
        item{val gallery=c.allPhotoUris
            if(gallery.isNotEmpty())PhotoGallery(gallery)
            else Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp)).border(1.dp,Muted.copy(alpha=.15f),RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Inset,Teal.copy(alpha=.28f)))),contentAlignment=Alignment.Center){FishGlyph(Teal.copy(alpha=.82f),Modifier.size(220.dp,120.dp))}}
        item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(c.species?.name?:"Unknown species",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f,fill=false))
                if(isPB){Spacer(Modifier.width(10.dp));PBPill()}}
            Text(c.item.weightGrams?.weight(s.settings.unitSystem)?:"—",style=MaterialTheme.typography.displaySmall,color=BrassSoft)}}
        item{HeritageCard{
            DetailRow(Icons.Default.WaterDrop,"Water",water?.name?:"Unassigned water")
            Rule();DetailRow(Icons.Default.CalendarMonth,"Caught",c.item.caughtAt.longDateShortTime())
            c.item.lengthCm?.let{Rule();DetailRow(Icons.Default.Straighten,"Length",it.length(s.settings.unitSystem))}
            Rule();DetailRow(Icons.Default.SetMeal,"Returned",if(c.item.returned)"Yes — put back" else "No — kept")
            c.item.rig?.takeIf{it.isNotBlank()}?.let{Rule();DetailRow(Icons.Default.Hub,"Rig",it)}
            c.item.bait?.takeIf{it.isNotBlank()}?.let{Rule();DetailRow(Icons.Default.Grain,"Bait",it)}}}
        c.conditions?.summary(s.settings.unitSystem)?.takeIf{it.isNotBlank()}?.let{line->item{HeritageCard{
            LabelledHeading(Icons.Default.Air,"CONDITIONS",Teal);Spacer(Modifier.height(8.dp));Text(line)}}}
        if(c.item.notes.isNotBlank())item{HeritageCard{LabelledHeading(Icons.Default.Notes,"NOTES",Brass);Spacer(Modifier.height(8.dp));Text(c.item.notes)}}
        item{Button({if(!sharing){sharing=true;scope.launch{
                val uri=withContext(Dispatchers.IO){runCatching{ShareCards.write(context,ShareCards.catchCard(context,c,water?.name,s.settings.unitSystem),"catch.jpg")}.getOrNull()}
                if(uri!=null)ShareCards.share(context,uri,ShareCards.catchText(c,water?.name,s.settings.unitSystem),"Share this catch")
                else ShareSheet.catchCard(context,c,s.settings.unitSystem)
                sharing=false}}},
            Modifier.fillMaxWidth().height(52.dp).testTag("shareCatch"),shape=RoundedCornerShape(16.dp),enabled=!sharing,
            colors=ButtonDefaults.buttonColors(containerColor=BrassSoft,contentColor=Background)){
            Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Share this catch",fontWeight=FontWeight.Bold)}}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this catch?")},
        text={Text("It is removed from your vault, your records and your insights. This cannot be undone.")},
        confirmButton={TextButton({id?.let{vm.deleteCatch(it)};confirmDelete=false;nav.popBackStack()}){Text("Delete")}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

/** "45 cm" or "17.7 in" — iOS `CatchMetrics.formatLength`. */
fun Double.length(unit:UnitSystem):String=if(unit==UnitSystem.METRIC)"%.0f cm".format(this) else "%.1f in".format(this/2.54)

@Composable fun PBPill(small:Boolean=false)=Text("PB",color=Background,fontSize=if(small)9.sp else 10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,
    modifier=Modifier.background(BrassSoft,CircleShape).padding(horizontal=if(small)7.dp else 10.dp,vertical=if(small)2.dp else 6.dp))

@Composable fun Rule()=HorizontalDivider(color=Muted.copy(alpha=.16f))

/** One icon row of the detail card: a teal glyph, an upper-case label and the value on the right. */
@Composable fun DetailRow(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,value:String){
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=52.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(icon,null,tint=Teal,modifier=Modifier.size(20.dp));Spacer(Modifier.width(12.dp))
        Text(label.uppercase(),color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.1.sp)
        Spacer(Modifier.width(12.dp).weight(1f))
        Text(value,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.End,style=MaterialTheme.typography.bodyMedium)}
}

@Composable fun LabelledHeading(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String,colour:androidx.compose.ui.graphics.Color){
    Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=colour,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp))
        Text(text,color=colour,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)}
}

/** The cover photo, or the fish glyph on an inset square — iOS `CatchThumbnail`. */
@Composable fun CatchThumbnail(row:CatchRow,size:Dp=50.dp){
    Box(Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(Inset).border(1.dp,Muted.copy(alpha=.15f),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){
        if(row.item.photoUri!=null)AsyncImage(row.item.photoUri,"Photo of this catch",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else FishGlyph(Teal.copy(alpha=.82f),Modifier.fillMaxSize().padding(8.dp))}
}
