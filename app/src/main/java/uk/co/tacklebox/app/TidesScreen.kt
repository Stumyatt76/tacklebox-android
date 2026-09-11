/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.data.UnitSystem
import uk.co.tacklebox.app.services.*
import uk.co.tacklebox.app.ui.*
import java.time.LocalDateTime
import java.time.ZoneId

/** A height in the angler's units: "1.2 m" or "3.9 ft". */
fun Double.metres(unit:UnitSystem):String=if(unit==UnitSystem.METRIC)"%.1f m".format(this) else "%.1f ft".format(this/0.3048)
/** A temperature in the angler's units: "14.2°C" or "57.6°F" — the iOS sea-temperature tile. */
fun Double.celsius(unit:UnitSystem):String=if(unit==UnitSystem.METRIC)"%.1f°C".format(this) else "%.1f°F".format(this*9/5+32)

/** What the sea is doing now, taken from the hourly series at the current hour — iOS's `MarineConditions` current values. */
data class SeaNow(val waveHeight:Double?,val waveDirection:Double?,val wavePeriod:Double?,val seaTemperature:Double?)
object SeaState {
    fun now(h:MarineHourly, at:LocalDateTime=LocalDateTime.now()):SeaNow {
        val i=MarineHours.firstFromNow(h.time,at)
        fun <T> List<T?>.at()=getOrNull(i) ?: firstNotNullOfOrNull { it }
        return SeaNow(h.waveHeight.at(),h.waveDirection.at(),h.wavePeriod.at(),h.seaTemperature.at())
    }
    /** Inland or outside coverage: nothing in the series at all. */
    fun isCoastal(h:MarineHourly?)=h!=null&&h.waveHeight.any{it!=null}
    /** The next 24 hours of wave heights from now, paired with their local times. */
    fun next24(h:MarineHourly, at:LocalDateTime=LocalDateTime.now()):List<Pair<String,Double>> {
        val from=MarineHours.firstFromNow(h.time,at)
        return (from until minOf(from+25,h.time.size)).mapNotNull { i -> h.waveHeight.getOrNull(i)?.let { h.time[i] to it } }
    }
}

/**
 * Tides & sea in the iOS order: the location note; "Reading the sea…"; "You're not near the coast" or "Sea
 * conditions are unavailable"; otherwise the cached banner, the SEA STATE card (WAVES / DIRECTION / SEA TEMP tiles,
 * wave period, NEXT TIDE, attribution, refresh) and TODAY'S DETAIL (TODAY'S TIDES table, 24-HOUR WAVE FORECAST).
 */
