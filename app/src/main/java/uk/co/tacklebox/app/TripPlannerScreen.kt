package uk.co.tacklebox.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
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
    val history=s.catches.filter { selectedWater==null || it.item.waterId==selectedWater }
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
    Screen("Choose your next day","Trip planner") {
        item { Text(if(place==null)"Showing central UK estimates. Enable Location for forecasts near you." else "Forecasts near your current location. Selecting a water filters your catch history; it does not move the forecast.",color=Muted) }
        if(!DeviceLocation.hasPermission(context))item { TextButton({permission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)}) { Text("Use my location") } }
        item { WaterChoice(s.waters,selectedWater){selectedWater=it} }
        item { LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(dates) { value->FilterChip(value==date,{selected=dates.indexOf(value)},{Text(value.dayOfWeek.name.take(3)+" "+value.dayOfMonth)},colors=brassChipColours()) } } }
        item { HeritageCard {
            Text(date.toString(),style=MaterialTheme.typography.headlineSmall)
            if(busy)CircularProgressIndicator()
            val values=forecast?.days?.firstOrNull { it.date==date.toString() }
            if(values!=null){Text(temperature(values.low)+" to "+temperature(values.high));Text("Peak wind ${wind(values.wind)} · Rain chance ${values.rainChance?.let { "%.0f%%".format(it) } ?: "unavailable"}")}
            else if(!busy)Text("Weather forecast unavailable for this date.",color=Muted)
            forecast?.let { Text((if(it.cached)"Cached " else "Updated ")+Instant.ofEpochMilli(it.fetched).pretty(),color=Muted) }
            message?.let { Text(it,color=Muted) }
            TextButton({reload++},enabled=!busy){Text("Refresh forecast")}
            TextButton({context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/")))}){Text("Forecast: Open-Meteo")}
        } }
        item { HeritageCard {
            Text("Bite windows · ${day.rating.title}",style=MaterialTheme.typography.titleLarge);Text(day.moonPhase,color=Muted)
            day.windows.forEach { Text("${if(it.major)"Major" else "Minor"} · ${it.start}–${it.end}") }
            Text("Astronomical estimates, not a catch-success forecast.",color=Muted)
        } }
        item { HeritageCard { Text("Your history in this month",style=MaterialTheme.typography.titleLarge);Text("${TripHistory.matchingMonth(history.map { it.item.caughtAt },date,zone)} recorded catches across all years.");Text("This describes your journal; it does not account for trips without catches or fishing effort.",color=Muted) } }
        item { TextButton({nav.navigate("rivers")}){Text("Check river conditions")};TextButton({nav.navigate("tides")}){Text("Check tides and sea state")};Text("River readings and the available tide dates are shown in those views. A current reading is not a prediction for your selected trip date.",color=Muted) }
    }
}
