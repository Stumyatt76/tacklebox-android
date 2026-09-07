/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.services.*
import uk.co.tacklebox.app.ui.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{TackleboxTheme{TackleboxRoot()}}}}
data class Tab(val route:String,val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
val tabs=listOf(Tab("vault","Vault",Icons.Default.Home),Tab("waters","Waters",Icons.Default.Water),Tab("sessions","Sessions",Icons.Default.Schedule),Tab("insights","Insights",Icons.Default.BarChart))

@Composable fun TackleboxRoot(vm:MainViewModel=viewModel()){
    val state by vm.state.collectAsStateWithLifecycle(); val nav=rememberNavController()
    val back by nav.currentBackStackEntryAsState(); val showFab=back?.destination?.route in setOf("vault","waters","sessions","insights")
    if(!state.settings.onboardingComplete){Onboarding(vm)} else Scaffold(containerColor=Background,bottomBar={BottomBar(nav,showFab)}){pad ->
        NavHost(nav,"vault",Modifier.padding(pad)){
            composable("vault"){Vault(state,nav)}; composable("waters"){Waters(state,vm,nav)}; composable("sessions"){Sessions(state,vm)}; composable("insights"){Insights(state,nav)}; composable("log"){LogCatch(state,vm,nav)}
            composable("catches"){Catches(state,nav)}; composable("tackle"){Tacklebox(state,vm)}; composable("solunar"){Solunar(vm)}; composable("tides"){Tides(vm)}; composable("rivers"){Rivers(vm)}; composable("settings"){Settings(state,vm)}; composable("year"){YearOnWater(state)}
            composable("water/{id}"){WaterPassport(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("species/{id}"){SpeciesDetail(state,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("catch/{id}"){CatchDetail(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}
        }
    }
}
// The log button is docked into the bar rather than floated over the content: a centre-docked FAB sat on top of the
// Vault's chip row and the Log screen's Save button (TB-A-04, TB-A-12). saveState/restoreState keep each tab's
// scroll position and back stack across tab switches.
@Composable fun BottomBar(nav:NavHostController,showFab:Boolean){val back by nav.currentBackStackEntryAsState();Box{NavigationBar(containerColor=Surface){tabs.forEachIndexed{i,t->if(i==2)Spacer(Modifier.weight(.65f));NavigationBarItem(selected=back?.destination?.route==t.route,onClick={nav.navigate(t.route){popUpTo("vault"){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(t.icon,null)},label={Text(t.label)},modifier=Modifier.testTag("tab_${t.route}"),colors=NavigationBarItemDefaults.colors(selectedIconColor=Brass,selectedTextColor=Brass,indicatorColor=Inset))}};if(showFab)FloatingActionButton(onClick={nav.navigate("log")},containerColor=Brass,contentColor=Background,shape=CircleShape,modifier=Modifier.align(Alignment.Center).testTag("logCatchFab")){Icon(Icons.Default.Add,"Log a catch")}}}

// safeDrawingPadding keeps the wordmark clear of the status bar; without it the header collided with the clock (TB-A-01).
@Composable fun Onboarding(vm:MainViewModel){var page by rememberSaveable{mutableIntStateOf(0)};val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){};BackHandler(enabled=page>0){page-=1};Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),verticalArrangement=Arrangement.SpaceBetween){Column{Text("TACKLEBOX",color=Brass,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(48.dp))
    Text(when(page){0->"Your water.\nYour story.";1->"Make it yours.";else->"Private by design."},style=MaterialTheme.typography.displaySmall);Spacer(Modifier.height(18.dp))
    Text(when(page){0->"A calm, offline home for catches, waters and days on the bank.";1->"Choose how weights and lengths appear. You can change this any time in Settings.";else->"Your precise fishing spots are never stored. Location is used only when you ask for live conditions."},color=Muted,style=MaterialTheme.typography.bodyLarge)
    // iOS asks for units during onboarding; Android did not, so the locale guess was never confirmed (TB-A-14).
    if(page==1){Spacer(Modifier.height(24.dp));val settings by vm.state.collectAsStateWithLifecycle()
        SingleChoiceSegmentedButtonRow{UnitSystem.entries.forEachIndexed{i,u->SegmentedButton(settings.settings.unitSystem==u,{vm.settings(settings.settings.copy(unitSystem=u))},SegmentedButtonDefaults.itemShape(i,2),colors=SegmentedButtonDefaults.colors(activeContainerColor=Brass,activeContentColor=Background,inactiveContainerColor=Inset,inactiveContentColor=Ink)){Text(u.name.lowercase().replaceFirstChar(Char::uppercase))}}}}}
Column{when(page){
    0->Button(onClick={page=1},modifier=Modifier.fillMaxWidth()){Text("Continue")}
    1->Button(onClick={page=2},modifier=Modifier.fillMaxWidth()){Text("Continue")}
    else->{Button(onClick={request.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))},modifier=Modifier.fillMaxWidth()){Text("Allow live conditions")};Spacer(Modifier.height(10.dp));Button(onClick={vm.seed(true)},modifier=Modifier.fillMaxWidth()){Text("Begin with sample waters")};TextButton(onClick={vm.seed(false)},modifier=Modifier.fillMaxWidth()){Text("Start with an empty vault")}}}
    Text("No account · No tracking · Works offline",Modifier.fillMaxWidth().padding(top=14.dp),textAlign=TextAlign.Center,color=Muted)}}}

@Composable fun Screen(title:String,subtitle:String?=null,actions:@Composable RowScope.()->Unit={},content:LazyListScope.()->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,top=18.dp,end=18.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.headlineLarge);subtitle?.let{Text(it,color=Muted)}};actions()}};content()}}
@Composable fun HeritageCard(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit){Card(modifier=modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(20.dp),onClick=onClick?:{}){Column(Modifier.padding(18.dp),content=content)}}
@Composable fun Stat(value:String,label:String,modifier:Modifier=Modifier){Column(modifier.background(Inset,RoundedCornerShape(16.dp)).padding(14.dp)){Text(value,color=BrassSoft,style=MaterialTheme.typography.titleLarge);Text(label,color=Muted,style=MaterialTheme.typography.bodyMedium)}}
// Rounds ounces before splitting, so 15.6 oz reads "1 lb 0 oz" rather than "0 lb 16 oz" (TB-A-13).
fun Double.weight(unit:UnitSystem)=if(unit==UnitSystem.METRIC) if(this>=1000)"%.2f kg".format(this/1000) else "%.0f g".format(this) else Weights.toPoundsAndOunces(this).let{(lb,oz)->"$lb lb $oz oz"}
val numberKeyboard=KeyboardOptions(keyboardType=KeyboardType.Decimal)
// Selected chips and segmented buttons defaulted to the Material purple container rather than the brand brass (TB-A-19).
@Composable fun brassChipColours()=FilterChipDefaults.filterChipColors(selectedContainerColor=Brass,selectedLabelColor=Background,labelColor=Ink,containerColor=Inset)
fun Instant.pretty():String=atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))

