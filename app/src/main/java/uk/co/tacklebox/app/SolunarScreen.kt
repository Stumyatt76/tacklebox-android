/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.services.*
import uk.co.tacklebox.app.ui.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** The MATCHED TO YOUR CATCHES lines, as the iOS `personalInsight` builds them. */
object PersonalInsight {
    fun bandLabel(band:Int):String = "${LocalTime.of(band*4,0).hm()}–${LocalTime.of((band*4+4)%24,0).hm()}"
    fun contextLine(band:Int, sunrise:LocalTime, sunset:LocalTime):String = when(band) {
        1 -> "Your catches cluster around dawn; the selected day's sunrise is ${sunrise.hm()}."
        4,5 -> "Your catches lean toward dusk and night; the selected day's sunset is ${sunset.hm()}."
        else -> "Compare that pattern with the selected day's highlighted feeding periods."
    }
}

/**
 * Bite windows in the iOS order: the day controls, the rating card, FEEDING WINDOWS, SUN & MOON (with solar noon),
 * MATCHED TO YOUR CATCHES and the footer. The in-app location card stays until the system has been asked once.
 */
@Composable fun Solunar(vm:MainViewModel,nav:NavHostController){
    val state by vm.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var place by remember{mutableStateOf<Pair<Double,Double>?>(null)}
    var reload by remember{mutableIntStateOf(0)}
    var canAsk by remember{mutableStateOf(!DeviceLocation.hasPermission(context))}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){canAsk=false;reload++}
    LaunchedEffect(reload){place=vm.solunarPlace()}
    val located=place!=null&&place!=DeviceLocation.FALLBACK_INLAND
    val zone=if(located)ZoneId.systemDefault() else ZoneId.of("Europe/London")
    var dayOffset by rememberSaveable{mutableIntStateOf(0)}
    val selectedDay=LocalDate.now(zone).plusDays(dayOffset.toLong())
    val d=place?.let{Astronomy.calculate(date=selectedDay,latitude=it.first,longitude=it.second,zone=zone)}?:Astronomy.calculate(date=selectedDay,zone=zone)
    fun clock(t:LocalTime?)=t?.hm()?:"No event"
    PushedScreen("Bite windows",onBack={nav.popBackStack()}){
        item{Row(verticalAlignment=Alignment.CenterVertically){
            IconButton({dayOffset--},Modifier.testTag("previousDay")){Icon(Icons.Default.ChevronLeft,"Previous day",tint=BrassSoft)}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)){
                Text(if(dayOffset==0)"TODAY" else selectedDay.dayOfWeek.getDisplayName(TextStyle.FULL,Locale.getDefault()).uppercase(),color=Brass,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                Text(selectedDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center)}
            IconButton({dayOffset++},Modifier.testTag("nextDay")){Icon(Icons.Default.ChevronRight,"Next day",tint=BrassSoft)}}}
        item{HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(62.dp).background(Brass.copy(alpha=.14f),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.NightsStay,null,tint=BrassSoft,modifier=Modifier.size(27.dp))}
                Spacer(Modifier.width(15.dp))
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text("${d.rating.title} day",style=MaterialTheme.typography.headlineMedium);Text(d.moonPhase,color=Muted,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}}
            if(!located){Spacer(Modifier.height(10.dp));Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocationOff,null,tint=BrassSoft,modifier=Modifier.size(14.dp));Spacer(Modifier.width(8.dp));Text("Using an approximate UK location. Enable Location for local times.",color=BrassSoft,style=MaterialTheme.typography.bodyMedium)}}}}
        if(!located&&canAsk)item{HeritageCard(onClick={request.launch(Manifest.permission.ACCESS_COARSE_LOCATION)}){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocationOn,null,tint=Brass);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Use my location for local times",fontWeight=FontWeight.SemiBold);Text("Approximate only — your precise spot is never stored.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Feeding windows")
            HeritageCard{d.windows.forEachIndexed{i,w->
                Row(Modifier.defaultMinSize(minHeight=58.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(if(w.major)Icons.Default.DarkMode else Icons.Default.WbTwilight,null,tint=if(w.major)BrassSoft else Teal,modifier=Modifier.width(26.dp));Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(if(w.major)"Major period" else "Minor period",fontWeight=FontWeight.SemiBold);Text("${w.start.hm()} – ${w.end.hm()}",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                    Text(if(w.major)"2 HR" else "1 HR",color=Dim,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)}
                if(i<d.windows.size-1)Rule()}}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Sun & moon")
            HeritageCard{
                SkyRow(Icons.Default.WbTwilight,"Sunrise",clock(d.sunrise));Rule()
                SkyRow(Icons.Default.WbSunny,"Solar noon",clock(d.solarNoon));Rule()
                SkyRow(Icons.Default.WbTwilight,"Sunset",clock(d.sunset));Rule()
                SkyRow(Icons.Default.NightsStay,"Moonrise",clock(d.moonrise));Rule()
                SkyRow(Icons.Default.Bedtime,"Moonset",clock(d.moonset))}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Matched to your catches")
            HeritageCard{MatchedToCatches(state.catches,place?.first?:DeviceLocation.FALLBACK_INLAND.first,place?.second?:DeviceLocation.FALLBACK_INLAND.second,zone,d)}}}
        item{Text("Solunar times are planning estimates from local astronomical calculations. Weather, water and fish behaviour still matter.",color=Dim,style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun SkyRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,value:String)=Row(Modifier.defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(icon,null,tint=BrassSoft,modifier=Modifier.width(28.dp));Text(title,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(value,color=Muted)}

@Composable private fun MatchedToCatches(catches:List<CatchRow>,lat:Double,lon:Double,zone:ZoneId,day:SolunarDay){
    if(catches.size<5){val left=5-catches.size
        Text("Log $left more ${if(left==1)"catch" else "catches"} to personalise your bite windows.",style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp));Text("This summarises recorded catches; it does not measure fishing effort.",color=Muted,style=MaterialTheme.typography.bodyMedium)
    } else {
        val summary=remember(catches,lat,lon){PersonalWindows.summary(catches.map{it.item.caughtAt},lat,lon,zone)}
        Text("${summary.percent}%",style=MaterialTheme.typography.displaySmall,color=BrassSoft)
        Text("of your recorded catches fall within estimated major or minor windows",fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp));Rule();Spacer(Modifier.height(10.dp))
        Text("Most recorded catches: ${PersonalInsight.bandLabel(summary.hourBand)}.",color=Teal,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp));Text(PersonalInsight.contextLine(summary.hourBand,day.sunrise,day.sunset),color=Muted,style=MaterialTheme.typography.bodyMedium)
        Text("Estimated using your current planning location, which may differ from past fishing spots. Catch counts do not measure fishing effort.",color=Muted,style=MaterialTheme.typography.bodySmall)}
}
