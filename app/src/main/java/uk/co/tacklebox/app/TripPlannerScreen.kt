/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.tacklebox.app.data.UnitSystem
import uk.co.tacklebox.app.services.*
import uk.co.tacklebox.app.ui.*
import java.time.*

@Composable fun TripPlannerScreen(s:AppState,nav:NavHostController) {
    val context=LocalContext.current
    var selectedWater by rememberSaveable { mutableStateOf<Long?>(null) };var selected by rememberSaveable { mutableIntStateOf(0) }
    var place by remember { mutableStateOf<Pair<Double,Double>?>(null) };var forecast by remember { mutableStateOf<TripForecast?>(null) }
    var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf<String?>(null) };var reload by remember { mutableIntStateOf(0) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){reload++}
    val zone=if(place==null)ZoneId.of("Europe/London") else ZoneId.systemDefault()
    val dates=(0..6).map { LocalDate.now(zone).plusDays(it.toLong()) };val date=dates[selected]
    val position=place ?: DeviceLocation.FALLBACK_INLAND
    val day=Astronomy.calculate(date=date,latitude=position.first,longitude=position.second,zone=zone)
    val history=s.catches.filter { selectedWater==null || s.resolvedWater(it)?.id==selectedWater }
    val imperial=s.settings.unitSystem==UnitSystem.IMPERIAL
    fun temperature(value:Double?)=value?.let { "%.0f°%s".format(if(imperial)it*9/5+32 else it,if(imperial)"F" else "C") } ?: "unavailable"
    fun wind(value:Double?)=value?.let { "%.0f %s".format(if(imperial)it/1.609344 else it,if(imperial)"mph" else "km/h") } ?: "unavailable"
    LaunchedEffect(reload) {
        busy=true;message=null
        try {
            place=DeviceLocation.current(context)
            val point=place ?: DeviceLocation.FALLBACK_INLAND
            forecast=withContext(Dispatchers.IO){TripForecastService.fetch(context,point.first,point.second,if(place==null)ZoneId.of("Europe/London") else ZoneId.systemDefault())}
        } catch(_:Exception){forecast=null;message="Could not update the weather. Bite windows and your journal history are still available."}
        finally { busy=false }
    }
    var expanded by remember { mutableStateOf(false) }
    PushedScreen("",onBack={nav.popBackStack()},eyebrow="Choose your next day",heading="Trip planner") {
        item { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Icon(if(place==null)Icons.Default.LocationOff else Icons.Default.LocationOn,null,tint=Muted,modifier=Modifier.size(14.dp));Spacer(Modifier.width(8.dp))
            Text(if(place==null)"Showing central UK estimates. Enable Location for forecasts near you." else "Forecasts near your current location. Selecting a water filters your catch history; it does not move the forecast.",color=Muted,style=MaterialTheme.typography.bodyMedium) } }
        // Android asks before the system does; iOS prompts on first use. The card goes once the system has been asked.
        if(!DeviceLocation.hasPermission(context))item { TextButton({permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)},contentPadding=PaddingValues(0.dp)) { Text("Use my location",color=BrassSoft) } }
        item { HeritageCard { Row(Modifier.defaultMinSize(minHeight=44.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Icon(Icons.Default.WaterDrop,null,tint=Brass);Spacer(Modifier.width(8.dp));SectionLabel("Catch history");Spacer(Modifier.weight(1f))
            Box { Row(Modifier.clickable{expanded=true}.testTag("catchHistoryWater"),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Text(s.waters.firstOrNull { it.id==selectedWater }?.name ?: "All waters",color=BrassSoft,fontWeight=FontWeight.SemiBold);Icon(Icons.Default.ArrowDropDown,null,tint=BrassSoft) }
                DropdownMenu(expanded,{expanded=false}) { DropdownMenuItem(text={Text("All waters")},onClick={selectedWater=null;expanded=false});s.waters.forEach { w->DropdownMenuItem(text={Text(w.name)},onClick={selectedWater=w.id;expanded=false}) } } } } } }
        item { SectionLabel("Day") }
        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(dates) { value->FilterChip(value==date,{selected=dates.indexOf(value)},{Text(value.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault())+" "+value.dayOfMonth)},colors=brassChipColours(),shape=androidx.compose.foundation.shape.CircleShape) } } }
        item { HeritageCard {
            SectionLabel("Forecast");Spacer(Modifier.height(6.dp))
            Text(date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)),style=MaterialTheme.typography.headlineMedium)
            if(busy)Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp),color=Brass,strokeWidth=2.dp);Spacer(Modifier.width(8.dp));Text("Updating forecast…",color=Muted,style=MaterialTheme.typography.bodyMedium) }
            val values=forecast?.days?.firstOrNull { it.date==date.toString() }
            if(values!=null){Text(temperature(values.low)+" to "+temperature(values.high),style=MaterialTheme.typography.titleLarge,color=BrassSoft);Text("Peak wind ${wind(values.wind)} · Rain chance ${values.rainChance?.let { "%.0f%%".format(it) } ?: "unavailable"}")}
            else if(!busy)Text("Weather forecast unavailable for this date.",color=Muted)
            forecast?.let { Text((if(it.cached)"Cached " else "Updated ")+Instant.ofEpochMilli(it.fetched).pretty(),color=Muted,style=MaterialTheme.typography.bodyMedium) }
            message?.let { Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(6.dp))
            TextButton({reload++},Modifier.fillMaxWidth().height(44.dp).background(Inset,androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),enabled=!busy){Icon(Icons.Default.Refresh,null,tint=BrassSoft,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("Refresh forecast",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
            TextButton({context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/")))},contentPadding=PaddingValues(0.dp)){Text("Forecast: Open-Meteo",color=Dim,style=MaterialTheme.typography.bodySmall)}
        } }
        item { HeritageCard {
            SectionLabel("Bite windows");Spacer(Modifier.height(6.dp))
            Text("Bite windows · ${day.rating.title}",style=MaterialTheme.typography.titleLarge);Text(day.moonPhase,color=Muted,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
            day.windows.forEach { Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Icon(if(it.major)Icons.Default.DarkMode else Icons.Default.WbTwilight,null,tint=if(it.major)BrassSoft else Teal,modifier=Modifier.size(16.dp));Spacer(Modifier.width(8.dp));Text("${if(it.major)"Major" else "Minor"} · ${it.start.hm()}–${it.end.hm()}",style=MaterialTheme.typography.bodyMedium) } }
            Text("Astronomical estimates, not a catch-success forecast.",color=Muted,style=MaterialTheme.typography.bodyMedium)
        } }
        item { HeritageCard { SectionLabel("Your history");Spacer(Modifier.height(6.dp));Text("Your history in this month",style=MaterialTheme.typography.titleLarge);Text("${TripHistory.matchingMonth(history.map { it.item.caughtAt },date,zone)} recorded catches across all years.",style=MaterialTheme.typography.bodyMedium);Text("This describes your journal; it does not account for trips without catches or fishing effort.",color=Muted,style=MaterialTheme.typography.bodyMedium) } }
        item { LiveEntryCard(Icons.Default.Waves,"","Check river conditions"){nav.navigate("rivers")} }
        item { LiveEntryCard(Icons.Default.Sailing,"","Check tides and sea state"){nav.navigate("tides")} }
        item { Text("River readings and the available tide dates are shown in those views. A current reading is not a prediction for your selected trip date.",color=Dim,style=MaterialTheme.typography.bodyMedium) }
    }
}