@Composable fun Vault(s:AppState,nav:NavHostController){val pb=s.catches.filter{it.item.weightGrams!=null}.maxByOrNull{it.item.weightGrams!!};val sol=Astronomy.calculate();Screen("The Vault","A private ledger of time well spent",actions={IconButton({nav.navigate("catches")},modifier=Modifier.testTag("searchCatches")){Icon(Icons.Default.Search,"Search your catches")};IconButton({nav.navigate("settings")}){Icon(Icons.Default.Settings,"Settings")}}){item{HeritageCard(onClick=pb?.let{{nav.navigate("catch/${it.item.id}")}}){Text("FEATURED PERSONAL BEST",color=Brass,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(12.dp));Text(pb?.species?.name?:"Your finest catch awaits",style=MaterialTheme.typography.headlineMedium);Text(pb?.item?.weightGrams?.weight(s.settings.unitSystem)?:"Log a catch to begin your board",color=Muted)}};item{HeritageCard(onClick={nav.navigate("solunar")}){Text("TODAY ON THE BANK",color=Teal,style=MaterialTheme.typography.labelLarge);Text("${sol.rating}/5 day · ${sol.moonPhase}",style=MaterialTheme.typography.titleLarge);Text("Next window ${sol.windows.first().start}–${sol.windows.first().end}  ·  ↑ ${sol.sunrise}  ↓ ${sol.sunset}",color=Muted)}};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${s.catches.size}","fish landed",Modifier.weight(1f).clickable{nav.navigate("catches")});Stat("${s.catches.mapNotNull{it.species?.id}.distinct().size}","species",Modifier.weight(1f));Stat("${s.waters.size}","waters",Modifier.weight(1f))}};item{Text("Personal best board",style=MaterialTheme.typography.titleLarge)};items(s.catches.filter{it.item.weightGrams!=null}.groupBy{it.species?.id}.mapNotNull{(_,v)->v.maxByOrNull{it.item.weightGrams?:0.0}}){c->HeritageCard(onClick={nav.navigate("species/${c.species?.id}")}){Row{Text(c.species?.name?:"Unknown",Modifier.weight(1f));Text(c.item.weightGrams?.weight(s.settings.unitSystem).orEmpty(),color=BrassSoft)}}};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){AssistChip({nav.navigate("tackle")},{Text("My Tacklebox")},leadingIcon={Icon(Icons.Default.Inventory2,null)});AssistChip({nav.navigate("solunar")},{Text("Bite windows")},leadingIcon={Icon(Icons.Default.DarkMode,null)})}}}}

// Waters could only ever arrive from the optional sample seed: Repository.addWater existed but nothing called it,
// so a tester who chose "Start with an empty vault" could never have one (TB-A-11).
@Composable fun Waters(s:AppState,vm:MainViewModel,nav:NavHostController){
    var adding by rememberSaveable{mutableStateOf(false)}
    Screen("Waters","Your places, kept private",actions={IconButton({adding=true}){Icon(Icons.Default.Add,"Add a water")}}){
        item{HeritageCard(onClick={nav.navigate("rivers")}){Text("River conditions",style=MaterialTheme.typography.titleLarge);Text("Levels and nearby gauges",color=Muted)}}
        item{HeritageCard(onClick={nav.navigate("tides")}){Text("Tides & sea",style=MaterialTheme.typography.titleLarge);Text("Coastal waves and sea state",color=Muted)}}
        item{Text("Water passports",style=MaterialTheme.typography.titleLarge)}
        if(s.waters.isEmpty())item{Empty("No waters saved","Add a water to build your private map.")}
        items(s.waters){w->HeritageCard(onClick={nav.navigate("water/${w.id}")}){Row{Icon(Icons.Default.Water,null,tint=Teal);Spacer(Modifier.width(12.dp));Column{Text(w.name,style=MaterialTheme.typography.titleLarge);Text("${w.type.name.lowercase().replaceFirstChar(Char::uppercase)} · ${w.region}",color=Muted)}}}}
    }
    if(adding)WaterFormDialog(onDismiss={adding=false},onSave={name,type,region->vm.addWater(name,type,region);adding=false})
}

@Composable fun WaterFormDialog(onDismiss:()->Unit,onSave:(String,WaterType,String)->Unit){
    var name by rememberSaveable{mutableStateOf("")}; var region by rememberSaveable{mutableStateOf("")}
    var type by rememberSaveable{mutableStateOf(WaterType.LAKE)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add a water")},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Name")},singleLine=true,modifier=Modifier.testTag("waterName"))
            OutlinedTextField(region,{region=it},label={Text("Region")},singleLine=true)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(WaterType.entries.toList()){t->FilterChip(type==t,{type=t},{Text(t.name.lowercase().replaceFirstChar(Char::uppercase))},colors=brassChipColours())}}}},
        confirmButton={TextButton({if(name.isNotBlank())onSave(name.trim(),type,region.trim())},enabled=true){Text("Save")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

// Swim notes are editable here, matching iOS; they were read-only while the card invited the angler to keep them.
@Composable fun WaterPassport(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val w=s.waters.firstOrNull{it.id==id};val catches=s.catches.filter{it.water?.id==id}
    var notes by remember(w?.id){mutableStateOf(w?.swimNotes.orEmpty())}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    Screen(w?.name?:"Water passport",w?.region){
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${catches.size}","catches",Modifier.weight(1f));Stat("${catches.mapNotNull{it.species}.distinctBy{it.id}.size}","species",Modifier.weight(1f))}}
        item{HeritageCard{Text("Private swim notes",color=Brass)
            OutlinedTextField(notes,{notes=it;w?.let{water->vm.updateWater(water.copy(swimNotes=it))}},placeholder={Text("Reeds on the west bank fish well at dusk…")},modifier=Modifier.fillMaxWidth())}}
        item{Text("Most productive",style=MaterialTheme.typography.titleLarge);Text(catches.groupingBy{it.species?.name?:"Unknown"}.eachCount().maxByOrNull{it.value}?.key?:"Log catches here to reveal patterns",color=Muted)}
        item{TextButton({confirmDelete=true},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete this water")}}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete ${w?.name.orEmpty()}?")},
        text={Text("The water is removed. Catches and sessions logged here are kept, but they will no longer name a water.")},
        confirmButton={TextButton({id?.let{vm.deleteWater(it)};confirmDelete=false;nav.popBackStack()}){Text("Delete")}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

@Composable fun Sessions(s:AppState,vm:MainViewModel){var selected by rememberSaveable{mutableStateOf<Long?>(s.waters.firstOrNull()?.id)};val active=s.sessions.firstOrNull{it.item.endAt==null};Screen("Sessions","Hours on the bank, remembered"){item{HeritageCard{if(active==null){Text("Start a session",style=MaterialTheme.typography.titleLarge);s.waters.forEach{FilterChip(selected==it.id,{selected=it.id},{Text(it.name)},colors=brassChipColours())};Button({vm.startSession(selected)},enabled=s.waters.isNotEmpty()){Text("Start fishing")}}else{Text("Session in progress",color=Teal);Text(active.water?.name?:"Unspecified water",style=MaterialTheme.typography.headlineMedium);Button({vm.stopSession(active.item.id)}){Text("Finish session")}}}};items(s.sessions){x->HeritageCard{Text(x.water?.name?:"Unspecified water",style=MaterialTheme.typography.titleLarge);Text("${x.item.startAt.pretty()} · ${x.catches.size} ${if(x.catches.size==1)"catch" else "catches"}",color=Muted);Text(if(x.item.endAt==null)"LIVE" else "Finished",color=if(x.item.endAt==null)Teal else Brass)}}}}

@Composable fun Insights(s:AppState,nav:NavHostController){val total=s.catches.mapNotNull{it.item.weightGrams}.sum();Screen("Insights","Patterns emerge from patient notes"){item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${s.catches.size}","landed",Modifier.weight(1f));Stat(total.weight(s.settings.unitSystem),"total weight",Modifier.weight(1f))}};item{HeritageCard(onClick={nav.navigate("year")}){Text("YEAR ON THE WATER",color=Brass);Text("Your season, distilled",style=MaterialTheme.typography.headlineMedium);Text("Open shareable summary →",color=Muted)}};item{Breakdown("Catches over time",s.catches.groupingBy{it.item.caughtAt.atZone(ZoneId.systemDefault()).month.name.take(3)}.eachCount())};item{Breakdown("Species",s.catches.groupingBy{it.species?.name?:"Unknown"}.eachCount())};item{Breakdown("Waters",s.catches.groupingBy{it.water?.name?:"Unspecified"}.eachCount())};item{HeritageCard{Text("Conditions insight",style=MaterialTheme.typography.titleLarge);Text(Insight.conditions(s.catches),color=Muted)}}}}
@Composable fun Breakdown(title:String,data:Map<String,Int>){HeritageCard{Text(title,style=MaterialTheme.typography.titleLarge);if(data.isEmpty())Text("Not enough data yet",color=Muted) else data.entries.sortedByDescending{it.value}.take(5).forEach{Row(Modifier.padding(top=8.dp)){Text(it.key,Modifier.weight(1f));Text("${it.value}",color=Brass)}}}}
@Composable fun YearOnWater(s:AppState){val year=Year.now().value;val catches=s.catches.filter{it.item.caughtAt.atZone(ZoneId.systemDefault()).year==year};val biggest=catches.maxByOrNull{it.item.weightGrams?:0.0};val hours=s.sessions.filter{it.item.endAt!=null}.sumOf{Duration.between(it.item.startAt,it.item.endAt!!).toMinutes()}/60;Screen("Year on the Water","$year · a season worth keeping"){item{Card(colors=CardDefaults.cardColors(containerColor=Inset),shape=RoundedCornerShape(28.dp)){Column(Modifier.padding(26.dp)){Text("TACKLEBOX · $year",color=Brass);Text("${catches.size}",style=MaterialTheme.typography.displaySmall);Text("fish landed",color=Muted);HorizontalDivider(Modifier.padding(vertical=16.dp));Text("${catches.mapNotNull{it.item.weightGrams}.sum().weight(s.settings.unitSystem)} carried gently");Text("${biggest?.species?.name?:"No biggest fish yet"} · ${biggest?.item?.weightGrams?.weight(s.settings.unitSystem).orEmpty()}");Text("$hours hours on the bank");Text("Top bait · ${catches.groupingBy{it.item.bait?:"Unrecorded"}.eachCount().maxByOrNull{it.value}?.key?:"—"}",color=BrassSoft)}}};item{val context=LocalContext.current;Button({ShareSheet.season(context,s,year)},Modifier.fillMaxWidth().testTag("shareSeason")){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Share summary")}}}}

/**
 * The capture screen. Previously it could not record a water (the waterId argument was hardcoded null and there was
 * no picker), never linked the catch to the open session, entered weight in ounces but displayed pounds-and-ounces,
 * lost everything on rotation, and offered an "Identify from photo" button that did nothing (TB-A-02, TB-A-03,
 * TB-A-13, TB-A-15, TB-A-07).
 */
@Composable fun LogCatch(s:AppState,vm:MainViewModel,nav:NavHostController){
    val metric=s.settings.unitSystem==UnitSystem.METRIC
    val openSession=s.sessions.firstOrNull{it.item.endAt==null}
    // rememberSaveable throughout: the activity is recreated on rotation and none of this survived it (TB-A-15).
    var species by rememberSaveable{mutableStateOf<Long?>(null)}
    var grams by rememberSaveable{mutableStateOf("")}; var pounds by rememberSaveable{mutableStateOf("")}; var ounces by rememberSaveable{mutableStateOf("")}
    var length by rememberSaveable{mutableStateOf("")}
    var rig by rememberSaveable{mutableStateOf("")}; var bait by rememberSaveable{mutableStateOf("")}
    var returned by rememberSaveable{mutableStateOf(true)}
    var photo by rememberSaveable{mutableStateOf<String?>(null)}
    var water by rememberSaveable{mutableStateOf(openSession?.water?.id)}
    var newSpecies by rememberSaveable{mutableStateOf("")}; var addingSpecies by rememberSaveable{mutableStateOf(false)}
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){photo=it?.toString()}
    var pendingPhoto by remember{mutableStateOf<Uri?>(null)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(ok)photo=pendingPhoto?.toString()}
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){pendingPhoto=CapturePhoto.destination(context);camera.launch(pendingPhoto!!)}}
    fun capture(){ if(CapturePhoto.permitted(context)){pendingPhoto=CapturePhoto.destination(context);camera.launch(pendingPhoto!!)} else cameraPermission.launch(Manifest.permission.CAMERA) }

    Screen("Log a catch","A quiet record of the moment"){
        item{HeritageCard{
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)).background(Inset).clickable{picker.launch("image/*")},contentAlignment=Alignment.Center){
                if(photo==null)Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.AddAPhoto,null,tint=Brass);Text("Add photo",color=Muted)}
                else AsyncImage(photo,"Photo of this catch",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
            Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton({picker.launch("image/*")},Modifier.weight(1f)){Icon(Icons.Default.PhotoLibrary,null);Text(" Library")}
                OutlinedButton({capture()},Modifier.weight(1f)){Icon(Icons.Default.PhotoCamera,null);Text(" Camera")}}}}
        item{Text("Species",style=MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                items(s.species){sp->FilterChip(species==sp.id,{species=sp.id},{Text(sp.name)},colors=brassChipColours())}
                item{FilterChip(false,{addingSpecies=true},{Text("＋ Add species")},colors=brassChipColours())}}}
        item{SpeciesIdRow(s,vm,photo,suggestions,onPick={name->s.species.firstOrNull{it.name.equals(name,true)}?.let{species=it.id} ?: vm.addSpecies(name);vm.clearSuggestions()})}
        item{Text("Water",style=MaterialTheme.typography.titleLarge)
            if(s.waters.isEmpty())Text("Add a water on the Waters tab to record where this fish came from.",color=Muted)
            else LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(s.waters){w->FilterChip(water==w.id,{water=if(water==w.id)null else w.id},{Text(w.name)},colors=brassChipColours())}}}
        item{if(metric)OutlinedTextField(grams,{grams=it},label={Text("Weight (g)")},keyboardOptions=numberKeyboard,modifier=Modifier.fillMaxWidth().testTag("weightGrams"))
             else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                 OutlinedTextField(pounds,{pounds=it},label={Text("Weight (lb)")},keyboardOptions=numberKeyboard,modifier=Modifier.weight(1f).testTag("weightPounds"))
                 OutlinedTextField(ounces,{ounces=it},label={Text("oz")},keyboardOptions=numberKeyboard,modifier=Modifier.weight(1f).testTag("weightOunces"))}}
        item{OutlinedTextField(length,{length=it},label={Text(if(metric)"Length (cm)" else "Length (in)")},keyboardOptions=numberKeyboard,modifier=Modifier.fillMaxWidth())}
        item{PresetField(rig,{rig=it},"Rig",s.presets.filter{it.kind==PresetKind.RIG}.map{it.name})
             PresetField(bait,{bait=it},"Bait",s.presets.filter{it.kind==PresetKind.BAIT}.map{it.name})}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Returned",Modifier.weight(1f));Switch(returned,{returned=it})}}
        item{if(openSession!=null)Text("Will be added to your open session at ${openSession.water?.name?:"an unspecified water"}.",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        item{Button(onClick={
                val weightGrams=if(metric) grams.toDoubleOrNull() else Weights.fromPoundsAndOunces(pounds,ounces)
                val lengthCm=length.toDoubleOrNull()?.let{if(metric)it else it*2.54}
                vm.addCatch(species,weightGrams,lengthCm,rig,bait,returned,water,photo){nav.navigate("catch/$it"){popUpTo("vault")}}
            },modifier=Modifier.fillMaxWidth().testTag("saveCatch"),enabled=species!=null){Text("Save catch")}}
    }
    if(addingSpecies)AlertDialog(onDismissRequest={addingSpecies=false},title={Text("Add a species")},
        text={OutlinedTextField(newSpecies,{newSpecies=it},label={Text("Name")},singleLine=true)},
        confirmButton={TextButton({if(newSpecies.isNotBlank()){vm.addSpecies(newSpecies.trim());newSpecies="";addingSpecies=false}}){Text("Add")}},
        dismissButton={TextButton({addingSpecies=false}){Text("Cancel")}})
}

/** Free text that can also be filled from a saved preset — the brief asked for presets, the field was plain text. */
@Composable fun PresetField(value:String,onChange:(String)->Unit,label:String,presets:List<String>){
    OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth())
    if(presets.isNotEmpty())LazyRow(Modifier.padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        items(presets){p->FilterChip(value==p,{onChange(if(value==p)"" else p)},{Text(p)},colors=brassChipColours())}}
}

/** Photo species identification. The button used to be inert with no client behind it (TB-A-07). */
@Composable fun SpeciesIdRow(s:AppState,vm:MainViewModel,photo:String?,suggestions:LiveState<List<SpeciesSuggestion>>,onPick:(String)->Unit){
    if(s.settings.speciesIdToken.isBlank()){Text("Species ID is available after adding your own token in Settings.",color=Muted);return}
    OutlinedButton({vm.identify(photo,s.settings.speciesIdToken)},enabled=photo!=null&&suggestions !is LiveState.Loading){
        Icon(Icons.Default.AutoAwesome,null);Text(if(suggestions is LiveState.Loading)" Identifying…" else " Identify from photo")}
    when(val x=suggestions){
        is LiveState.Error->Text(x.message,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
        is LiveState.Data->if(x.value.isEmpty())Text("No confident match. Pick the species by hand.",color=Muted)
            else Column{x.value.forEach{sug->TextButton({onPick(sug.commonName?:sug.scientificName)}){Text("${sug.commonName?:sug.scientificName} · ${(sug.score).roundToInt()}%")}}}
        else->{}
    }
}

// Gear was always filed as OTHER and presets always as RIG, with no way to pick either and no way to delete
// anything — deleteGear existed in the DAO but nothing reached it, and presets had no delete at all.
@Composable fun Tacklebox(s:AppState,vm:MainViewModel){
    var name by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf(GearCategory.ROD)}
    var kind by rememberSaveable{mutableStateOf(PresetKind.RIG)}
    Screen("My Tacklebox","Gear that earns its place"){
        item{HeritageCard{Text("Gear performance",color=Brass);Text("Best rig · ${s.catches.mapNotNull{it.item.rig}.filter{it.isNotBlank()}.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key?:"—"}");Text("Top bait · ${s.catches.mapNotNull{it.item.bait}.filter{it.isNotBlank()}.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key?:"—"}",color=Muted)}}
        items(s.gear){g->HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(g.name,style=MaterialTheme.typography.titleLarge);Text(g.category.name.lowercase().replaceFirstChar(Char::uppercase),color=Muted)};IconButton({vm.deleteGear(g)}){Icon(Icons.Default.Delete,"Delete ${g.name}",tint=Muted)}}}}
        item{OutlinedTextField(name,{name=it},label={Text("New gear or preset")},modifier=Modifier.fillMaxWidth().testTag("gearName"))
            Text("Gear category",color=Muted,style=MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(GearCategory.entries.toList()){c->FilterChip(category==c,{category=c},{Text(c.name.lowercase().replaceFirstChar(Char::uppercase))},colors=brassChipColours())}}
            Row(Modifier.padding(top=8.dp)){Button({if(name.isNotBlank()){vm.addGear(name.trim(),category);name=""}}){Text("Add gear")}
                Spacer(Modifier.width(8.dp))
                OutlinedButton({if(name.isNotBlank()){vm.addPreset(name.trim(),kind);name=""}}){Text("Save ${kind.name.lowercase()}")}}
            LazyRow(Modifier.padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(PresetKind.entries.toList()){k->FilterChip(kind==k,{kind=k},{Text(k.name.lowercase().replaceFirstChar(Char::uppercase))},colors=brassChipColours())}}}
        item{Text("Quick picks",style=MaterialTheme.typography.titleLarge);if(s.presets.isEmpty())Text("No saved rigs or baits",color=Muted)}
        items(s.presets){p->HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name);Text(p.kind.name.lowercase().replaceFirstChar(Char::uppercase),color=Muted,style=MaterialTheme.typography.bodyMedium)};IconButton({vm.deletePreset(p)}){Icon(Icons.Default.Delete,"Delete ${p.name}",tint=Muted)}}}}
    }
}