@Composable fun Tides(vm:MainViewModel,nav:NavHostController){
    val unit=vm.state.collectAsStateWithLifecycle().value.settings.unitSystem
    val sea by vm.marine.collectAsStateWithLifecycle()
    val tide by vm.tides.collectAsStateWithLifecycle()
    val located by vm.marineLocated.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){vm.marine();vm.tides()}
    fun refresh(){vm.marine(refresh=true);vm.tides(refresh=true)}
    PushedScreen("",onBack={nav.popBackStack()},eyebrow="Coastal outlook",heading="Tides & sea"){
        item{LocationNote(located)}
        when(val x=sea){
            LiveState.Idle,LiveState.Loading->item{HeritageCard{Row(Modifier.defaultMinSize(minHeight=72.dp),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(20.dp),color=Brass,strokeWidth=2.dp);Spacer(Modifier.width(12.dp));Text("Reading the sea…",color=Muted)}}}
            is LiveState.Error->item{HeritageCard{Icon(Icons.Default.WifiOff,null,tint=Brass,modifier=Modifier.size(25.dp));Spacer(Modifier.height(12.dp))
                Text("Sea conditions are unavailable",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(8.dp))
                Text("Try again when you're back online. Cached conditions appear automatically when available.",color=Muted);Spacer(Modifier.height(12.dp));TryAgainButton(::refresh)}}
            is LiveState.Data->{val h=x.value.response.hourly
                if(!SeaState.isCoastal(h))item{Box(Modifier.fillMaxWidth().border(1.dp,Teal.copy(alpha=.3f),RoundedCornerShape(18.dp))){HeritageCard{Icon(Icons.Default.Waves,null,tint=Teal,modifier=Modifier.size(27.dp));Spacer(Modifier.height(12.dp))
                    Text("You're not near the coast",style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(8.dp))
                    Text("Sea conditions show when you're by the water.",color=Muted);Spacer(Modifier.height(12.dp));TryAgainButton(::refresh)}}}
                else{
                    val now=SeaState.now(h!!)
                    if(x.value.cached)item{CachedBanner()}
                    item{Box(Modifier.fillMaxWidth().border(1.dp,Teal.copy(alpha=.4f),RoundedCornerShape(18.dp))){HeritageCard{
                        Row(verticalAlignment=Alignment.Top){
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){SectionLabel("Sea state");Text("Current conditions",style=MaterialTheme.typography.headlineMedium)}
                            IconButton(::refresh,Modifier.testTag("refreshTides")){Icon(Icons.Default.Refresh,"Refresh tides and sea conditions",tint=BrassSoft)}}
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                            MetricTile(now.waveHeight?.metres(unit)?:"—","Waves",Modifier.weight(1f))
                            MetricTile(now.waveDirection?.let(::compassPoint)?:"—","Direction",Modifier.weight(1f))
                            MetricTile(now.seaTemperature?.celsius(unit)?:"—","Sea temp",Modifier.weight(1f))}
                        now.wavePeriod?.let{Spacer(Modifier.height(12.dp));Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Timer,null,tint=Muted,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text("Wave period ${LiveFormat.number(it,1)} seconds",color=Muted,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}}
                        Spacer(Modifier.height(16.dp))
                        TideSummary(tide,unit)
                        Spacer(Modifier.height(12.dp))
                        Text("Marine: Open-Meteo"+((tide as? LiveState.Data)?.value?.source?.label?.let{" · Tides: $it"}.orEmpty()),color=Dim,style=MaterialTheme.typography.bodySmall)}}}
                    item{Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Verified,null,tint=Brass,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text("TODAY'S DETAIL",color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.2.sp)}}
                    (tide as? LiveState.Data)?.value?.let{r->item{HeritageCard{SectionLabel("Today's tides");Spacer(Modifier.height(6.dp))
                        r.events.forEachIndexed{i,e->Row(Modifier.defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(if(e.kind==TideKind.HIGH)Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,null,tint=Teal,modifier=Modifier.size(18.dp));Spacer(Modifier.width(10.dp))
                            Text(if(e.kind==TideKind.HIGH)"High" else "Low",fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                            Text(e.time.atZone(ZoneId.systemDefault()).toLocalTime().hm(),color=Muted,style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.width(12.dp))
                            Text(e.heightMetres.metres(unit),color=BrassSoft,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}
                            if(i<r.events.size-1)Rule()}}}}
                    item{HeritageCard{SectionLabel("24-hour wave forecast");Spacer(Modifier.height(10.dp))
                        val hours=SeaState.next24(h)
                        if(hours.size>1){Text(if(unit==UnitSystem.METRIC)"m" else "ft",color=Muted,style=MaterialTheme.typography.bodySmall)
                            ReadingsChart(hours.map{if(unit==UnitSystem.METRIC)it.second else it.second*3.28084},170.dp,"Wave height over the next 24 hours")}
                        else Text("A marine forecast isn't available here right now.",color=Muted,modifier=Modifier.defaultMinSize(minHeight=80.dp))}}
                }
            }
        }
    }
}

@Composable private fun MetricTile(value:String,label:String,modifier:Modifier=Modifier){
    Column(modifier.background(Inset,RoundedCornerShape(12.dp)).padding(10.dp).defaultMinSize(minHeight=62.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text(label.uppercase(),color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
        Text(value,style=MaterialTheme.typography.titleLarge,maxLines=1)}
}

/** NEXT TIDE inside the sea-state card, in each of iOS's four states. */
@Composable private fun TideSummary(tide:LiveState<TideResult>,unit:UnitSystem){
    when(tide){
        LiveState.Idle,LiveState.Loading->Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(14.dp),color=Brass,strokeWidth=2.dp);Spacer(Modifier.width(8.dp));Text("Finding today's tides…",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        is LiveState.Data->{val next=tide.value.next()
            if(next!=null)Row(verticalAlignment=Alignment.CenterVertically){
                Icon(if(next.kind==TideKind.HIGH)Icons.Default.ArrowCircleUp else Icons.Default.ArrowCircleDown,null,tint=Teal);Spacer(Modifier.width(10.dp))
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)){SectionLabel("Next tide")
                    Text("${if(next.kind==TideKind.HIGH)"High" else "Low"} at ${next.time.atZone(ZoneId.systemDefault()).toLocalTime().hm()} · ${next.heightMetres.metres(unit)}",fontWeight=FontWeight.SemiBold)}}
            else Text("Today's remaining tides have passed.",color=Muted,style=MaterialTheme.typography.bodyMedium)
            if(tide.value.cached)Text("Tide table is from the last successful update.",color=BrassSoft,style=MaterialTheme.typography.bodySmall)}
        is LiveState.Error->if(tide.message==TideError.NotAvailableHere.message)Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text("Tide predictions aren't available for your area.",color=Muted,style=MaterialTheme.typography.bodyMedium)
                Text("A WorldTides key in Settings enables global tides.",color=Muted,style=MaterialTheme.typography.bodyMedium)}
            else Text("Tide predictions couldn't be updated, but sea conditions are still available.",color=Muted,style=MaterialTheme.typography.bodyMedium)
    }
}
