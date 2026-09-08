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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.ui.FishGlyph
import uk.co.tacklebox.app.services.*
import uk.co.tacklebox.app.ui.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{TackleboxTheme{TackleboxRoot()}}}}
data class Tab(val route:String,val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
// The same four symbols as the iOS tab bar, in their outline weight: shield, drop, calendar, chart.bar. Android
// had a house, waves and a clock against iOS's shield, droplet and calendar — three of the four icons on the one
// piece of chrome that is on screen the entire time (TB-P-10).
val tabs=listOf(Tab("vault","Vault",Icons.Outlined.Shield),Tab("waters","Waters",Icons.Outlined.WaterDrop),Tab("sessions","Sessions",Icons.Outlined.CalendarMonth),Tab("insights","Insights",Icons.Outlined.BarChart))

@Composable fun TackleboxRoot(vm:MainViewModel=viewModel()){
    val state by vm.state.collectAsStateWithLifecycle(); val nav=rememberNavController()
    val back by nav.currentBackStackEntryAsState(); val route=back?.destination?.route
    val showFab=route in setOf("vault","waters","sessions","insights")
    // Capture is presented as its own thing, not as a tab: iOS opens it as a modal sheet, and leaving the tab bar
    // visible behind it made the same task look like a different kind of thing on each platform (TB-P-04).
    val modal=route in setOf("log","edit/{id}")
    // Nothing until the store has answered — see AppState.loaded. Showing onboarding while the read is in flight
    // asks a returning angler to set the app up again.
    if(!state.loaded){Box(Modifier.fillMaxSize().background(Background))}
    else if(!state.settings.onboardingComplete){Onboarding(vm)} else Scaffold(containerColor=Background,bottomBar={if(!modal)BottomBar(nav,showFab)}){pad ->
        NavHost(nav,"vault",Modifier.padding(pad)){
            composable("vault"){Vault(state,vm,nav)}; composable("waters"){Waters(state,vm,nav)}; composable("sessions"){Sessions(state,vm)}; composable("insights"){Insights(state,nav)}; composable("log"){LogCatch(state,vm,nav)}
            composable("catches"){Catches(state,nav)}; composable("edit/{id}"){EditCatch(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("tackle"){Tacklebox(state,vm)}; composable("solunar"){Solunar(vm,nav)}; composable("tides"){Tides(vm)}; composable("rivers"){Rivers(vm)}; composable("settings"){Settings(state,vm,nav)}; composable("year"){YearOnWater(state,nav)}
            composable("water/{id}"){WaterPassport(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("species/{id}"){SpeciesDetail(state,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("catch/{id}"){CatchDetail(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}
        }
    }
}
// The log button is docked into the bar rather than floated over the content: a centre-docked FAB sat on top of the
// Vault's chip row and the Log screen's Save button (TB-A-04, TB-A-12). saveState/restoreState keep each tab's
// scroll position and back stack across tab switches.
@Composable fun BottomBar(nav:NavHostController,showFab:Boolean){val back by nav.currentBackStackEntryAsState();Box{NavigationBar(containerColor=Surface){tabs.forEachIndexed{i,t->if(i==2)Spacer(Modifier.weight(.65f));NavigationBarItem(selected=back?.destination?.route==t.route,onClick={nav.navigate(t.route){popUpTo("vault"){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(t.icon,null)},label={Text(t.label,fontSize=10.sp,fontWeight=FontWeight.SemiBold)},modifier=Modifier.testTag("tab_${t.route}"),
            // No selection pill and brassSoft on the selected item, as iOS does it — Material's filled indicator
            // capsule put a shape behind one tab that has no counterpart on the other platform (TB-P-10).
            colors=NavigationBarItemDefaults.colors(selectedIconColor=BrassSoft,selectedTextColor=BrassSoft,unselectedIconColor=Muted.copy(alpha=.6f),unselectedTextColor=Muted.copy(alpha=.6f),indicatorColor=Color.Transparent))}};if(showFab)FloatingActionButton(onClick={nav.navigate("log")},containerColor=Brass,contentColor=Background,shape=CircleShape,modifier=Modifier.align(Alignment.Center).testTag("logCatchFab")){Icon(Icons.Default.Add,"Log a catch")}}}

// safeDrawingPadding keeps the wordmark clear of the status bar; without it the header collided with the clock (TB-A-01).
/**
 * Onboarding, matching iOS page for page (TB-P-06).
 *
 * The two apps used to share only a brand here: Android was left-aligned with no icon, no page indicator and no
 * way to skip, under different titles on every page. This is the iOS flow — shield hero, brass eyebrow above a
 * serif title, centred, dots, Skip — with Android's genuinely better last page kept and now on both platforms:
 * the angler chooses whether to start with sample waters or an empty vault.
 *
 * The upfront location request is gone. Its result was discarded (the callback was empty) and DeviceLocation only
 * ever *checks* the permission, so the button was the sole place Android could obtain it. It is now asked in
 * context, on the Bite windows screen, at the moment local times would actually change — which is where iOS asks,
 * and converts far better than a cold prompt before the app has shown anything.
 */
@Composable fun Onboarding(vm:MainViewModel){
    val pager=rememberPagerState(pageCount={3});val scope=rememberCoroutineScope()
    BackHandler(enabled=pager.currentPage>0){scope.launch{pager.animateScrollToPage(pager.currentPage-1)}}
    Column(Modifier.fillMaxSize().safeDrawingPadding()){
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
            Text("TACKLEBOX",color=Brass,style=MaterialTheme.typography.labelLarge,letterSpacing=2.sp)
            Spacer(Modifier.weight(1f))
            // Skipping means an empty vault. It must never quietly hand someone content they did not ask for, and
            // empty is the recoverable option: waters can be added, invented ones have to be found and deleted.
            TextButton(onClick={vm.seed(false)}){Text("Skip",color=Muted,fontWeight=FontWeight.SemiBold)}}
        HorizontalPager(pager,Modifier.weight(1f)){page->when(page){
            0->OnboardingPage(Icons.Default.Shield,"YOUR PRIVATE JOURNAL","Welcome to Tacklebox","A quiet place for every catch, water and session — your fishing life, kept together.")
            1->OnboardingPage(null,"MAKE IT YOURS","Set up your vault","Choose how weights and lengths appear. You can change this any time in Settings."){
                val state by vm.state.collectAsStateWithLifecycle()
                SingleChoiceSegmentedButtonRow{UnitSystem.entries.forEachIndexed{i,u->SegmentedButton(state.settings.unitSystem==u,{vm.settings(state.settings.copy(unitSystem=u))},SegmentedButtonDefaults.itemShape(i,2),colors=SegmentedButtonDefaults.colors(activeContainerColor=Brass,activeContentColor=Background,inactiveContainerColor=Inset,inactiveContentColor=Ink),icon={}){Text(u.name.lowercase().replaceFirstChar(Char::uppercase))}}}}
            else->OnboardingPage(null,"PRIVATE BY DESIGN","Useful context, kept discreet","We'll ask only when a feature needs access."){
                HeritageCard{
                    PrivacyRow(Icons.Default.NearMe,"Location with restraint","Used for weather, tides and bite times. Your precise spot is never stored.")
                    HorizontalDivider(Modifier.padding(vertical=18.dp),color=Muted.copy(alpha=.2f))
                    PrivacyRow(Icons.Default.Photo,"Photos stay with you","Catch photos stay on your device as part of your private vault.")}}}}
        Row(Modifier.fillMaxWidth().padding(bottom=20.dp),horizontalArrangement=Arrangement.Center){
            repeat(3){i->Box(Modifier.padding(horizontal=4.dp).size(width=if(i==pager.currentPage)24.dp else 8.dp,height=8.dp).background(if(i==pager.currentPage)Brass else Inset,RoundedCornerShape(4.dp)))}}
        Column(Modifier.padding(horizontal=24.dp).padding(bottom=24.dp)){
            if(pager.currentPage==2){
                Button(onClick={vm.seed(true)},Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp)){Text("Begin with sample waters",fontWeight=FontWeight.Bold)}
                Spacer(Modifier.height(10.dp))
                TextButton(onClick={vm.seed(false)},Modifier.fillMaxWidth().height(44.dp)){Text("Start with an empty vault",color=BrassSoft,fontWeight=FontWeight.SemiBold)}}
            else Button(onClick={scope.launch{pager.animateScrollToPage(pager.currentPage+1)}},Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp)){Text("Continue",fontWeight=FontWeight.Bold)}}}}

/** One onboarding page: optional hero icon, brass eyebrow, serif title, muted body, then anything the page adds. */
@Composable private fun OnboardingPage(icon:androidx.compose.ui.graphics.vector.ImageVector?,eyebrow:String,title:String,message:String,extra:@Composable ColumnScope.()->Unit={}){
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        icon?.let{Box(Modifier.size(112.dp).background(Teal.copy(alpha=.14f),CircleShape),contentAlignment=Alignment.Center){Icon(it,null,tint=Teal,modifier=Modifier.size(46.dp))};Spacer(Modifier.height(22.dp))}
        Text(eyebrow,color=Brass,style=MaterialTheme.typography.labelLarge,letterSpacing=1.7.sp,textAlign=TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(title,style=MaterialTheme.typography.headlineLarge,textAlign=TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message,color=Muted,style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        extra()}}

@Composable private fun PrivacyRow(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,message:String){
    Row(verticalAlignment=Alignment.Top){
        Icon(icon,null,tint=Teal,modifier=Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Column{Text(title,fontWeight=FontWeight.SemiBold);Text(message,color=Muted,style=MaterialTheme.typography.bodyMedium)}}}

/**
 * A top-level screen: a brass uppercase eyebrow above a serif title, as iOS's `ScreenHeader` has always been.
 *
 * Android put a serif title above a sentence-case subtitle instead, so the two apps used different heading systems
 * on every screen — the same information, arranged the other way up (TB-P-08).
 */
@Composable fun Screen(eyebrow:String,title:String,actions:@Composable RowScope.()->Unit={},content:LazyListScope.()->Unit){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,top=18.dp,end=18.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(eyebrow.uppercase(),color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
                Spacer(Modifier.height(4.dp))
                Text(title,style=MaterialTheme.typography.displaySmall,maxLines=2)}
            actions()}}
        content()}
}

/**
 * A screen reached from another, rather than from the tab bar: a back chevron and a centred inline title.
 *
 * iOS pushes Catches, Bite windows, Year on the Water and Settings with a `navigationTitle`, which renders exactly
 * this way. Android showed them with the same large header as a tab root, so a pushed screen and a top-level one
 * were indistinguishable (TB-P-08).
 */
@Composable fun PushedScreen(title:String,onBack:()->Unit,actions:@Composable RowScope.()->Unit={},content:LazyListScope.()->Unit){
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onBack,Modifier.testTag("back")){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=BrassSoft)}
            Spacer(Modifier.weight(1f))
            Text(title,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row{actions()}
            Spacer(Modifier.width(4.dp))}
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,top=4.dp,end=18.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)}
}
@Composable fun HeritageCard(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit){Card(modifier=modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Surface),shape=RoundedCornerShape(20.dp),onClick=onClick?:{}){Column(Modifier.padding(18.dp),content=content)}}
// Matches the iOS StatTile: a serif value in ink over an uppercase letterspaced label. The label was sentence
// case and the value brass, so the same three tiles read as a different component on each platform (TB-P-09).
@Composable fun Stat(value:String,label:String,modifier:Modifier=Modifier){
    Column(modifier.background(Inset,RoundedCornerShape(14.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        // Two lines, not one. iOS shrinks the value to fit (minimumScaleFactor 0.7); Compose clips instead, so a
        // one-line tile turned "Waning Crescent" into "Waning" — the moon phase, silently halved.
        Text(value,style=MaterialTheme.typography.headlineMedium,maxLines=2)
        Text(label.uppercase(),color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp)}}
// Rounds ounces before splitting, so 15.6 oz reads "1 lb 0 oz" rather than "0 lb 16 oz" (TB-A-13).
fun Double.weight(unit:UnitSystem)=if(unit==UnitSystem.METRIC) if(this>=1000)"%.2f kg".format(this/1000) else "%.0f g".format(this) else Weights.toPoundsAndOunces(this).let{(lb,oz)->"$lb lb $oz oz"}
val numberKeyboard=KeyboardOptions(keyboardType=KeyboardType.Decimal)
// LocalTime.toString() prints seconds when they are non-zero, so a computed sunrise read "06:43:12" while the
// hardcoded old one read "06:43". The ephemeris returns real times, so they always have seconds.
//
// Localised rather than a fixed HH:mm, which is what iOS does: a US device should read "9:40 AM" and a UK one
// "09:40". A hardcoded pattern had the two apps printing the same instant differently on the same phone (TB-P-05).
fun java.time.LocalTime.hm():String=format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
/** An uppercase letterspaced section label, matching the iOS capture screen (TB-P-04). */
@Composable fun SectionLabel(text:String)=Text(text.uppercase(),color=Muted,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)

/**
 * A number, its unit, and a minus/plus pair — the iOS weight and length control (TB-P-04).
 *
 * Free-text entry and a stepper are different interactions with different error modes, and the step size is a
 * design decision a text field cannot express: grams move in tens, because at fifty a metric angler could not
 * enter most real weights. The value shrinks rather than wraps, which is what TB-I-03 was on iOS.
 */
@Composable fun ValueStepper(title:String,value:Int,range:IntRange,step:Int=1,modifier:Modifier=Modifier,onChange:(Int)->Unit){
    HeritageCard(modifier){Row(verticalAlignment=Alignment.CenterVertically){
        Row(Modifier.weight(1f),verticalAlignment=Alignment.Bottom){
            Text("$value",style=MaterialTheme.typography.headlineLarge,maxLines=1,overflow=TextOverflow.Visible,modifier=Modifier.testTag("stepper_$title"))
            Spacer(Modifier.width(4.dp))
            Text(title,color=Muted,style=MaterialTheme.typography.labelLarge)}
        Row(Modifier.background(Inset,RoundedCornerShape(10.dp)),verticalAlignment=Alignment.CenterVertically){
            IconButton({onChange((value-step).coerceIn(range.first,range.last))},Modifier.testTag("minus_$title"),enabled=value>range.first){Icon(Icons.Default.Remove,"Less $title",tint=Ink)}
            Box(Modifier.width(1.dp).height(22.dp).background(Muted.copy(alpha=.3f)))
            IconButton({onChange((value+step).coerceIn(range.first,range.last))},Modifier.testTag("plus_$title"),enabled=value<range.last){Icon(Icons.Default.Add,"More $title",tint=Ink)}}}}
}

/**
 * The one-line conditions summary, in the same order and units as iOS's `ConditionsMetrics.summary`.
 * Android captured these silently; the angler could not see what was being stamped on the fish (TB-P-04).
 */
fun ConditionsSnapshot.summary(unit:UnitSystem):String{
    fun whole(v:Double)=v.roundToInt().toString()
    val temperature=airTempC?.let{if(unit==UnitSystem.METRIC)"${whole(it)}°C" else "${whole(it*9/5+32)}°F"}
    val wind=windSpeedKph?.let{"${windDirection.orEmpty()} ${if(unit==UnitSystem.METRIC) whole(it)+" km/h" else whole(it/1.609344)+" mph"}".trim()}
    val pressure=pressureHpa?.let{if(unit==UnitSystem.METRIC)"${whole(it)} hPa" else String.format("%.2f inHg",it*0.0295299830714)}
    val moon=moonPhase?.let{"$it moon"}
    return listOfNotNull(temperature,wind,pressure,moon).joinToString("  ·  ")
}

/** The day rating, shown exactly as iOS shows it: the word, uppercased, in a coloured pill (TB-P-05). */
@Composable fun RatingPill(rating:SolunarRating){val colour=when(rating){SolunarRating.EXCELLENT->BrassSoft;SolunarRating.GOOD->Teal;else->Muted}
    Text(rating.title.uppercase(),color=Background,style=MaterialTheme.typography.labelLarge,modifier=Modifier.background(colour,RoundedCornerShape(14.dp)).padding(horizontal=10.dp,vertical=5.dp))}
// Selected chips and segmented buttons defaulted to the Material purple container rather than the brand brass (TB-A-19).
// Chips are capsules, as they are on iOS: Material's default is an 8dp rounded rectangle, which read as a
// different component sitting next to the same content (TB-P-04).
@Composable fun brassChipColours()=FilterChipDefaults.filterChipColors(selectedContainerColor=Brass,selectedLabelColor=Background,labelColor=Ink,containerColor=Inset)
fun Instant.pretty():String=atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm"))

@Composable fun Vault(s:AppState,vm:MainViewModel,nav:NavHostController){
    val pb=s.catches.filter{it.item.weightGrams!=null}.maxByOrNull{it.item.weightGrams!!}
    // The card used to call Astronomy.calculate() with its default coordinates, so it showed central-UK times even
    // with location granted — and disagreed with the Bite windows screen, which has always followed the device.
    var place by remember{mutableStateOf<Pair<Double,Double>?>(null)}
    LaunchedEffect(Unit){place=vm.solunarPlace()}
    val located=place!=null&&place!=DeviceLocation.FALLBACK_INLAND
    val sol=place?.let{Astronomy.calculate(latitude=it.first,longitude=it.second)}?:Astronomy.calculate()
    Screen("Your fishing life","The Vault",// The same three destinations iOS puts in its toolbar, in the same order (TB-P-07). My Tacklebox lived in an
        // in-content chip here, so the same place was reached from structurally different parts of the screen.
        actions={IconButton({nav.navigate("catches")},modifier=Modifier.testTag("searchCatches")){Icon(Icons.Outlined.Search,"Search your catches",tint=BrassSoft)};IconButton({nav.navigate("tackle")}){Icon(Icons.Outlined.Inventory2,"My Tacklebox",tint=BrassSoft)};IconButton({nav.navigate("settings")}){Icon(Icons.Outlined.MoreHoriz,"Settings",tint=BrassSoft)}}){
        // An empty vault gets the empty state, not a hero card with nothing in it — which is what iOS does, and
        // what makes the first screen say what to do rather than showing an ornament (TB-P-12).
        item{if(s.catches.isEmpty())Empty("Your vault is empty","Tap + to log your first catch and begin your private record.")
             else FeaturedPersonalBest(pb,s.settings.unitSystem,onClick=pb?.let{{nav.navigate("catch/${it.item.id}")}})}
        item{TodayOnTheBank(sol,located,onClick={nav.navigate("solunar")})}
        // Stats and the board only once there is something to count, as iOS does — an empty vault showed three
        // zeroes and a PB BOARD heading with nothing under it (TB-P-12).
        if(s.catches.isNotEmpty()){
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${s.catches.size}","fish landed",Modifier.weight(1f).clickable{nav.navigate("catches")});Stat("${s.catches.mapNotNull{it.species?.id}.distinct().size}","species",Modifier.weight(1f));Stat("${s.waters.size}","waters",Modifier.weight(1f))}}
            item{SectionLabel("PB board")}}
        items(s.catches.filter{it.item.weightGrams!=null}.groupBy{it.species?.id}.mapNotNull{(_,v)->v.maxByOrNull{it.item.weightGrams?:0.0}}){c->HeritageCard(onClick={nav.navigate("species/${c.species?.id}")}){Row{Text(c.species?.name?:"Unknown",Modifier.weight(1f));Text(c.item.weightGrams?.weight(s.settings.unitSystem).orEmpty(),color=BrassSoft)}}}
}
}

/**
 * The featured personal best (TB-P-05).
 *
 * This was a flat text card against an illustrated hero on iOS — the same record, presented as a list row on one
 * platform and as the centrepiece of the app on the other. It is now the iOS card: a 230dp hero showing the
 * catch's own photo where there is one, falling back to the carp glyph on a teal gradient, with a scrim so the
 * weight stays legible over any photo.
 */
@Composable fun FeaturedPersonalBest(pb:CatchRow?,unit:UnitSystem,onClick:(()->Unit)?){
    Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp))
        .border(1.dp,Muted.copy(alpha=.15f),RoundedCornerShape(18.dp))
        .then(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier)){
        val photo=pb?.allPhotoUris?.firstOrNull()
        if(photo!=null)AsyncImage(photo,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Inset,Teal.copy(alpha=.3f)))),contentAlignment=Alignment.Center){
            FishGlyph(Teal.copy(alpha=.82f),Modifier.size(210.dp,115.dp))}
        // Scrim: the weight has to read over a photo as well as over the gradient.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Background.copy(alpha=.92f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)){
            Text("FEATURED PERSONAL BEST",color=Brass,style=MaterialTheme.typography.labelLarge,letterSpacing=1.3.sp)
            Spacer(Modifier.height(4.dp))
            Text(pb?.item?.weightGrams?.weight(unit)?:"Log your first catch",style=MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(pb?.species?.name?:"Your finest catch awaits",color=Ink.copy(alpha=.88f),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                if(pb!=null)Icon(Icons.Default.ChevronRight,null,tint=BrassSoft)}}}
}

