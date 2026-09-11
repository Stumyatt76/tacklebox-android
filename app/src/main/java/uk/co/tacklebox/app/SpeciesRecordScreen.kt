/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.UnitSystem
import uk.co.tacklebox.app.ui.*
import java.time.ZoneId

/** The weighed catches of a species in date order, each flagged when it beat everything before it. */
object SpeciesProgressionRules {
    data class Entry(val row:CatchRow, val isPB:Boolean)
    fun progression(catches:List<CatchRow>):List<Entry> {
        var runningBest=-1.0
        return catches.filter { it.item.weightGrams!=null }.sortedBy { it.item.caughtAt }.map { row ->
            val weight=row.item.weightGrams!!; val isPB=weight>runningBest; runningBest=maxOf(runningBest,weight); Entry(row,isPB) }
    }
    /** The running record after each catch, for the RISING RECORD step chart. */
    fun recordLine(catches:List<CatchRow>):List<Pair<Long,Double>> {
        var best=0.0
        return catches.filter { it.item.weightGrams!=null }.sortedBy { it.item.caughtAt }.map { best=maxOf(best,it.item.weightGrams!!); it.item.caughtAt.toEpochMilli() to best }
    }
}

/**
 * The species record, in the iOS order: ABOUT THIS SPECIES (only once iNaturalist has answered) → PERSONAL BEST →
 * RISING RECORD (two or more weighed catches) → PROGRESSION, newest first, with a PB pill on every catch that set
 * a new record at the time.
 */
@Composable fun SpeciesDetail(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val sp=s.species.firstOrNull{it.id==id}
    val unit=s.settings.unitSystem
    val progression=remember(s.catches,id){SpeciesProgressionRules.progression(s.catches.filter{it.species?.id==id})}
    val best=progression.filter{it.isPB}.lastOrNull()?.row
    // Fetches the reference photo and description once, the first time the record is opened, as iOS does.
    LaunchedEffect(sp?.id){sp?.let(vm::enrichSpecies)}
    fun waterName(row:CatchRow)=s.resolvedWater(row)?.name?:"Unassigned water"
    PushedScreen("",onBack={nav.popBackStack()},eyebrow="Species record",heading=sp?.name?:"Species"){
        if(sp!=null&&(sp.scientificName!=null||sp.commonName!=null||sp.about!=null||sp.referencePhotoUrl!=null)){
            item{SectionLabel("About this species")}
            item{HeritageCard{
                val photo=sp.referencePhotoUrl
                Box(Modifier.fillMaxWidth().height(if(photo.isNullOrBlank())120.dp else 180.dp).clip(RoundedCornerShape(14.dp)).background(Inset),contentAlignment=Alignment.Center){
                    if(!photo.isNullOrBlank())AsyncImage(photo,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    else FishGlyph(Teal.copy(alpha=.75f),Modifier.size(160.dp,85.dp))}
                sp.commonName?.let{Spacer(Modifier.height(12.dp));Text(it,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)}
                sp.scientificName?.let{Spacer(Modifier.height(6.dp));Text(it,color=BrassSoft,fontStyle=FontStyle.Italic,style=MaterialTheme.typography.titleMedium)}
                sp.about?.let{Spacer(Modifier.height(8.dp));Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium)}
                Spacer(Modifier.height(12.dp));Rule();Spacer(Modifier.height(8.dp))
                Text("Species info: iNaturalist",color=Dim,fontSize=10.sp)
                sp.photoAttribution?.let{Text(it,color=Dim,fontSize=10.sp)}}}}
        item{HeritageCard{
            if(best!=null){
                Text("PERSONAL BEST",color=Brass,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment=Alignment.CenterVertically){
                    if(best.item.photoUri!=null)AsyncImage(best.item.photoUri,null,Modifier.size(105.dp,82.dp).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Crop)
                    else FishGlyph(Teal.copy(alpha=.8f),Modifier.size(105.dp,58.dp))
                    Spacer(Modifier.width(16.dp))
                    Column{
                        Text(best.item.weightGrams?.weight(unit).orEmpty(),style=MaterialTheme.typography.headlineLarge)
                        Text(waterName(best),color=BrassSoft,fontWeight=FontWeight.SemiBold)
                        Text(best.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalDate().pretty(),color=Muted,style=MaterialTheme.typography.bodyMedium)}}
                best.conditions?.summary(unit)?.takeIf{it.isNotBlank()}?.let{line->
                    Spacer(Modifier.height(12.dp));Rule();Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Air,null,tint=Muted,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text(line,color=Muted,style=MaterialTheme.typography.bodyMedium)}}
            } else Text("Log a ${sp?.name?:"fish"} to start this record.",color=Muted)}}
        if(progression.size>=2)item{HeritageCard{
            Text("RISING RECORD",color=Brass,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
            Spacer(Modifier.height(12.dp))
            RisingRecordChart(SpeciesProgressionRules.recordLine(progression.map{it.row}),unit)}}
        item{SectionLabel("Progression")}
        item{HeritageCard{
            if(progression.isEmpty())Text("No weighted catches yet",color=Muted)
            else progression.reversed().forEachIndexed{index,entry->
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=52.dp).clickable{nav.navigate("catch/${entry.row.item.id}")}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                    CatchThumbnail(entry.row);Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text(entry.row.item.weightGrams?.weight(unit).orEmpty(),style=MaterialTheme.typography.titleLarge)
                        Text("${waterName(entry.row)} · ${entry.row.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalDate().pretty()}",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                    if(entry.isPB){PBPill();Spacer(Modifier.width(8.dp))}
                    Icon(Icons.Default.ChevronRight,null,tint=Dim,modifier=Modifier.size(18.dp))}
                if(index<progression.size-1)Rule()}}}
    }
}

/** The running record as a step line with a point at every catch, with a "kg" / "lb" axis label. */
@Composable fun RisingRecordChart(points:List<Pair<Long,Double>>,unit:UnitSystem){
    val values=points.map{if(unit==UnitSystem.METRIC)it.second/1000 else it.second/453.592}
    val top=(values.maxOrNull()?:1.0).coerceAtLeast(0.001)
    Text(if(unit==UnitSystem.METRIC)"kg" else "lb",color=Muted,style=MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics{contentDescription="Record weight over time; the catches are listed below."}){
        val first=points.first().first; val span=(points.last().first-first).coerceAtLeast(1)
        val pad=8f
        val xs=points.map{pad+(size.width-2*pad)*(it.first-first).toFloat()/span}
        val ys=values.map{size.height-pad-(size.height-2*pad)*(it/top).toFloat()}
        for(i in 0 until points.size-1){
            drawLine(Teal,Offset(xs[i],ys[i]),Offset(xs[i+1],ys[i]),strokeWidth=4f,cap=StrokeCap.Round)
            drawLine(Teal,Offset(xs[i+1],ys[i]),Offset(xs[i+1],ys[i+1]),strokeWidth=4f,cap=StrokeCap.Round)}
        xs.indices.forEach{drawCircle(BrassSoft,6f,Offset(xs[it],ys[it]))}
        drawLine(Muted.copy(alpha=.3f),Offset(pad,size.height-pad),Offset(size.width-pad,size.height-pad),strokeWidth=1f)
        drawLine(Muted.copy(alpha=.3f),Offset(pad,pad),Offset(pad,size.height-pad),strokeWidth=1f,cap=StrokeCap.Butt)
        drawRect(Muted.copy(alpha=0f),style=Stroke(1f))}
}