// Bite windows now follow the device rather than a hardcoded 52.5/-1.5 (TB-A-10), and say so when they cannot.
@Composable fun Solunar(vm:MainViewModel){
    var place by remember{mutableStateOf<Pair<Double,Double>?>(null)}
    LaunchedEffect(Unit){place=vm.solunarPlace()}
    val located=place!=null&&place!=DeviceLocation.FALLBACK_INLAND
    val d=place?.let{Astronomy.calculate(latitude=it.first,longitude=it.second)}?:Astronomy.calculate()
    Screen("Bite windows",if(located)"Calculated on-device for your position" else "Calculated on-device · showing central UK"){item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${d.rating}/5","day rating",Modifier.weight(1f));Stat(d.moonPhase,"moon",Modifier.weight(1f))}};item{HeritageCard{Text("Sun & moon",style=MaterialTheme.typography.titleLarge);Text("Sunrise ${d.sunrise}  ·  Sunset ${d.sunset}",color=Muted);Text("Moonrise ${d.moonrise}",color=Muted)}};items(d.windows){w->HeritageCard{Row{Column(Modifier.weight(1f)){Text(w.label);Text(if(w.major)"Major feeding period" else "Minor feeding period",color=if(w.major)Brass else Muted)};Text("${w.start}–${w.end}")}}}}}
@Composable fun Tides(vm:MainViewModel){val live by vm.marine.collectAsStateWithLifecycle();LaunchedEffect(Unit){vm.marine()};Screen("Tides & sea","Live coastal outlook · Open-Meteo"){item{when(val x=live){LiveState.Idle,LiveState.Loading->Loading();is LiveState.Error->ErrorCard(x.message){vm.marine()};is LiveState.Data->{val h=x.value.hourly;if(h==null||h.waveHeight.isEmpty())Empty("No coastal data","You may be inland or outside forecast coverage.") else HeritageCard{Text("Sea state",style=MaterialTheme.typography.headlineMedium);h.time.take(8).forEachIndexed{i,t->Row{Text(t.takeLast(5),Modifier.weight(1f));Text("${h.waveHeight.getOrNull(i)?:0.0} m · ${h.wavePeriod.getOrNull(i)?:0.0} s",color=Teal)}}}}}}}}
@Composable fun Rivers(vm:MainViewModel){val live by vm.river.collectAsStateWithLifecycle();LaunchedEffect(Unit){vm.river()};Screen("River conditions","Environment Agency gauges"){item{when(val x=live){LiveState.Idle,LiveState.Loading->Loading();is LiveState.Error->ErrorCard(x.message){vm.river()};is LiveState.Data->if(x.value.items.isEmpty())Empty("No nearby gauges","Try again nearer a gauged river.")else Column{Text("Nearby readings",style=MaterialTheme.typography.titleLarge);x.value.items.take(8).forEach{HeritageCard{Text(it.value?.let{"$it m"}?:"Reading unavailable");Text(it.dateTime?:"Latest observation",color=Muted)}}}}}}}

@Composable fun SpeciesDetail(s:AppState,id:Long?,nav:NavHostController){val sp=s.species.firstOrNull{it.id==id};val catches=s.catches.filter{it.species?.id==id};Screen(sp?.name?:"Species record",sp?.scientificName){item{Box(Modifier.fillMaxWidth().height(170.dp).background(Inset,RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.SetMeal,null,tint=Brass,modifier=Modifier.size(64.dp))}};item{Text(sp?.about?:"A personal record built from your catches.",color=Muted)};item{Text("Catch history",style=MaterialTheme.typography.titleLarge)};items(catches){c->HeritageCard(onClick={nav.navigate("catch/${c.item.id}")}){Text(c.item.caughtAt.pretty());Text(c.item.weightGrams?.weight(s.settings.unitSystem)?:"Weight not recorded",color=Brass)}}}}
@Composable fun CatchDetail(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val c=s.catches.firstOrNull{it.item.id==id}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    Screen(c?.species?.name?:"Catch detail",c?.item?.caughtAt?.pretty()){
        item{Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp)).background(Inset),contentAlignment=Alignment.Center){if(c?.item?.photoUri!=null)AsyncImage(c.item.photoUri,"Photo of this catch",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Default.SetMeal,null,tint=Brass,modifier=Modifier.size(72.dp))}}
        item{HeritageCard{Text(c?.item?.weightGrams?.weight(s.settings.unitSystem)?:"Weight not recorded",style=MaterialTheme.typography.headlineMedium)
            c?.item?.lengthCm?.let{Text(if(s.settings.unitSystem==UnitSystem.METRIC)"%.0f cm".format(it) else "%.1f in".format(it/2.54),color=Muted)}
            Text("${c?.water?.name?:"Water not recorded"} · ${if(c?.item?.returned==true)"Returned" else "Kept"}",color=Muted)}}
        item{HeritageCard{Text("Tackle",color=Brass);Text("Rig · ${c?.item?.rig?.ifBlank{"Not recorded"}?:"Not recorded"}");Text("Bait · ${c?.item?.bait?.ifBlank{"Not recorded"}?:"Not recorded"}")}}
        // Weather is genuinely captured now, so the card reports what was recorded instead of blaming the network
        // for a snapshot the app never even attempted (TB-A-09).
        item{HeritageCard{Text("Conditions",color=Teal)
            val w=c?.conditions
            Text("Moon · ${w?.moonPhase?:"Not available"}")
            if(w?.airTempC!=null)Text("Air · %.1f °C".format(w.airTempC))
            if(w?.windSpeedKph!=null)Text("Wind · %.0f kph${w.windDirection?.let{" $it"}.orEmpty()}".format(w.windSpeedKph))
            if(w?.pressureHpa!=null)Text("Pressure · %.0f hPa".format(w.pressureHpa))
            if(w?.airTempC==null&&w?.windSpeedKph==null&&w?.pressureHpa==null)Text("No weather was recorded for this catch.",color=Muted)}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton({c?.let{ShareSheet.catchCard(context,it,s.settings.unitSystem)}},Modifier.weight(1f)){Icon(Icons.Default.Share,null);Text(" Share")}
            TextButton({confirmDelete=true},Modifier.weight(1f),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete")}}}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this catch?")},
        text={Text("It is removed from your vault, your records and your insights. This cannot be undone.")},
        confirmButton={TextButton({id?.let{vm.deleteCatch(it)};confirmDelete=false;nav.popBackStack()}){Text("Delete")}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

@Composable fun Settings(s:AppState,vm:MainViewModel){
    var token by rememberSaveable(s.settings.speciesIdToken){mutableStateOf(s.settings.speciesIdToken)}
    var confirm by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    val exported by vm.exported.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    // Hand the finished file straight to the share sheet, then clear it so rotating does not re-open the chooser.
    LaunchedEffect(exported){exported?.let{ShareSheet.file(context,it,"application/json","Export your Tacklebox journal");vm.clearExport()}}
    Screen("Settings","Your data, your choices"){
        item{HeritageCard{Text("Units",style=MaterialTheme.typography.titleLarge);SingleChoiceSegmentedButtonRow{UnitSystem.entries.forEachIndexed{i,u->SegmentedButton(s.settings.unitSystem==u,{vm.settings(s.settings.copy(unitSystem=u))},SegmentedButtonDefaults.itemShape(i,2),colors=SegmentedButtonDefaults.colors(activeContainerColor=Brass,activeContentColor=Background,inactiveContainerColor=Inset,inactiveContentColor=Ink)){Text(u.name.lowercase().replaceFirstChar(Char::uppercase))}}}}}
        // The Drive switch only ever persisted a boolean — there is no Drive code, no OAuth client and no
        // GoogleSignIn dependency in the app. Rather than keep a control that implies a backup is happening, say
        // plainly that it is not built yet and point at the export that does work.
        item{HeritageCard{Text("Backup",style=MaterialTheme.typography.titleLarge);Text("Cloud backup isn’t built yet. Your journal lives on this device only — use Export below to keep a copy.",color=Muted)}}
        item{OutlinedTextField(token,{token=it},label={Text("Species-ID API token")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Text("An iNaturalist token, used only to identify a photo. It expires after about a day.",color=Muted,style=MaterialTheme.typography.bodyMedium);Button({vm.settings(s.settings.copy(speciesIdToken=token))}){Text("Save token")}}
        item{HeritageCard{Text("Data",style=MaterialTheme.typography.titleLarge);OutlinedButton({vm.exportJson()},Modifier.fillMaxWidth().testTag("exportJson")){Icon(Icons.Default.FileDownload,null);Text(" Export JSON")};TextButton({confirm=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete catches, waters & gear")}}}
        item{Text("No ads · No analytics · No subscriptions",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=Muted)}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Delete your data?")},text={Text("This removes catches, sessions, waters, gear and presets from this device. Species and settings remain.")},confirmButton={TextButton({vm.deleteData();confirm=false}){Text("Delete")}},dismissButton={TextButton({confirm=false}){Text("Cancel")}})
    notice?.let{message->AlertDialog(onDismissRequest={vm.clearNotice()},title={Text("Export failed")},text={Text(message)},confirmButton={TextButton({vm.clearNotice()}){Text("OK")}})}
}
@Composable fun Empty(title:String,body:String){Column(Modifier.fillMaxWidth().padding(30.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.SetMeal,null,tint=Brass);Text(title,style=MaterialTheme.typography.titleLarge);Text(body,color=Muted,textAlign=TextAlign.Center)}}
@Composable fun Loading(){Box(Modifier.fillMaxWidth().padding(40.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Brass)}}
@Composable fun ErrorCard(message:String,retry:()->Unit){HeritageCard{Text(message,color=MaterialTheme.colorScheme.error);TextButton(retry){Text("Try again")}}}
// --- Catches: the browsable, searchable list -------------------------------------------------------------------
// Neither app had search or filter of any kind, and the Vault only ever showed one catch per species, so a logged
// catch was very hard to get back to. This is the list plus the filter sheet that drives CatchFilter.
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Catches(s:AppState,nav:NavHostController){
    var filter by rememberSaveable(stateSaver=CatchFilterSaver){mutableStateOf(CatchFilter())}
    var showFilters by rememberSaveable{mutableStateOf(false)}
    val bests=remember(s.catches){CatchFilter.personalBests(s.catches)}
    val results=remember(s.catches,filter){filter.apply(s.catches)}
    Screen("Catches","Everything you have landed",actions={
        IconButton({showFilters=true},modifier=Modifier.testTag("filterCatches")){
            Icon(if(filter.isActive)Icons.Default.FilterAlt else Icons.Default.FilterAltOff,"Filter catches",tint=Brass)}}){
        item{OutlinedTextField(filter.text,{filter=filter.copy(text=it)},
            label={Text("Species, water, rig or bait")},singleLine=true,
            leadingIcon={Icon(Icons.Default.Search,null)},
            trailingIcon={if(filter.text.isNotEmpty())IconButton({filter=filter.copy(text="")}){Icon(Icons.Default.Close,"Clear search")}},
            modifier=Modifier.fillMaxWidth().testTag("catchSearch"))}
        if(filter.isActive)item{HeritageCard{
            Text(filter.activeSummary(s.settings.unitSystem).joinToString(" · "),color=Muted,style=MaterialTheme.typography.bodyMedium)
            TextButton({filter=CatchFilter()},modifier=Modifier.testTag("clearFilters")){Text("Clear filters")}}}
        if(results.isEmpty())item{
            if(s.catches.isEmpty())Empty("Your vault is empty","Tap + to log your first catch.")
            else Empty("Nothing matched","No catch fits those filters. Try widening the date range or clearing the search.")}
        else{
            item{Text("${results.size} ${if(results.size==1)"catch" else "catches"}",color=Muted,style=MaterialTheme.typography.bodyMedium)}
            items(results){c->HeritageCard(onClick={nav.navigate("catch/${c.item.id}")}){Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Inset),contentAlignment=Alignment.Center){
                    if(c.item.photoUri!=null)AsyncImage(c.item.photoUri,"Photo of this catch",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    else Icon(Icons.Default.SetMeal,null,tint=Brass)}
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(c.species?.name?:"Unknown species",style=MaterialTheme.typography.titleLarge)
                        if(bests[c.species?.name]?.item?.id==c.item.id){Spacer(Modifier.width(6.dp))
                            Text("PB",color=Background,style=MaterialTheme.typography.bodyMedium,
                                modifier=Modifier.background(BrassSoft,RoundedCornerShape(6.dp)).padding(horizontal=6.dp))}}
                    Text(c.item.weightGrams?.weight(s.settings.unitSystem)?:"Weight not recorded",color=BrassSoft)
                    Text(listOfNotNull(c.item.caughtAt.pretty(),c.water?.name,c.item.bait?.ifBlank{null}).joinToString(" · "),
                        color=Muted,style=MaterialTheme.typography.bodyMedium,maxLines=1)}}}}
        }
    }
    if(showFilters)CatchFilterSheet(s,filter,{filter=it},{showFilters=false})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CatchFilterSheet(s:AppState,filter:CatchFilter,onChange:(CatchFilter)->Unit,onDismiss:()->Unit){
    // Round thresholds in whichever units the angler thinks in, converted to canonical grams.
    val weights=if(s.settings.unitSystem==UnitSystem.IMPERIAL)
        listOf("1 lb" to 453.6,"5 lb" to 2268.0,"10 lb" to 4536.0,"20 lb" to 9072.0)
        else listOf("500 g" to 500.0,"1 kg" to 1000.0,"5 kg" to 5000.0,"10 kg" to 10000.0)
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Background){
        // Scrollable: on a short screen, or at a large font scale, the personal-bests toggle sits below the fold
        // and would otherwise be unreachable.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("Filter",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
                TextButton({onChange(CatchFilter())}){Text("Clear")}}
            Text("WHEN",color=Brass,style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(CatchFilter.Period.entries.toList()){p->
                FilterChip(filter.period==p,{onChange(filter.copy(period=p))},{Text(p.title)},colors=brassChipColours())}}
            Text("SPECIES",color=Brass,style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(s.species){sp->
                FilterChip(filter.speciesName==sp.name,{onChange(filter.copy(speciesName=if(filter.speciesName==sp.name)null else sp.name))},{Text(sp.name)},colors=brassChipColours())}}
            if(s.waters.isNotEmpty()){
                Text("WATER",color=Brass,style=MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(s.waters){w->
                    FilterChip(filter.waterName==w.name,{onChange(filter.copy(waterName=if(filter.waterName==w.name)null else w.name))},{Text(w.name)},colors=brassChipColours())}}}
            Text("AT LEAST",color=Brass,style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(weights){(label,grams)->
                FilterChip(filter.minimumGrams==grams,{onChange(filter.copy(minimumGrams=if(filter.minimumGrams==grams)null else grams))},{Text(label)},colors=brassChipColours())}}
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("Personal bests only");Text("Your best fish of each species",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                Switch(filter.personalBestsOnly,{onChange(filter.copy(personalBestsOnly=it))})}
        }
    }
}

/** rememberSaveable needs to know how to store the filter across rotation and process death. */
val CatchFilterSaver=androidx.compose.runtime.saveable.listSaver<CatchFilter,Any?>(
    save={listOf(it.text,it.speciesName,it.waterName,it.period.name,it.personalBestsOnly,it.minimumGrams)},
    restore={CatchFilter(it[0] as String,it[1] as String?,it[2] as String?,CatchFilter.Period.valueOf(it[3] as String),it[4] as Boolean,it[5] as Double?)}
)