/**
 * Today on the bank (TB-P-05).
 *
 * Android showed the phase and one compact line of times. iOS leads with the next window and how long until it
 * starts, which is the only part an angler acts on — the sun times are context beneath it, not the headline.
 */
@Composable fun TodayOnTheBank(sol:SolunarDay,located:Boolean,onClick:()->Unit){
    val now=LocalTime.now()
    val next=sol.windows.firstOrNull{!it.end.isBefore(now)}
    HeritageCard(onClick=onClick){
        Row(verticalAlignment=Alignment.CenterVertically){
            Text("TODAY ON THE BANK",color=Muted,style=MaterialTheme.typography.labelLarge,letterSpacing=1.5.sp,modifier=Modifier.weight(1f))
            RatingPill(sol.rating)}
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.NightsStay,null,tint=BrassSoft,modifier=Modifier.size(23.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                if(next!=null){
                    Text("Next ${if(next.major)"Major" else "Minor"} · ${next.start.hm()}",style=MaterialTheme.typography.titleLarge)
                    Text(countdownTo(now,next.start),color=Teal,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                }else Text("Today's windows have passed",style=MaterialTheme.typography.titleLarge)}
            Icon(Icons.Default.ChevronRight,null,tint=BrassSoft)}
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
            SunEvent(Icons.Default.WbTwilight,"Sunrise",sol.sunrise)
            SunEvent(Icons.Default.WbSunny,"Sunset",sol.sunset)}
        // Say so when the times are the central-UK fallback rather than the angler's own, as iOS does.
        if(!located){Spacer(Modifier.height(6.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.LocationOff,null,tint=Muted.copy(alpha=.7f),modifier=Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text("Approximate · enable Location for local times",color=Muted.copy(alpha=.7f),style=MaterialTheme.typography.bodyMedium)}}}
}

