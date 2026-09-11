/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.services.*
import uk.co.tacklebox.app.ui.*
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Formatting shared by the live screens, matching the iOS `formatted(...)` calls. */
object LiveFormat {
    /** "3.2 km" under ten, "14 km" above. */
    fun distance(km:Double):String = if (km<10) "%.1f km".format(Locale.getDefault(), km) else "${Math.round(km)} km"
    /** Up to `fractionDigits` decimals, trailing zeros dropped — `.number.precision(.fractionLength(0...2))`. */
    fun number(value:Double, fractionDigits:Int=2):String = java.text.DecimalFormat("0" + if (fractionDigits>0) "." + "#".repeat(fractionDigits) else "").format(value)
    /** "just now", "5 minutes ago", "2 hours ago", "3 days ago" — `.relative(presentation: .named)`, near enough. */
    fun relativeAgo(at:Instant, now:Instant=Instant.now()):String {
        val minutes=Duration.between(at,now).toMinutes()
        return when {
            minutes<1 -> "just now"
            minutes<60 -> "$minutes ${if(minutes==1L)"minute" else "minutes"} ago"
            minutes<1440 -> "${minutes/60} ${if(minutes/60==1L)"hour" else "hours"} ago"
            else -> "${minutes/1440} ${if(minutes/1440==1L)"day" else "days"} ago"
        }
    }
    fun measurement(m:RiverMeasurement?):String? = m?.let { "${number(it.value)} ${it.unit}".trim() }
    /** The one-line reading under a nearby gauge: "Level 0.45 m" / "Flow 12.3 m3/s" / "Latest reading unavailable". */
    fun gaugeSummary(g:RiverGauge):String = g.latestLevel?.let { "Level ${number(it.value)} ${it.unit}".trim() }
        ?: g.latestFlow?.let { "Flow ${number(it.value,1)} ${it.unit}".trim() } ?: "Latest reading unavailable"
    fun trendTitle(t:RiverTrend)=when(t){RiverTrend.RISING->"Rising";RiverTrend.FALLING->"Falling";RiverTrend.STEADY->"Steady";RiverTrend.UNKNOWN->"Trend unavailable"}
}

/** The location note every live screen carries. */
@Composable fun LocationNote(located:Boolean){
    Row(verticalAlignment=Alignment.CenterVertically){
        Icon(if(located)Icons.Default.LocationOn else Icons.Default.LocationOff,null,tint=Dim,modifier=Modifier.size(14.dp));Spacer(Modifier.width(8.dp))
        Text(if(located)"Approximate location · precise coordinates aren't stored" else "Location unavailable · showing central UK",color=Dim,style=MaterialTheme.typography.bodyMedium)}
}