@Composable private fun SunEvent(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,at:LocalTime){
    Row(verticalAlignment=Alignment.CenterVertically){
        Icon(icon,null,tint=Muted,modifier=Modifier.size(14.dp));Spacer(Modifier.width(5.dp))
        Text("$label ${at.hm()}",color=Muted,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)}
}

/** "in 4m", "in 2h 31m", or "Happening now" — the same wording as iOS. */
fun countdownTo(now:LocalTime,start:LocalTime):String{
    val minutes=java.time.Duration.between(now,start).toMinutes()
    if(minutes<=0)return "Happening now"
    return if(minutes>=60)"in ${minutes/60}h ${minutes%60}m" else "in ${maxOf(1,minutes)}m"
}

@Composable fun Waters(s:AppState,vm:MainViewModel,nav:NavHostController){
    var adding by rememberSaveable{mutableStateOf(false)}
    Screen("Private places","Waters",actions={IconButton({adding=true}){Icon(Icons.Default.Add,"Add a water")}}){
        item{HeritageCard(onClick={nav.navigate("rivers")}){Text("River conditions",style=MaterialTheme.typography.titleLarge);Text("Levels and nearby gauges",color=Muted)}}
        item{HeritageCard(onClick={nav.navigate("tides")}){Text("Tides & sea",style=MaterialTheme.typography.titleLarge);Text("Coastal waves and sea state",color=Muted)}}
        item{SectionLabel("Water passports")}
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
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(WaterType.entries.toList()){t->FilterChip(type==t,{type=t},{Text(t.name.lowercase().replaceFirstChar(Char::uppercase))},colors=brassChipColours(),shape=CircleShape)}}}},
        confirmButton={TextButton({if(name.isNotBlank())onSave(name.trim(),type,region.trim())},enabled=true){Text("Save")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

// Swim notes are editable here, matching iOS; they were read-only while the card invited the angler to keep them.
@Composable fun WaterPassport(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val w=s.waters.firstOrNull{it.id==id};val catches=s.catches.filter{it.water?.id==id}
    var notes by remember(w?.id){mutableStateOf(w?.swimNotes.orEmpty())}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    Screen("Water passport",w?.name?:"Water passport"){
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${catches.size}","catches",Modifier.weight(1f));Stat("${catches.mapNotNull{it.species}.distinctBy{it.id}.size}","species",Modifier.weight(1f))}}
        item{HeritageCard{Text("Private swim notes",color=Brass)
            OutlinedTextField(notes,{notes=it;w?.let{water->vm.updateWater(water.copy(swimNotes=it))}},placeholder={Text("Reeds on the west bank fish well at dusk…")},modifier=Modifier.fillMaxWidth())}}
        item{SectionLabel("Most productive");Text(catches.groupingBy{it.species?.name?:"Unknown"}.eachCount().maxByOrNull{it.value}?.key?:"Log catches here to reveal patterns",color=Muted)}
        item{TextButton({confirmDelete=true},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete this water")}}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete ${w?.name.orEmpty()}?")},
        text={Text("The water is removed. Catches and sessions logged here are kept, but they will no longer name a water.")},
        confirmButton={TextButton({id?.let{vm.deleteWater(it)};confirmDelete=false;nav.popBackStack()}){Text("Delete")}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

@Composable fun Sessions(s:AppState,vm:MainViewModel){var selected by rememberSaveable{mutableStateOf<Long?>(s.waters.firstOrNull()?.id)};val active=s.sessions.firstOrNull{it.item.endAt==null};Screen("Time on the bank","Sessions"){item{HeritageCard{if(active==null){Text("Start a session",style=MaterialTheme.typography.titleLarge);s.waters.forEach{FilterChip(selected==it.id,{selected=it.id},{Text(it.name)},colors=brassChipColours(),shape=CircleShape)};Button({vm.startSession(selected)},enabled=s.waters.isNotEmpty()){Text("Start fishing")}}else{Text("Session in progress",color=Teal);Text(active.water?.name?:"Unspecified water",style=MaterialTheme.typography.headlineMedium);Button({vm.stopSession(active.item.id)}){Text("Finish session")}}}};items(s.sessions){x->HeritageCard{Text(x.water?.name?:"Unspecified water",style=MaterialTheme.typography.titleLarge);Text("${x.item.startAt.pretty()} · ${x.catches.size} ${if(x.catches.size==1)"catch" else "catches"}",color=Muted);Text(if(x.item.endAt==null)"LIVE" else "Finished",color=if(x.item.endAt==null)Teal else Brass)}}}}

@Composable fun Insights(s:AppState,nav:NavHostController){val total=s.catches.mapNotNull{it.item.weightGrams}.sum();Screen("Patterns from the bank","Insights"){item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${s.catches.size}","landed",Modifier.weight(1f));Stat(total.weight(s.settings.unitSystem),"total weight",Modifier.weight(1f))}};item{HeritageCard(onClick={nav.navigate("year")}){Text("YEAR ON THE WATER",color=Brass);Text("Your season, distilled",style=MaterialTheme.typography.headlineMedium);Text("Open shareable summary →",color=Muted)}};item{Breakdown("Catches over time",s.catches.groupingBy{it.item.caughtAt.atZone(ZoneId.systemDefault()).month.name.take(3)}.eachCount())};item{Breakdown("Species",s.catches.groupingBy{it.species?.name?:"Unknown"}.eachCount())};item{Breakdown("Waters",s.catches.groupingBy{it.water?.name?:"Unspecified"}.eachCount())};item{HeritageCard{Text("Conditions insight",style=MaterialTheme.typography.titleLarge);Text(Insight.conditions(s.catches),color=Muted)}}}}
@Composable fun Breakdown(title:String,data:Map<String,Int>){HeritageCard{Text(title,style=MaterialTheme.typography.titleLarge);if(data.isEmpty())Text("Not enough data yet",color=Muted) else data.entries.sortedByDescending{it.value}.take(5).forEach{Row(Modifier.padding(top=8.dp)){Text(it.key,Modifier.weight(1f));Text("${it.value}",color=Brass)}}}}
@Composable fun YearOnWater(s:AppState,nav:NavHostController){val year=Year.now().value;val catches=s.catches.filter{it.item.caughtAt.atZone(ZoneId.systemDefault()).year==year};val biggest=catches.maxByOrNull{it.item.weightGrams?:0.0};val hours=s.sessions.filter{it.item.endAt!=null}.sumOf{Duration.between(it.item.startAt,it.item.endAt!!).toMinutes()}/60;PushedScreen("Year on the Water",onBack={nav.popBackStack()}){item{Card(colors=CardDefaults.cardColors(containerColor=Inset),shape=RoundedCornerShape(28.dp)){Column(Modifier.padding(26.dp)){Text("TACKLEBOX · $year",color=Brass);Text("${catches.size}",style=MaterialTheme.typography.displaySmall);Text("fish landed",color=Muted);HorizontalDivider(Modifier.padding(vertical=16.dp));Text("${catches.mapNotNull{it.item.weightGrams}.sum().weight(s.settings.unitSystem)} carried gently");Text("${biggest?.species?.name?:"No biggest fish yet"} · ${biggest?.item?.weightGrams?.weight(s.settings.unitSystem).orEmpty()}");Text("$hours hours on the bank");Text("Top bait · ${catches.groupingBy{it.item.bait?:"Unrecorded"}.eachCount().maxByOrNull{it.value}?.key?:"—"}",color=BrassSoft)}}};item{val context=LocalContext.current;Button({ShareSheet.season(context,s,year)},Modifier.fillMaxWidth().testTag("shareSeason")){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Share summary")}}}}

/**
 * The capture screen. Previously it could not record a water (the waterId argument was hardcoded null and there was
 * no picker), never linked the catch to the open session, entered weight in ounces but displayed pounds-and-ounces,
 * lost everything on rotation, and offered an "Identify from photo" button that did nothing (TB-A-02, TB-A-03,
 * TB-A-13, TB-A-15, TB-A-07).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun LogCatch(s:AppState,vm:MainViewModel,nav:NavHostController){
    val metric=s.settings.unitSystem==UnitSystem.METRIC
    val openSession=s.sessions.firstOrNull{it.item.endAt==null}
    // rememberSaveable throughout: the activity is recreated on rotation and none of this survived it (TB-A-15).
    var species by rememberSaveable{mutableStateOf<Long?>(null)}
    var kilograms by rememberSaveable{mutableIntStateOf(0)}; var grams by rememberSaveable{mutableIntStateOf(0)}
    var pounds by rememberSaveable{mutableIntStateOf(0)}; var ounces by rememberSaveable{mutableIntStateOf(0)}
    var centimetres by rememberSaveable{mutableIntStateOf(0)}; var inches by rememberSaveable{mutableIntStateOf(0)}
    var rig by rememberSaveable{mutableStateOf("")}; var bait by rememberSaveable{mutableStateOf("")}
    var returned by rememberSaveable{mutableStateOf(true)}
    var notes by rememberSaveable{mutableStateOf("")}
    var caughtAt by rememberSaveable{mutableStateOf(System.currentTimeMillis())}
    var photos by rememberSaveable{mutableStateOf(listOf<String>())}
    var water by rememberSaveable{mutableStateOf(openSession?.water?.id)}
    var newSpecies by rememberSaveable{mutableStateOf("")}; var addingSpecies by rememberSaveable{mutableStateOf(false)}
    var addingPreset by rememberSaveable{mutableStateOf<PresetKind?>(null)}; var newPreset by rememberSaveable{mutableStateOf("")}
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()

    val enteredGrams:Double=if(metric)(kilograms*1000+grams).toDouble() else (Weights.fromPoundsAndOunces(pounds.toString(),ounces.toString())?:0.0)
    val lengthCm:Double=if(metric)centimetres.toDouble() else inches*2.54
    val previousBest=species?.let{id->s.catches.filter{it.species?.id==id}.mapNotNull{it.item.weightGrams}.maxOrNull()}
    val isNewPB=enteredGrams>0 && previousBest!=null && enteredGrams>previousBest
    val isFirstOfSpecies=enteredGrams>0 && species!=null && previousBest==null

    // Read once when the screen opens, and shown, so the angler can see what will be stamped on the fish and retry
    // a failed reading before saving. Android captured this silently at save time (TB-P-04).
    var stamped by remember{mutableStateOf<ConditionsSnapshot?>(null)}
    var conditionsAttempt by remember{mutableIntStateOf(0)}
    var reading by remember{mutableStateOf(true)}
    LaunchedEffect(conditionsAttempt){reading=true;stamped=runCatching{vm.captureConditions()}.getOrNull();reading=false}

    CaptureScaffold("Log a Catch",onCancel={nav.popBackStack()}){
        item{PhotoStrip(photos){photos=it}}
        item{SectionLabel("Species")
            FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                s.species.forEach{sp->FilterChip(species==sp.id,{species=sp.id},{Text(sp.name)},colors=brassChipColours(),shape=CircleShape)}
                FilterChip(false,{addingSpecies=true},{Text("＋ Add species")},colors=brassChipColours(),shape=CircleShape)}}
        item{SpeciesIdRow(s,vm,photos.firstOrNull(),suggestions,onPick={name->s.species.firstOrNull{it.name.equals(name,true)}?.let{species=it.id} ?: vm.addSpecies(name);vm.clearSuggestions()})}
        item{SectionLabel("Weight")
            Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                if(metric){
                    ValueStepper("KG",kilograms,0..100,modifier=Modifier.weight(1f)){kilograms=it}
                    // 10 g steps: at 50 g a metric angler could not enter most real weights (TB-I-09 on iOS).
                    ValueStepper("G",grams,0..990,10,Modifier.weight(1f)){grams=it}
                }else{
                    ValueStepper("LB",pounds,0..200,modifier=Modifier.weight(1f)){pounds=it}
                    ValueStepper("OZ",ounces,0..15,modifier=Modifier.weight(1f)){ounces=it}}}}
        if(isNewPB)item{Banner("NEW PERSONAL BEST",Icons.Default.AutoAwesome,BrassSoft,"pbBanner")}
        else if(isFirstOfSpecies)item{Banner("YOUR FIRST ${s.species.firstOrNull{it.id==species}?.name?.uppercase().orEmpty()}",Icons.Default.Star,Teal,"firstOfSpeciesBanner")}
        item{SectionLabel("Length")
            Row(Modifier.padding(top=10.dp)){
                if(metric)ValueStepper("CM",centimetres,0..300,modifier=Modifier.weight(1f)){centimetres=it}
                else ValueStepper("IN",inches,0..120,modifier=Modifier.weight(1f)){inches=it}}}
        item{HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Returned",fontWeight=FontWeight.SemiBold);Text(if(returned)"Put back in the water" else "Kept",color=Muted,style=MaterialTheme.typography.bodyMedium)}
            Switch(returned,{returned=it})}}}
        item{SectionLabel("Water")
            if(s.waters.isEmpty())Text("Add a water on the Waters tab to record where this fish came from.",color=Muted,modifier=Modifier.padding(top=10.dp))
            else FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                s.waters.forEach{w->FilterChip(water==w.id,{water=if(water==w.id)null else w.id},{Text(w.name)},colors=brassChipColours(),shape=CircleShape)}}}
        item{SectionLabel("When");Box(Modifier.padding(top=10.dp)){CaughtAtField(caughtAt){caughtAt=it}}}
        item{PresetChips("Rig",rig,s.presets.filter{it.kind==PresetKind.RIG}.map{it.name},onPick={rig=it},onAdd={newPreset="";addingPreset=PresetKind.RIG})}
        item{PresetChips("Bait",bait,s.presets.filter{it.kind==PresetKind.BAIT}.map{it.name},onPick={bait=it},onAdd={newPreset="";addingPreset=PresetKind.BAIT})}
        // Sessions, waters and gear could all carry a note; the catch could not, so the story behind a fish — the
        // thing that makes a journal worth keeping — had nowhere to go.
        item{SectionLabel("Notes")
            OutlinedTextField(notes,{notes=it},placeholder={Text("Took it on the drop, margin swim, three hours in…")},
                minLines=3,modifier=Modifier.fillMaxWidth().padding(top=10.dp).testTag("catchNotes"))}
        item{ConditionsCard(reading,stamped,s.settings.unitSystem){conditionsAttempt++}}
        if(openSession!=null)item{Text("Will be added to your open session at ${openSession.water?.name?:"an unspecified water"}.",color=Muted,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.Lock,null,tint=Muted,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp))
            Text("Your spot stays private",color=Muted,style=MaterialTheme.typography.bodyMedium)}}
        item{Button(onClick={
                vm.addCatch(species,enteredGrams.takeIf{it>0},lengthCm.takeIf{it>0},rig,bait,returned,water,photos,notes,Instant.ofEpochMilli(caughtAt),stamped){nav.navigate("catch/$it"){popUpTo("vault")}}
            },modifier=Modifier.fillMaxWidth().height(52.dp).testTag("saveCatch"),shape=RoundedCornerShape(14.dp),
            enabled=species!=null&&enteredGrams>0){Text("Save to the Vault",fontWeight=FontWeight.Bold)}}
    }
    if(addingSpecies)AlertDialog(onDismissRequest={addingSpecies=false},title={Text("Add a species")},
        text={OutlinedTextField(newSpecies,{newSpecies=it},label={Text("Name")},singleLine=true)},
        confirmButton={TextButton({if(newSpecies.isNotBlank()){vm.addSpecies(newSpecies.trim());newSpecies="";addingSpecies=false}}){Text("Add")}},
        dismissButton={TextButton({addingSpecies=false}){Text("Cancel")}})
    addingPreset?.let{kind->AlertDialog(onDismissRequest={addingPreset=null},title={Text("Add ${kind.name.lowercase()}")},
        text={Column{OutlinedTextField(newPreset,{newPreset=it},label={Text("Name")},singleLine=true);Spacer(Modifier.height(8.dp));Text("Save it to My Tacklebox and select it for this catch.",color=Muted,style=MaterialTheme.typography.bodyMedium)}},
        confirmButton={TextButton({if(newPreset.isNotBlank()){vm.addPreset(newPreset.trim(),kind);if(kind==PresetKind.RIG)rig=newPreset.trim() else bait=newPreset.trim();newPreset="";addingPreset=null}}){Text("Add")}},
        dismissButton={TextButton({addingPreset=null}){Text("Cancel")}})}
}

/**
 * The capture screen's frame: a Cancel action and a centred inline title, with no tab bar behind it.
 *
 * iOS presents Log a Catch as a modal sheet. Android had it as an ordinary tab destination with the bottom bar
 * still showing, so the same task looked like a different kind of thing on each platform (TB-P-04).
 */
@Composable fun CaptureScaffold(title:String,onCancel:()->Unit,content:LazyListScope.()->Unit){
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onCancel){Text("Cancel",color=BrassSoft,fontWeight=FontWeight.SemiBold)}
            Spacer(Modifier.weight(1f));Text(title,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))}
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp),content=content)}
}

/** A record or a first, marked on the form rather than celebrated after the fact. */
@Composable fun Banner(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector,colour:Color,tag:String){
    Row(Modifier.fillMaxWidth().background(colour,RoundedCornerShape(12.dp)).padding(12.dp).testTag(tag),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
        Icon(icon,null,tint=Background,modifier=Modifier.size(16.dp));Spacer(Modifier.width(8.dp))
        Text(text,color=Background,style=MaterialTheme.typography.labelLarge,letterSpacing=1.sp)}
}

/** What is about to be stamped on the fish, and a way to try again when the reading failed. */
@Composable fun ConditionsCard(reading:Boolean,stamped:ConditionsSnapshot?,unit:UnitSystem,onRetry:()->Unit){
    HeritageCard{
        Row(verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Default.Air,null,tint=Brass,modifier=Modifier.size(15.dp));Spacer(Modifier.width(6.dp))
            Text("AUTO-STAMPED CONDITIONS",color=Brass,style=MaterialTheme.typography.labelLarge,letterSpacing=1.3.sp)}
        Spacer(Modifier.height(8.dp))
        when{
            reading->Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(16.dp),color=BrassSoft,strokeWidth=2.dp);Spacer(Modifier.width(9.dp));Text("Reading conditions…",color=Muted)}
            stamped?.airTempC==null&&stamped?.pressureHpa==null->{
                Text("Conditions unavailable — offline or location off",color=Muted)
                TextButton(onRetry,Modifier.testTag("retryConditions")){Icon(Icons.Default.Refresh,null,tint=BrassSoft,modifier=Modifier.size(15.dp));Spacer(Modifier.width(6.dp));Text("Retry",color=BrassSoft,fontWeight=FontWeight.SemiBold)}}
            else->Text(stamped!!.summary(unit),color=Ink)}}
}

/**
 * Rig and bait, as a grid of saved presets plus an Add.
 *
 * The free-text field is gone: iOS has never had one here, and a text box beside chips invites two spellings of
 * the same rig, which then split the Insights breakdown (TB-P-04). Anything new is added as a preset, so it is
 * there next time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun PresetChips(label:String,value:String,presets:List<String>,onPick:(String)->Unit,onAdd:()->Unit){
    SectionLabel(label)
    FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        presets.forEach{p->FilterChip(value==p,{onPick(if(value==p)"" else p)},{Text(p)},colors=brassChipColours(),shape=CircleShape)}
        FilterChip(false,onAdd,{Text("＋ Add")},colors=brassChipColours(),shape=CircleShape)}
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
    Screen("Ready for the bank","My Tacklebox"){
        item{HeritageCard{Text("Gear performance",color=Brass);Text("Best rig · ${s.catches.mapNotNull{it.item.rig}.filter{it.isNotBlank()}.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key?:"—"}");Text("Top bait · ${s.catches.mapNotNull{it.item.bait}.filter{it.isNotBlank()}.groupingBy{it}.eachCount().maxByOrNull{it.value}?.key?:"—"}",color=Muted)}}
        items(s.gear){g->HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(g.name,style=MaterialTheme.typography.titleLarge);Text(g.category.name.lowercase().replaceFirstChar(Char::uppercase),color=Muted)};IconButton({vm.deleteGear(g)}){Icon(Icons.Default.Delete,"Delete ${g.name}",tint=Muted)}}}}
        item{OutlinedTextField(name,{name=it},label={Text("New gear or preset")},modifier=Modifier.fillMaxWidth().testTag("gearName"))
            Text("Gear category",color=Muted,style=MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(GearCategory.entries.toList()){c->FilterChip(category==c,{category=c},{Text(c.name.lowercase().replaceFirstChar(Char::uppercase))},colors=brassChipColours(),shape=CircleShape)}}
            Row(Modifier.padding(top=8.dp)){Button({if(name.isNotBlank()){vm.addGear(name.trim(),category);name=""}}){Text("Add gear")}
                Spacer(Modifier.width(8.dp))
                OutlinedButton({if(name.isNotBlank()){vm.addPreset(name.trim(),kind);name=""}}){Text("Save ${kind.name.lowercase()}")}}
            LazyRow(Modifier.padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(PresetKind.entries.toList()){k->FilterChip(kind==k,{kind=k},{Text(k.name.lowercase().replaceFirstChar(Char::uppercase))},colors=brassChipColours(),shape=CircleShape)}}}
        item{SectionLabel("Quick picks");if(s.presets.isEmpty())Text("No saved rigs or baits",color=Muted)}
        items(s.presets){p->HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name);Text(p.kind.name.lowercase().replaceFirstChar(Char::uppercase),color=Muted,style=MaterialTheme.typography.bodyMedium)};IconButton({vm.deletePreset(p)}){Icon(Icons.Default.Delete,"Delete ${p.name}",tint=Muted)}}}}
    }
}

// Bite windows now follow the device rather than a hardcoded 52.5/-1.5 (TB-A-10), and say so when they cannot.
@Composable fun Solunar(vm:MainViewModel,nav:NavHostController){
    val context=LocalContext.current
    var place by remember{mutableStateOf<Pair<Double,Double>?>(null)}
    // Asking here rather than during onboarding. This is the screen where the permission visibly changes something
    // — the times stop being central UK and become the angler's own — so the prompt arrives with a reason attached.
    // `reload` re-runs the lookup once the choice is made, whichever way it goes.
    var reload by remember{mutableIntStateOf(0)}
    var canAsk by remember{mutableStateOf(!DeviceLocation.hasPermission(context))}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){canAsk=false;reload++}
    LaunchedEffect(reload){place=vm.solunarPlace()}
    val located=place!=null&&place!=DeviceLocation.FALLBACK_INLAND
    val d=place?.let{Astronomy.calculate(latitude=it.first,longitude=it.second)}?:Astronomy.calculate()
    PushedScreen("Bite windows",onBack={nav.popBackStack()}){
        item{Text(if(located)"Calculated on-device for your position" else "Calculated on-device · showing central UK",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        if(!located&&canAsk)item{HeritageCard(onClick={request.launch(Manifest.permission.ACCESS_COARSE_LOCATION)}){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocationOn,null,tint=Brass);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Use my location for local times",fontWeight=FontWeight.SemiBold);Text("Approximate only — your precise spot is never stored.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat(d.rating.title,"day rating",Modifier.weight(1f));Stat(d.moonPhase,"moon",Modifier.weight(1f))}};item{HeritageCard{Text("Sun & moon",style=MaterialTheme.typography.titleLarge);Text("Sunrise ${d.sunrise.hm()}  ·  Sunset ${d.sunset.hm()}",color=Muted);Text("Moonrise ${d.moonrise?.hm() ?: "—"}  ·  Moonset ${d.moonset?.hm() ?: "—"}",color=Muted)}};items(d.windows){w->HeritageCard{Row{Column(Modifier.weight(1f)){Text(w.label);Text(if(w.major)"Major feeding period" else "Minor feeding period",color=if(w.major)Brass else Muted)};Text("${w.start.hm()}–${w.end.hm()}")}}}}}
@Composable fun Tides(vm:MainViewModel){val live by vm.marine.collectAsStateWithLifecycle();LaunchedEffect(Unit){vm.marine()};Screen("Coastal outlook","Tides & sea"){item{when(val x=live){LiveState.Idle,LiveState.Loading->Loading();is LiveState.Error->ErrorCard(x.message){vm.marine()};is LiveState.Data->{val h=x.value.hourly;if(h==null||h.waveHeight.isEmpty())Empty("No coastal data","You may be inland or outside forecast coverage.") else HeritageCard{Text("Sea state",style=MaterialTheme.typography.headlineMedium);h.time.take(8).forEachIndexed{i,t->Row{Text(t.takeLast(5),Modifier.weight(1f));Text("${h.waveHeight.getOrNull(i)?:0.0} m · ${h.wavePeriod.getOrNull(i)?:0.0} s",color=Teal)}}}}}}}}
@Composable fun Rivers(vm:MainViewModel){val live by vm.river.collectAsStateWithLifecycle();LaunchedEffect(Unit){vm.river()};Screen("Live water","River conditions"){item{when(val x=live){LiveState.Idle,LiveState.Loading->Loading();is LiveState.Error->ErrorCard(x.message){vm.river()};is LiveState.Data->if(x.value.items.isEmpty())Empty("No nearby gauges","Try again nearer a gauged river.")else Column{SectionLabel("Nearby readings");x.value.items.take(8).forEach{HeritageCard{Text(it.value?.let{"$it m"}?:"Reading unavailable");Text(it.dateTime?:"Latest observation",color=Muted)}}}}}}}

@Composable fun SpeciesDetail(s:AppState,id:Long?,nav:NavHostController){val sp=s.species.firstOrNull{it.id==id};val catches=s.catches.filter{it.species?.id==id};Screen("Species record",sp?.name?:"Species record"){sp?.scientificName?.let{n->item{Text(n,color=Muted,fontStyle=androidx.compose.ui.text.font.FontStyle.Italic)}};item{Box(Modifier.fillMaxWidth().height(170.dp).background(Inset,RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.SetMeal,null,tint=Brass,modifier=Modifier.size(64.dp))}};item{Text(sp?.about?:"A personal record built from your catches.",color=Muted)};item{SectionLabel("Catch history")};items(catches){c->HeritageCard(onClick={nav.navigate("catch/${c.item.id}")}){Text(c.item.caughtAt.pretty());Text(c.item.weightGrams?.weight(s.settings.unitSystem)?:"Weight not recorded",color=Brass)}}}}
@Composable fun CatchDetail(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val c=s.catches.firstOrNull{it.item.id==id}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    PushedScreen("Catch",onBack={nav.popBackStack()}){
        item{Text(c?.species?.name?:"Catch detail",style=MaterialTheme.typography.displaySmall)
             c?.item?.caughtAt?.let{Text(it.pretty(),color=Muted)}}
        item{val gallery=c?.allPhotoUris.orEmpty()
            if(gallery.isNotEmpty())PhotoGallery(gallery)
            else Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp)).background(Inset),contentAlignment=Alignment.Center){Icon(Icons.Default.SetMeal,null,tint=Brass,modifier=Modifier.size(72.dp))}}
        item{HeritageCard{Text(c?.item?.weightGrams?.weight(s.settings.unitSystem)?:"Weight not recorded",style=MaterialTheme.typography.headlineMedium)
            c?.item?.lengthCm?.let{Text(if(s.settings.unitSystem==UnitSystem.METRIC)"%.0f cm".format(it) else "%.1f in".format(it/2.54),color=Muted)}
            Text("${c?.water?.name?:"Water not recorded"} · ${if(c?.item?.returned==true)"Returned" else "Kept"}",color=Muted)}}
        item{HeritageCard{Text("Tackle",color=Brass);Text("Rig · ${c?.item?.rig?.ifBlank{"Not recorded"}?:"Not recorded"}");Text("Bait · ${c?.item?.bait?.ifBlank{"Not recorded"}?:"Not recorded"}")}}
        // Weather is genuinely captured now, so the card reports what was recorded instead of blaming the network
        // for a snapshot the app never even attempted (TB-A-09).
        // Through the same summary the capture screen uses, so the units follow the setting. These four lines were
        // hardcoded metric: an imperial angler was shown 56°F when logging the fish and 13.2 °C when reading it
        // back. iOS has always rendered this one line through ConditionsMetrics.summary(_:unit:).
        item{HeritageCard{Text("Conditions",color=Teal)
            val w=c?.conditions
            val line=w?.summary(s.settings.unitSystem).orEmpty()
            if(line.isBlank())Text("No weather was recorded for this catch.",color=Muted) else Text(line)}}
        item{if(!c?.item?.notes.isNullOrBlank())HeritageCard{Text("Notes",color=Brass);Text(c!!.item.notes)}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton({c?.let{nav.navigate("edit/${it.item.id}")}},Modifier.weight(1f).testTag("editCatch")){Icon(Icons.Default.Edit,null);Text(" Edit")}
            OutlinedButton({c?.let{ShareSheet.catchCard(context,it,s.settings.unitSystem)}},Modifier.weight(1f)){Icon(Icons.Default.Share,null);Text(" Share")}}}
        item{TextButton({confirmDelete=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete this catch")}}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this catch?")},
        text={Text("It is removed from your vault, your records and your insights. This cannot be undone.")},
        confirmButton={TextButton({id?.let{vm.deleteCatch(it)};confirmDelete=false;nav.popBackStack()}){Text("Delete")}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}

@Composable fun Settings(s:AppState,vm:MainViewModel,nav:NavHostController){
    var token by rememberSaveable(s.settings.speciesIdToken){mutableStateOf(s.settings.speciesIdToken)}
    var confirm by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    val exported by vm.exported.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val importPlan by vm.importPlan.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let(vm::planImport)}
    // Hand the finished file straight to the share sheet, then clear it so rotating does not re-open the chooser.
    LaunchedEffect(exported){exported?.let{ShareSheet.file(context,it,"application/json","Export your Tacklebox journal");vm.clearExport()}}
    PushedScreen("Settings",onBack={nav.popBackStack()}){
        // icon={} for the same reason as onboarding: Material draws a checkmark in the selected segment that iOS's
        // control has no counterpart for, and the same control appeared two different ways in the same app.
        item{HeritageCard{SectionLabel("Units");SingleChoiceSegmentedButtonRow{UnitSystem.entries.forEachIndexed{i,u->SegmentedButton(s.settings.unitSystem==u,{vm.settings(s.settings.copy(unitSystem=u))},SegmentedButtonDefaults.itemShape(i,2),colors=SegmentedButtonDefaults.colors(activeContainerColor=Brass,activeContentColor=Background,inactiveContainerColor=Inset,inactiveContentColor=Ink),icon={}){Text(u.name.lowercase().replaceFirstChar(Char::uppercase))}}}}}
        // The Drive switch only ever persisted a boolean — there is no Drive code, no OAuth client and no
        // GoogleSignIn dependency in the app. Rather than keep a control that implies a backup is happening, say
        // plainly that it is not built yet and point at the export that does work.
        item{HeritageCard{SectionLabel("Backup");Text("Cloud backup isn’t built yet. Your journal lives on this device only — use Export below to keep a copy.",color=Muted)}}
        item{OutlinedTextField(token,{token=it},label={Text("Species-ID API token")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Text("An iNaturalist token, used only to identify a photo. It expires after about a day.",color=Muted,style=MaterialTheme.typography.bodyMedium);Button({vm.settings(s.settings.copy(speciesIdToken=token))}){Text("Save token")}}
        item{HeritageCard{SectionLabel("Data")
            OutlinedButton({vm.exportJson()},Modifier.fillMaxWidth().testTag("exportJson")){Icon(Icons.Default.FileDownload,null);Text(" Export JSON")}
            // Export alone is an escape hatch; import is what makes the journal portable — between devices, after
            // a wiped phone, or in from another app.
            OutlinedButton({importPicker.launch("application/json")},Modifier.fillMaxWidth().testTag("importJournal")){Icon(Icons.Default.FileUpload,null);Text(" Import a journal")}
            Text("Adds catches from a Tacklebox export. Nothing you already have is changed or removed.",color=Muted,style=MaterialTheme.typography.bodyMedium)
            TextButton({confirm=true},Modifier.fillMaxWidth(),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete catches, waters & gear")}}}
        item{Text("No ads · No analytics · No subscriptions",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=Muted)}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Delete your data?")},text={Text("This removes catches, sessions, waters, gear and presets from this device. Species and settings remain.")},confirmButton={TextButton({vm.deleteData();confirm=false}){Text("Delete")}},dismissButton={TextButton({confirm=false}){Text("Cancel")}})
    notice?.let{message->AlertDialog(onDismissRequest={vm.clearNotice()},title={Text("Couldn’t do that")},text={Text(message)},confirmButton={TextButton({vm.clearNotice()}){Text("OK")}})}
    // The plan is shown before anything is written, so the confirmation says exactly what will happen.
    importPlan?.let{plan->AlertDialog(onDismissRequest={vm.cancelImport()},title={Text("Import this journal?")},
        text={Text(importSummary(plan))},
        confirmButton={TextButton({vm.confirmImport()},modifier=Modifier.testTag("confirmImport")){Text("Import")}},
        dismissButton={TextButton({vm.cancelImport()}){Text("Cancel")}})}
    importResult?.let{r->AlertDialog(onDismissRequest={vm.clearImportResult()},title={Text("Import finished")},
        text={Text(importResultSummary(r))},
        confirmButton={TextButton({vm.clearImportResult()}){Text("OK")}})}
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
    PushedScreen("Catches",onBack={nav.popBackStack()},actions={
        // iOS uses line.3.horizontal.decrease.circle — the filter glyph inside a circle, filled once a filter is
        // active. Android showed a bare icon, and swapped to FilterAltOff when inactive, which reads as a
        // different control rather than the same one in a different state (TB-P-13).
        IconButton({showFilters=true},modifier=Modifier.testTag("filterCatches")){
            Box(Modifier.size(26.dp).background(if(filter.isActive)BrassSoft else Color.Transparent,CircleShape)
                .border(1.5.dp,BrassSoft,CircleShape),contentAlignment=Alignment.Center){
                Icon(Icons.Outlined.FilterAlt,"Filter catches",tint=if(filter.isActive)Background else BrassSoft,modifier=Modifier.size(15.dp))}}}){
        item{OutlinedTextField(filter.text,{filter=filter.copy(text=it)},
            label={Text("Species, water, rig or bait")},singleLine=true,
            leadingIcon={Icon(Icons.Default.Search,null)},
            trailingIcon={if(filter.text.isNotEmpty())IconButton({filter=filter.copy(text="")}){Icon(Icons.Default.Close,"Clear search")}},
            modifier=Modifier.fillMaxWidth().testTag("catchSearch"))}
        if(filter.isActive)item{HeritageCard{
            Text(filter.activeSummary(s.settings.unitSystem).joinToString(" · "),color=Muted,style=MaterialTheme.typography.bodyMedium)
            TextButton({filter=CatchFilter()},modifier=Modifier.testTag("clearFilters")){Text("Clear filters")}}}
        if(results.isEmpty())item{
            if(s.catches.isEmpty())Empty("Your vault is empty","Tap + to log your first catch and begin your private record.")
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
                FilterChip(filter.period==p,{onChange(filter.copy(period=p))},{Text(p.title)},colors=brassChipColours(),shape=CircleShape)}}
            Text("SPECIES",color=Brass,style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(s.species){sp->
                FilterChip(filter.speciesName==sp.name,{onChange(filter.copy(speciesName=if(filter.speciesName==sp.name)null else sp.name))},{Text(sp.name)},colors=brassChipColours(),shape=CircleShape)}}
            if(s.waters.isNotEmpty()){
                Text("WATER",color=Brass,style=MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(s.waters){w->
                    FilterChip(filter.waterName==w.name,{onChange(filter.copy(waterName=if(filter.waterName==w.name)null else w.name))},{Text(w.name)},colors=brassChipColours(),shape=CircleShape)}}}
            Text("AT LEAST",color=Brass,style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(weights){(label,grams)->
                FilterChip(filter.minimumGrams==grams,{onChange(filter.copy(minimumGrams=if(filter.minimumGrams==grams)null else grams))},{Text(label)},colors=brassChipColours(),shape=CircleShape)}}
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

// --- Catch date, notes and editing ------------------------------------------------------------------------------
/**
 * A tappable "caught at" row. The catch time used to be fixed at the moment of saving, so a session logged from the
 * car park recorded the wrong day and nothing could ever be back-dated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CaughtAtField(millis:Long,onChange:(Long)->Unit){
    var picking by rememberSaveable{mutableStateOf(false)}
    HeritageCard(onClick={picking=true}){
        Text("CAUGHT",color=Brass,style=MaterialTheme.typography.labelLarge)
        Text(Instant.ofEpochMilli(millis).pretty(),style=MaterialTheme.typography.titleLarge)
    }
    if(picking){
        val state=rememberDatePickerState(initialSelectedDateMillis=millis)
        DatePickerDialog(onDismissRequest={picking=false},
            confirmButton={TextButton({state.selectedDateMillis?.let(onChange);picking=false}){Text("Set")}},
            dismissButton={TextButton({picking=false}){Text("Cancel")}}){DatePicker(state)}
    }
}

/**
 * Editing a saved catch. Both apps were append-only: mistype a weight and the only remedy was to delete the catch
 * and enter it again.
 */
/**
 * Editing mirrors capture (TB-P-04), as it does on iOS. A screen where a weight is entered with steppers and
 * corrected with a text field is two designs for one number.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun EditCatch(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val row=s.catches.firstOrNull{it.item.id==id}
    if(row==null){PushedScreen("Edit catch",onBack={nav.popBackStack()}){item{Empty("Catch not found","It may have been deleted.")}};return}
    val metric=s.settings.unitSystem==UnitSystem.METRIC
    val original=row.item
    val startPounds=original.weightGrams?.let{Weights.toPoundsAndOunces(it)}
    var species by rememberSaveable{mutableStateOf(original.speciesId)}
    var kilograms by rememberSaveable{mutableIntStateOf(((original.weightGrams?:0.0)/1000).toInt())}
    var grams by rememberSaveable{mutableIntStateOf(((original.weightGrams?:0.0).toInt()%1000/10)*10)}
    var pounds by rememberSaveable{mutableIntStateOf(startPounds?.first?.toInt()?:0)}
    var ounces by rememberSaveable{mutableIntStateOf(startPounds?.second?.toInt()?:0)}
    var centimetres by rememberSaveable{mutableIntStateOf((original.lengthCm?:0.0).toInt())}
    var inches by rememberSaveable{mutableIntStateOf(((original.lengthCm?:0.0)/2.54).toInt())}
    var rig by rememberSaveable{mutableStateOf(original.rig.orEmpty())}
    var bait by rememberSaveable{mutableStateOf(original.bait.orEmpty())}
    var returned by rememberSaveable{mutableStateOf(original.returned)}
    var notes by rememberSaveable{mutableStateOf(original.notes)}
    var water by rememberSaveable{mutableStateOf(original.waterId)}
    var caughtAt by rememberSaveable{mutableStateOf(original.caughtAt.toEpochMilli())}
    var photos by rememberSaveable{mutableStateOf(row.allPhotoUris)}
    var addingPreset by rememberSaveable{mutableStateOf<PresetKind?>(null)}; var newPreset by rememberSaveable{mutableStateOf("")}

    val enteredGrams:Double=if(metric)(kilograms*1000+grams).toDouble() else (Weights.fromPoundsAndOunces(pounds.toString(),ounces.toString())?:0.0)
    val lengthCm:Double=if(metric)centimetres.toDouble() else inches*2.54

    CaptureScaffold("Edit Catch",onCancel={nav.popBackStack()}){
        item{PhotoStrip(photos){photos=it}}
        item{SectionLabel("Species")
            FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                s.species.forEach{sp->FilterChip(species==sp.id,{species=sp.id},{Text(sp.name)},colors=brassChipColours(),shape=CircleShape)}}}
        item{SectionLabel("Weight")
            Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                if(metric){
                    ValueStepper("KG",kilograms,0..100,modifier=Modifier.weight(1f)){kilograms=it}
                    ValueStepper("G",grams,0..990,10,Modifier.weight(1f)){grams=it}
                }else{
                    ValueStepper("LB",pounds,0..200,modifier=Modifier.weight(1f)){pounds=it}
                    ValueStepper("OZ",ounces,0..15,modifier=Modifier.weight(1f)){ounces=it}}}}
        item{SectionLabel("Length")
            Row(Modifier.padding(top=10.dp)){
                if(metric)ValueStepper("CM",centimetres,0..300,modifier=Modifier.weight(1f)){centimetres=it}
                else ValueStepper("IN",inches,0..120,modifier=Modifier.weight(1f)){inches=it}}}
        item{HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("Returned",fontWeight=FontWeight.SemiBold);Text(if(returned)"Put back in the water" else "Kept",color=Muted,style=MaterialTheme.typography.bodyMedium)}
            Switch(returned,{returned=it})}}}
        item{SectionLabel("Water")
            FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                s.waters.forEach{w->FilterChip(water==w.id,{water=if(water==w.id)null else w.id},{Text(w.name)},colors=brassChipColours(),shape=CircleShape)}}}
        item{SectionLabel("When");Box(Modifier.padding(top=10.dp)){CaughtAtField(caughtAt){caughtAt=it}}}
        item{PresetChips("Rig",rig,s.presets.filter{it.kind==PresetKind.RIG}.map{it.name},onPick={rig=it},onAdd={newPreset="";addingPreset=PresetKind.RIG})}
        item{PresetChips("Bait",bait,s.presets.filter{it.kind==PresetKind.BAIT}.map{it.name},onPick={bait=it},onAdd={newPreset="";addingPreset=PresetKind.BAIT})}
        item{SectionLabel("Notes")
            OutlinedTextField(notes,{notes=it},minLines=3,modifier=Modifier.fillMaxWidth().padding(top=10.dp).testTag("editNotes"))}
        item{Button({
                vm.updateCatch(original.copy(speciesId=species,weightGrams=enteredGrams.takeIf{it>0},lengthCm=lengthCm.takeIf{it>0},
                    rig=rig.ifBlank{null},bait=bait.ifBlank{null},returned=returned,notes=notes.trim(),
                    waterId=water,caughtAt=Instant.ofEpochMilli(caughtAt),photoUri=photos.firstOrNull()),photos)
                nav.popBackStack()
            },Modifier.fillMaxWidth().height(52.dp).testTag("saveEdit"),shape=RoundedCornerShape(14.dp),
            enabled=species!=null&&enteredGrams>0){Text("Save changes",fontWeight=FontWeight.Bold)}}
    }
    addingPreset?.let{kind->AlertDialog(onDismissRequest={addingPreset=null},title={Text("Add ${kind.name.lowercase()}")},
        text={OutlinedTextField(newPreset,{newPreset=it},label={Text("Name")},singleLine=true)},
        confirmButton={TextButton({if(newPreset.isNotBlank()){vm.addPreset(newPreset.trim(),kind);if(kind==PresetKind.RIG)rig=newPreset.trim() else bait=newPreset.trim();newPreset="";addingPreset=null}}){Text("Add")}},
        dismissButton={TextButton({addingPreset=null}){Text("Cancel")}})}
}


// --- Multiple photos per catch ----------------------------------------------------------------------------------
/**
 * The photo editor used by both the capture and edit screens. One photo per catch with no gallery was the biggest
 * thing the app under-used — anglers take three or four shots of a good fish. The first image is the cover, which
 * is what the Vault hero and list thumbnails show, so the first slot is labelled.
 */
@Composable fun PhotoStrip(photos:List<String>,onChange:(List<String>)->Unit){
    val limit=8
    val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){uris->
        if(uris.isNotEmpty())onChange((photos+uris.map{it.toString()}).take(limit))}
    var pending by remember{mutableStateOf<Uri?>(null)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->
        if(ok)pending?.let{onChange((photos+it.toString()).take(limit))}}
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted){pending=CapturePhoto.destination(context);camera.launch(pending!!)}}

    HeritageCard{
        if(photos.isEmpty()){
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedRectangle14).background(Inset).clickable{picker.launch("image/*")},contentAlignment=Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.AddAPhoto,null,tint=Brass);Text("Add photos",color=Muted)}}
        } else {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                itemsIndexed(photos){index,uri->
                    Box(Modifier.size(112.dp)){
                        AsyncImage(uri,if(index==0)"Cover photo" else "Photo ${index+1}",
                            Modifier.fillMaxSize().clip(RoundedRectangle14)
                                .border(if(index==0)2.dp else 1.dp,if(index==0)Brass else Muted.copy(alpha=.4f),RoundedRectangle14),
                            contentScale=ContentScale.Crop)
                        IconButton({onChange(photos.filterIndexed{i,_->i!=index})},Modifier.align(Alignment.TopEnd)){
                            Icon(Icons.Default.Cancel,"Remove photo ${index+1}",tint=Ink)}
                        if(index==0)Text("COVER",color=Background,style=MaterialTheme.typography.bodyMedium,
                            modifier=Modifier.align(Alignment.BottomStart).padding(4.dp).background(Brass,RoundedCornerShape(6.dp)).padding(horizontal=5.dp))}}}
        }
        Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton({picker.launch("image/*")},Modifier.weight(1f),enabled=photos.size<limit){
                Icon(Icons.Default.PhotoLibrary,null);Text(if(photos.isEmpty())" Library" else " Add more")}
            OutlinedButton({if(CapturePhoto.permitted(context)){pending=CapturePhoto.destination(context);camera.launch(pending!!)}
                            else cameraPermission.launch(Manifest.permission.CAMERA)},Modifier.weight(1f),enabled=photos.size<limit){
                Icon(Icons.Default.PhotoCamera,null);Text(" Camera")}}
        if(photos.size>=limit)Text("That's the limit of $limit photos for one catch.",color=Muted,style=MaterialTheme.typography.bodyMedium)
        else if(photos.size>1)Text("The first photo is the one that appears on your board.",color=Muted,style=MaterialTheme.typography.bodyMedium)
    }
}