@Composable fun CachedBanner()=Row(Modifier.fillMaxWidth().background(Inset,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Default.Refresh,null,tint=BrassSoft,modifier=Modifier.size(14.dp));Spacer(Modifier.width(8.dp))
    Text("Showing the last update · live readings didn't refresh",color=BrassSoft,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}

/** A brass "Try again" — the retry every live-screen message card ends with. */
@Composable fun TryAgainButton(onClick:()->Unit)=Button(onClick,Modifier.defaultMinSize(minWidth=130.dp,minHeight=44.dp).testTag("tryAgain"),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Refresh,null,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("Try again",fontWeight=FontWeight.Bold)}

/**
 * River conditions, as iOS lays it out: the location note, "Finding nearby river gauges…", the NEAREST GAUGE card
 * (LEVEL / FLOW tiles, trend, distance, "updated N ago", "Data: …", refresh), NEARBY GAUGES (each tappable to
 * show its RECENT HISTORY chart from the readings already fetched), the message cards, and the attribution footer.
 */
@Composable fun Rivers(vm:MainViewModel,nav:NavHostController){
    val live by vm.river.collectAsStateWithLifecycle()
    val located by vm.riverLocated.collectAsStateWithLifecycle()
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){vm.river()}
    PushedScreen("",onBack={nav.popBackStack()},eyebrow="Live water",heading="River conditions"){
        item{LocationNote(located)}
        when(val x=live){
            LiveState.Idle,LiveState.Loading->item{HeritageCard{Row(Modifier.defaultMinSize(minHeight=72.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(20.dp),color=Brass,strokeWidth=2.dp);Spacer(Modifier.width(12.dp));Text("Finding nearby river gauges…",color=Muted)}}}
            is LiveState.Error->item{RiverMessageCard(x.message){vm.river(refresh=true)}}
            is LiveState.Data->{
                val r=x.value
                val nearest=r.gauges.firstOrNull()
                if(nearest==null){item{RiverMessageCard(RiverError.NoGauges.message.orEmpty()){vm.river(refresh=true)}}}
                else{
                    if(r.cached)item{CachedBanner()}
                    item{Box(Modifier.fillMaxWidth().border(1.dp,Teal.copy(alpha=.4f),RoundedCornerShape(18.dp))){HeritageCard{
                        Row(verticalAlignment=Alignment.Top){
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                SectionLabel("Nearest gauge")
                                Text(nearest.river?:nearest.name,style=MaterialTheme.typography.headlineMedium)
                                if(nearest.river!=null)Text(nearest.name,color=Muted,style=MaterialTheme.typography.bodyMedium)}
                            IconButton({vm.river(refresh=true)},Modifier.testTag("refreshRivers")){Icon(Icons.Default.Refresh,"Refresh river conditions",tint=BrassSoft)}}
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){MeasurementTile(nearest.latestLevel,"Level",Modifier.weight(1f));MeasurementTile(nearest.latestFlow,"Flow",Modifier.weight(1f))}
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Icon(when(nearest.trend){RiverTrend.RISING->Icons.Default.NorthEast;RiverTrend.FALLING->Icons.Default.SouthEast;RiverTrend.STEADY->Icons.AutoMirrored.Filled.ArrowForward;RiverTrend.UNKNOWN->Icons.Default.Remove},null,tint=if(nearest.trend==RiverTrend.UNKNOWN)Dim else Teal,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp))
                            Text(listOfNotNull(LiveFormat.trendTitle(nearest.trend),"· ${LiveFormat.distance(nearest.distanceKm)} away",nearest.updatedAt?.let{"· updated ${LiveFormat.relativeAgo(it)}"}).joinToString(" "),color=Muted,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}
                        Spacer(Modifier.height(8.dp))
                        Text("Data: ${nearest.sourceLabel}",color=Dim,style=MaterialTheme.typography.bodySmall)}}}
                    item{Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Verified,null,tint=Brass,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text("NEARBY GAUGES",color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)}}
                    if(r.gauges.size>1)items(r.gauges.drop(1).size){i->val g=r.gauges[i+1]
                        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=48.dp).background(Surface,RoundedCornerShape(14.dp)).clickable{selected=g.id}.padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(g.river?:g.name,fontWeight=FontWeight.SemiBold);Text("${LiveFormat.distance(g.distanceKm)} · ${LiveFormat.gaugeSummary(g)}",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                            Icon(Icons.Default.ShowChart,null,tint=BrassSoft)}}
                    else item{Text("No other reporting gauges were found nearby.",color=Muted)}
                    val shown=r.gauges.firstOrNull{it.id==selected}?:nearest
                    item{HeritageCard{
                        SectionLabel("Recent history");Spacer(Modifier.height(6.dp))
                        Text(shown.river?:shown.name,style=MaterialTheme.typography.titleLarge)
                        if(shown.history.size>1){Spacer(Modifier.height(10.dp));ReadingsChart(shown.history.map{it.value});Spacer(Modifier.height(6.dp))
                            Text(shown.historyUnit?.let{"Reported in $it"}?:"Source-native units",color=Dim,style=MaterialTheme.typography.bodySmall)}
                        else Text("Recent history isn't available from this gauge right now.",color=Muted,modifier=Modifier.defaultMinSize(minHeight=80.dp))}}
                }
            }
        }
        item{Text("Live observations are supplied by the Environment Agency or U.S. Geological Survey where available. Gauge readings are informational and may be delayed.",color=Dim,style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun RiverMessageCard(message:String,retry:()->Unit){
    val offline=message==RiverError.Offline.message
    HeritageCard{
        Icon(when(message){RiverError.UnavailableArea.message->Icons.Default.Map;RiverError.NoGauges.message->Icons.Default.Waves;else->Icons.Default.WifiOff},null,tint=Brass,modifier=Modifier.size(25.dp))
        Spacer(Modifier.height(12.dp));Text(message,style=MaterialTheme.typography.titleLarge)
        if(offline){Spacer(Modifier.height(8.dp));Text("Nothing is lost — try again when you're back online.",color=Muted)}
        Spacer(Modifier.height(12.dp));TryAgainButton(retry)}
}

/** LEVEL / FLOW: the reading in serif or "Not reported". */
@Composable fun MeasurementTile(m:RiverMeasurement?,label:String,modifier:Modifier=Modifier){
    Column(modifier.background(Inset,RoundedCornerShape(13.dp)).padding(12.dp).defaultMinSize(minHeight=66.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(label.uppercase(),color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)
        val value=LiveFormat.measurement(m)
        if(value!=null)Text(value,style=MaterialTheme.typography.titleLarge,maxLines=1) else Text("Not reported",color=Dim,style=MaterialTheme.typography.bodyMedium)}
}

/** A teal line with a soft area under it — the RECENT HISTORY and 24-HOUR WAVE FORECAST charts. */
@Composable fun ReadingsChart(values:List<Double>,height:androidx.compose.ui.unit.Dp=150.dp,description:String="Readings over time"){
    val min=values.min();val max=values.max();val range=(max-min).takeIf{it>0}?:1.0
    Canvas(Modifier.fillMaxWidth().height(height).semantics{contentDescription=description}){
        val pad=8f
        val points=values.mapIndexed{i,v->Offset(pad+(size.width-2*pad)*i/(values.size-1).coerceAtLeast(1),size.height-pad-(size.height-2*pad)*((v-min)/range).toFloat())}
        val line=Path().apply{moveTo(points.first().x,points.first().y);points.drop(1).forEach{lineTo(it.x,it.y)}}
        val area=Path().apply{addPath(line);lineTo(points.last().x,size.height-pad);lineTo(points.first().x,size.height-pad);close()}
        drawPath(area,Brush.verticalGradient(listOf(Teal.copy(alpha=.3f),Teal.copy(alpha=0f))))
        drawPath(line,Teal,style=Stroke(width=4f))
        drawLine(Muted.copy(alpha=.3f),Offset(pad,pad),Offset(pad,size.height-pad),1f)}
}