/** A swipeable gallery for the catch detail. A single photo looks exactly as it did before. */
@Composable fun PhotoGallery(photos:List<String>){
    val pager=rememberPagerState(pageCount={photos.size})
    Column(horizontalAlignment=Alignment.CenterHorizontally){
        HorizontalPager(pager,Modifier.fillMaxWidth().height(220.dp)){page->
            AsyncImage(photos[page],"Photo ${page+1} of ${photos.size}",
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)),contentScale=ContentScale.Crop)}
        if(photos.size>1)Row(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            repeat(photos.size){i->Box(Modifier.size(7.dp).clip(CircleShape).background(if(i==pager.currentPage)Brass else Muted.copy(alpha=.35f)))}}
    }
}

val RoundedRectangle14=RoundedCornerShape(14.dp)

/** Says exactly what an import will do, including what it will skip and what it cannot carry. */
fun importSummary(plan:JournalImport.Plan):String{
    val parts=mutableListOf("${plan.newCatches} new ${if(plan.newCatches==1)"catch" else "catches"}")
    if(plan.newWaters>0)parts+="${plan.newWaters} new ${if(plan.newWaters==1)"water" else "waters"}"
    if(plan.newSpecies>0)parts+="${plan.newSpecies} new species"
    var text="This adds ${parts.joinToString(", ")}."
    if(plan.duplicateCatches>0)text+=" ${plan.duplicateCatches} ${if(plan.duplicateCatches==1)"catch is" else "catches are"} already in your vault and will be skipped."
    return text+" Nothing you already have is changed or removed. Photos aren’t included in an export, so imported catches arrive without them."
}

fun importResultSummary(r:JournalImport.Result):String{
    val parts=mutableListOf("${r.catches} ${if(r.catches==1)"catch" else "catches"}")
    if(r.waters>0)parts+="${r.waters} waters"
    if(r.sessions>0)parts+="${r.sessions} sessions"
    if(r.species>0)parts+="${r.species} species"
    return "Added ${parts.joinToString(", ")}."
}
