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
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.tacklebox.app.data.*
import uk.co.tacklebox.app.ui.FishGlyph
import uk.co.tacklebox.app.services.*
import uk.co.tacklebox.app.ui.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity:ComponentActivity(){
    /** A route another component asked for — the session notification or the OAuth callback. Consumed once shown. */
    private val requestedRoute=mutableStateOf<String?>(null)
    override fun onResume(){super.onResume();(application as TackleboxApp).unlimitedStore.refresh()}
    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        requestedRoute.value=intent.getStringExtra("tacklebox.route")
        setContent{TackleboxTheme{TackleboxRoot(requestedRoute=requestedRoute.value,onRouteConsumed={requestedRoute.value=null})}}
    }
    // singleTask in the manifest: a second launch reaches the running instance here instead of stacking another
    // activity, another ViewModel and another set of Room subscriptions on top of the first.
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);requestedRoute.value=intent.getStringExtra("tacklebox.route")}
}
data class Tab(val route:String,val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
// The same four symbols as the iOS tab bar, in their outline weight: shield, drop, calendar, chart.bar. Android
// had a house, waves and a clock against iOS's shield, droplet and calendar — three of the four icons on the one
// piece of chrome that is on screen the entire time (TB-P-10).
val tabs=listOf(Tab("vault","Vault",Icons.Outlined.Shield),Tab("waters","Waters",Icons.Outlined.WaterDrop),Tab("sessions","Sessions",Icons.Outlined.CalendarMonth),Tab("insights","Insights",Icons.Outlined.BarChart))

@Composable fun TackleboxRoot(vm:MainViewModel=viewModel(),requestedRoute:String?=null,onRouteConsumed:()->Unit={}){
    val state by vm.state.collectAsStateWithLifecycle(); val nav=rememberNavController()
    val back by nav.currentBackStackEntryAsState(); val route=back?.destination?.route
    // Every notice carries its own title — "Couldn't save this catch", "Session unavailable", "Photo backup" — as
    // iOS's alerts do. The free-session gate is a sheet on the capture screen (FreeSessionSheet), not a dialog here.
    val notice by vm.notice.collectAsStateWithLifecycle()
    notice?.let{n->AlertDialog(onDismissRequest=vm::clearNotice,title={Text(n.title)},
        text={Text(n.message)},confirmButton={TextButton(vm::clearNotice){Text("OK")}})}
    val showFab=route in setOf("vault","waters","sessions","insights")
    // Capture is presented as its own thing, not as a tab: iOS opens it as a modal sheet, and leaving the tab bar
    // visible behind it made the same task look like a different kind of thing on each platform (TB-P-04).
    val modal=route in setOf("log","edit/{id}")
    // Nothing until the store has answered — see AppState.loaded. Showing onboarding while the read is in flight
    // asks a returning angler to set the app up again.
    if(!state.loaded){Box(Modifier.fillMaxSize().background(Background))}
    else if(!state.settings.onboardingComplete){Onboarding(vm)} else Scaffold(containerColor=Background,bottomBar={if(!modal)BottomBar(nav,showFab)}){pad ->
        NavHost(nav,"vault",Modifier.padding(pad)){
            composable("vault"){Vault(state,vm,nav)}; composable("waters"){Waters(state,vm,nav)}; composable("sessions"){Sessions(state,vm,nav)}; composable("insights"){Insights(state,nav)}; composable("log"){LogCatch(state,vm,nav)}
            composable("session/{id}"){SessionDetail(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("catches"){Catches(state,nav)}; composable("edit/{id}"){EditCatch(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("tackle"){Tacklebox(state,vm,nav)}; composable("solunar"){Solunar(vm,nav)}; composable("tides"){Tides(vm,nav)}; composable("rivers"){Rivers(vm,nav)}; composable("settings"){Settings(state,vm,nav)}; composable("year"){YearOnWater(state,nav)}; composable("backup"){PhotoBackupScreen(state,vm,nav)}; composable("unlimited"){UnlimitedScreen(state,vm,nav)}; composable("season-book"){SeasonBookScreen(state,nav)}; composable("planner"){TripPlannerScreen(state,nav)}
            composable("water/{id}"){WaterPassport(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("species/{id}"){SpeciesDetail(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}; composable("catch/{id}"){CatchDetail(state,vm,it.arguments?.getString("id")?.toLongOrNull(),nav)}
        }
        // A route requested by the notification or the OAuth callback, applied once the graph exists. The start
        // destination stays Vault so the tab bar's popUpTo("vault") always has something to pop to.
        LaunchedEffect(requestedRoute){requestedRoute?.let{r->
            nav.navigate(r){launchSingleTop=true;if(tabs.any{it.route==r}){popUpTo("vault"){saveState=true};restoreState=true}}
            onRouteConsumed()}}
    }
}
// The log button is docked into the bar rather than floated over the content: a centre-docked FAB sat on top of the
// Vault's chip row and the Log screen's Save button (TB-A-04, TB-A-12). saveState/restoreState keep each tab's
// scroll position and back stack across tab switches.
@Composable fun BottomBar(nav:NavHostController,showFab:Boolean){val back by nav.currentBackStackEntryAsState();Box{NavigationBar(containerColor=Surface){tabs.forEachIndexed{i,t->if(i==2)Spacer(Modifier.weight(.65f));NavigationBarItem(selected=back?.destination?.route==t.route,onClick={nav.navigate(t.route){popUpTo("vault"){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(t.icon,null)},label={Text(t.label,fontSize=if(LocalDensity.current.fontScale>=2f)9.sp else 10.sp,lineHeight=12.sp,letterSpacing=0.sp,fontWeight=FontWeight.SemiBold,maxLines=2,softWrap=true,textAlign=TextAlign.Center)},modifier=Modifier.testTag("tab_${t.route}"),
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
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,top=18.dp,end=20.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Column{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){actions()}
            Text(eyebrow.uppercase(),color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
            Spacer(Modifier.height(4.dp))
            Text(title,style=MaterialTheme.typography.headlineLarge,maxLines=2)}}
        content()}
}

/**
 * A screen reached from another, rather than from the tab bar: a back chevron and a centred inline title.
 *
 * iOS pushes Catches, Bite windows, Year on the Water and Settings with a `navigationTitle`, which renders exactly
 * this way. Android showed them with the same large header as a tab root, so a pushed screen and a top-level one
 * were indistinguishable (TB-P-08).
 */
@Composable fun PushedScreen(title:String,onBack:()->Unit,actions:@Composable RowScope.()->Unit={},eyebrow:String?=null,heading:String?=null,content:LazyListScope.()->Unit){
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onBack,Modifier.testTag("back")){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back",tint=BrassSoft)}
            Spacer(Modifier.weight(1f))
            Text(title,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row{actions()}
            Spacer(Modifier.width(4.dp))}
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,top=4.dp,end=18.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            // iOS pushes some screens with an inline (empty) bar title and a `ScreenHeader` — eyebrow over a serif
            // title — at the top of the content: Species record, Water passport, Session detail, the live screens.
            if(heading!=null)item{Column{
                eyebrow?.let{Text(it.uppercase(),color=Brass,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp);Spacer(Modifier.height(4.dp))}
                Text(heading,style=MaterialTheme.typography.headlineLarge,maxLines=2)}}
            content()}}
}
// Only a card with something to do gets the clickable overload: `onClick = onClick ?: {}` turned every static card
// into a button with a ripple, which TalkBack announced as dozens of inert controls.
@Composable fun HeritageCard(modifier:Modifier=Modifier,onClick:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit){
    val colours=CardDefaults.cardColors(containerColor=Surface);val shape=RoundedCornerShape(18.dp);val border=BorderStroke(1.dp,Muted.copy(alpha=.16f))
    if(onClick!=null)Card(modifier=modifier.fillMaxWidth(),colors=colours,shape=shape,border=border,onClick=onClick){Column(Modifier.padding(16.dp),content=content)}
    else Card(modifier=modifier.fillMaxWidth(),colors=colours,shape=shape,border=border){Column(Modifier.padding(16.dp),content=content)}}
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
    // iOS appends the trend — "996 hPa steady". Android has the column and the importer fills it, but nothing here
    // ever captures one, so it shows only for journals imported from iOS. Recorded as a gap rather than faked.
    val pressure=pressureHpa?.let{
        val reading=if(unit==UnitSystem.METRIC)"${whole(it)} hPa" else String.format("%.2f inHg",it*0.0295299830714)
        pressureTrend?.takeIf{t->t.isNotBlank()}?.let{t->"$reading ${t.lowercase()}"} ?: reading}
    val moon=moonPhase?.let{"$it moon"}
    return listOfNotNull(temperature,wind,pressure,moon).joinToString("  ·  ")
}

/** Degrees to a sixteen-point compass label, the same table the conditions snapshot uses. */
fun compassPoint(degrees:Double):String{
    val points=listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
    return points[(((degrees % 360) / 22.5).roundToInt()) % 16]
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
        item{TextButton({nav.navigate("planner")},contentPadding=PaddingValues(0.dp)){Text("Plan a trip",color=BrassSoft)}}
        item{TodayOnTheBank(sol,located,onClick={nav.navigate("solunar")})}
        // Stats and the board only once there is something to count, as iOS does — an empty vault showed three
        // zeroes and a PB BOARD heading with nothing under it (TB-P-12).
        if(s.catches.isNotEmpty()){
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${s.catches.size}","fish landed",Modifier.weight(1f).clickable{nav.navigate("catches")});Stat("${s.catches.mapNotNull{it.species?.id}.distinct().size}","species",Modifier.weight(1f));Stat("${s.waters.size}","waters",Modifier.weight(1f))}}
            item{SectionLabel("PB board")}}
        // A record needs a species: catches with none have no page to open, so they do not get a board card. Two
        // columns with a chevron, as the iOS `LazyVGrid` lays the board out, sorted by species name.
        val board=CatchFilter.personalBests(s.catches).entries.sortedBy{it.key}.map{it.value}
        items(board.chunked(2)){pair->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            pair.forEach{c->HeritageCard(Modifier.weight(1f),onClick={nav.navigate("species/${c.species?.id}")}){
                Row(Modifier.defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(c.species?.name.orEmpty(),color=Muted,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,maxLines=2)
                        Text(c.item.weightGrams?.weight(s.settings.unitSystem).orEmpty(),color=BrassSoft,style=MaterialTheme.typography.titleLarge,maxLines=1)}
                    Icon(Icons.Default.ChevronRight,null,tint=Dim,modifier=Modifier.size(16.dp))}}}
            if(pair.size==1)Spacer(Modifier.weight(1f))}}
        if(s.catches.isNotEmpty()){
            item{SectionLabel("Last session")}
            item{val last=s.sessions.maxByOrNull{it.item.startAt}
                HeritageCard{if(last!=null){
                    Text(last.water?.name?:"Unassigned water",style=MaterialTheme.typography.titleLarge)
                    Text(last.item.startAt.atZone(ZoneId.systemDefault()).toLocalDate().pretty(),color=Muted,style=MaterialTheme.typography.bodyMedium)
                    Text("${last.catches.size} fish · ${last.catches.mapNotNull{it.weightGrams}.sum().weight(s.settings.unitSystem)}",style=MaterialTheme.typography.bodyMedium)
                } else Text("No sessions yet",color=Muted)}}}
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
            Text(pb?.item?.weightGrams?.weight(unit)?:"—",style=MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(pb?.species?.name?:"Log your first catch",color=Ink.copy(alpha=.88f),fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
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
    val next=BiteWindows.next(sol.windows,now)
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
    Screen("Private places","Waters",actions={IconButton({adding=true},modifier=Modifier.testTag("addWater")){Icon(Icons.Default.Add,"Add water",tint=BrassSoft)}}){
        item{HeritageCard(onClick={nav.navigate("rivers")}){Text("River conditions",style=MaterialTheme.typography.titleLarge);Text("Levels and nearby gauges",color=Muted)}}
        item{HeritageCard(onClick={nav.navigate("tides")}){Text("Tides & sea",style=MaterialTheme.typography.titleLarge);Text("Tide times, waves and sea state",color=Muted)}}
        item{SectionLabel("Water passports")}
        if(s.waters.isEmpty())item{Empty("No waters saved","Add a water to build your private map.")}
        items(s.waters){w->HeritageCard(onClick={nav.navigate("water/${w.id}")}){Row{Icon(Icons.Default.Water,null,tint=Teal);Spacer(Modifier.width(12.dp));Column{Text(w.name,style=MaterialTheme.typography.titleLarge);Text(w.subtitle,color=Muted)}}}}
    }
    if(adding)WaterSelectionSheet(vm,s.waters,"Add Water","Add water",creationOnly=true,onDismiss={adding=false}){}
}

@Composable fun WaterFormDialog(onDismiss:()->Unit,onSave:(String,WaterType,String)->Unit,initial:Water?=null){
    var name by rememberSaveable{mutableStateOf(initial?.name.orEmpty())}; var region by rememberSaveable{mutableStateOf(initial?.region.orEmpty())}
    var type by rememberSaveable{mutableStateOf(initial?.type ?: WaterType.DAY_TICKET)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(initial==null)"Add Water" else "Edit Water")},
        text={Column(Modifier.verticalScroll(rememberScrollState())){WaterFields(name,type,region,{name=it},{type=it},{region=it},label=null)}},
        confirmButton={TextButton({if(name.isNotBlank())onSave(name.trim(),type,region.trim())},enabled=name.isNotBlank()){Text("Save")}},
        dismissButton={TextButton(onDismiss){Text("Cancel")}})
}

// Swim notes are editable here, matching iOS; they were read-only while the card invited the angler to keep them.
@Composable fun WaterPassport(s:AppState,vm:MainViewModel,id:Long?,nav:NavHostController){
    val w=s.waters.firstOrNull{it.id==id};val catches=s.catches.filter{it.water?.id==id}
    var notes by remember(w?.id){mutableStateOf(w?.swimNotes.orEmpty())}
    var editing by rememberSaveable{mutableStateOf(false)}
    var confirmDelete by rememberSaveable{mutableStateOf(false)}
    PushedScreen(w?.name?:"Water passport",onBack={nav.popBackStack()}){
        item{Column{SectionLabel("Water passport");Spacer(Modifier.height(8.dp));OutlinedButton({editing=true},enabled=w!=null){Text("Edit water")}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${catches.size}","catches",Modifier.weight(1f));Stat("${catches.mapNotNull{it.species}.distinctBy{it.id}.size}","species",Modifier.weight(1f))}}
        item{HeritageCard{Text("Private swim notes",color=Brass)
            OutlinedTextField(notes,{notes=it;w?.let{water->vm.updateWater(water.copy(swimNotes=it))}},placeholder={Text("Reeds on the west bank fish well at dusk…")},modifier=Modifier.fillMaxWidth())}}
        item{SectionLabel("Most productive");Text(catches.groupingBy{it.species?.name?:"Unknown"}.eachCount().maxByOrNull{it.value}?.key?:"Log catches here to reveal patterns",color=Muted)}
        item{TextButton({confirmDelete=true},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Delete this water")}}
    }
    if(editing && w!=null)WaterFormDialog(onDismiss={editing=false},onSave={name,type,region->vm.updateWater(w.copy(name=name,type=type,region=region)){editing=false}},initial=w)
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete ${w?.name.orEmpty()}?")},
        text={Text("The water is removed. Catches and sessions logged here are kept, but they will no longer name a water.")},
        confirmButton={TextButton({id?.let{vm.deleteWater(it)};confirmDelete=false;nav.popBackStack()}){Text("Delete")}},
        dismissButton={TextButton({confirmDelete=false}){Text("Cancel")}})
}


@Composable fun Insights(s:AppState,nav:NavHostController){val total=s.catches.mapNotNull{it.item.weightGrams}.sum();Screen("Patterns from the bank","Insights"){item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat("${s.catches.size}","landed",Modifier.weight(1f));Stat(total.weight(s.settings.unitSystem),"total weight",Modifier.weight(1f))}};item{HeritageCard(onClick={nav.navigate("year")}){Text("YEAR ON THE WATER",color=Brass);Text("Your season, distilled",style=MaterialTheme.typography.headlineMedium);Text("Open shareable summary →",color=Muted)}};item{Breakdown("Catches over time",s.catches.groupingBy{java.time.YearMonth.from(it.item.caughtAt.atZone(ZoneId.systemDefault())).toString()}.eachCount())};item{Breakdown("Species",s.catches.groupingBy{it.species?.name?:"Unknown"}.eachCount())};item{Breakdown("Waters",s.catches.groupingBy{it.water?.name?:"Unspecified"}.eachCount())};item{HeritageCard{Text("Conditions insight",style=MaterialTheme.typography.titleLarge);Text(Insight.conditions(s.catches),color=Muted)}}}}
@Composable fun YearOnWater(s:AppState,nav:NavHostController){var year by rememberSaveable{mutableIntStateOf(Year.now().value)};val years=(s.catches.map{it.item.caughtAt.atZone(ZoneId.systemDefault()).year}+s.sessions.map{it.item.startAt.atZone(ZoneId.systemDefault()).year}+Year.now().value).distinct().sortedDescending();val catches=s.catches.filter{it.item.caughtAt.atZone(ZoneId.systemDefault()).year==year};val biggest=SeasonSummary.biggest(catches);val hours=SeasonMetrics.hours(s.sessions,year);PushedScreen("Year on the Water",onBack={nav.popBackStack()}){item{TextButton({nav.navigate("season-book")}){Text("Create a Season Book")}};item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(years){y->FilterChip(year==y,{year=y},{Text(y.toString())},colors=brassChipColours())}}};item{Card(colors=CardDefaults.cardColors(containerColor=Inset),shape=RoundedCornerShape(28.dp)){Column(Modifier.padding(26.dp)){Text("TACKLEBOX · $year",color=Brass);Text("${catches.size}",style=MaterialTheme.typography.displaySmall);Text("fish landed",color=Muted);HorizontalDivider(Modifier.padding(vertical=16.dp));Text("${catches.mapNotNull{it.item.weightGrams}.sum().weight(s.settings.unitSystem)} carried gently");Text(if(biggest==null)"No biggest fish yet" else "${biggest.species?.name?:"Unknown species"} · ${biggest.item.weightGrams?.weight(s.settings.unitSystem).orEmpty()}");Text("$hours hours on the bank");Text("Top bait · ${SeasonSummary.topBait(catches)?:"—"}",color=BrassSoft)}}};item{val context=LocalContext.current;Button({ShareSheet.season(context,s,year)},Modifier.fillMaxWidth().testTag("shareSeason")){Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Share summary")}}}}

// Gear was always filed as OTHER and presets always as RIG, with no way to pick either and no way to delete
// anything — deleteGear existed in the DAO but nothing reached it, and presets had no delete at all.
@Composable fun Tacklebox(s:AppState,vm:MainViewModel,nav:NavHostController){
    var name by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf(GearCategory.ROD)}
    var kind by rememberSaveable{mutableStateOf(PresetKind.RIG)}
    var editingGear by remember { mutableStateOf<GearItem?>(null) }
    var showGearEditor by rememberSaveable { mutableStateOf(false) }
    PushedScreen("My Tacklebox",onBack={nav.popBackStack()}){
        item{SectionLabel("Ready for the bank")}
        item { OutlinedButton({editingGear=null;showGearEditor=true}){Text("Add gear with notes")} }
        item { GearPerformance(s) }
        items(s.gear){g->HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(g.name,style=MaterialTheme.typography.titleLarge);Text(g.category.name.lowercase().replaceFirstChar(Char::uppercase),color=Muted)};IconButton({editingGear=g;showGearEditor=true}){Icon(Icons.Default.Edit,"Edit "+g.name)};IconButton({vm.deleteGear(g)}){Icon(Icons.Default.Delete,"Delete ${g.name}",tint=Muted)}}}}
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
    if(showGearEditor)GearEditor(editingGear,{showGearEditor=false}){vm.saveGear(it){showGearEditor=false}}
}

// Bite windows now follow the device rather than a hardcoded 52.5/-1.5 (TB-A-10), and say so when they cannot.
@Composable fun Solunar(vm:MainViewModel,nav:NavHostController){
    val state by vm.state.collectAsStateWithLifecycle()
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
    var dayOffset by rememberSaveable{mutableIntStateOf(0)}
    val selectedDay=java.time.LocalDate.now().plusDays(dayOffset.toLong())
    val d=place?.let{Astronomy.calculate(date=selectedDay,latitude=it.first,longitude=it.second)}?:Astronomy.calculate(date=selectedDay)
    PushedScreen("Bite windows",onBack={nav.popBackStack()}){
        item{PersonalWindowsCard(state.catches,place?.first ?: 52.36,place?.second ?: -1.17,if(located)ZoneId.systemDefault() else ZoneId.of("Europe/London"))}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton({dayOffset--}){Text("Previous")};TextButton({dayOffset=0}){Text(selectedDay.pretty())};TextButton({dayOffset++}){Text("Next")}}}
        item{Text(if(located)"Calculated on-device for your position" else "Calculated on-device · showing central UK",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        if(!located&&canAsk)item{HeritageCard(onClick={request.launch(Manifest.permission.ACCESS_COARSE_LOCATION)}){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.LocationOn,null,tint=Brass);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Use my location for local times",fontWeight=FontWeight.SemiBold);Text("Approximate only — your precise spot is never stored.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Stat(d.rating.title,"day rating",Modifier.weight(1f));Stat(d.moonPhase,"moon",Modifier.weight(1f))}};item{HeritageCard{Text("Sun & moon",style=MaterialTheme.typography.titleLarge);Text("Sunrise ${d.sunrise.hm()}  ·  Sunset ${d.sunset.hm()}",color=Muted);Text("Moonrise ${d.moonrise?.hm() ?: "—"}  ·  Moonset ${d.moonset?.hm() ?: "—"}",color=Muted)}};items(d.windows){w->HeritageCard{Row{Column(Modifier.weight(1f)){Text(w.label);Text(if(w.major)"Major feeding period" else "Minor feeding period",color=if(w.major)Brass else Muted)};Text("${w.start.hm()}–${w.end.hm()}")}}}}}
/**
 * Tides and sea state. The screen was named for tides it did not have until now: it showed wave height and period
 * from Open-Meteo and nothing else, while iOS had NOAA and WorldTides predictions. Both are here now.
 */
@Composable fun Tides(vm:MainViewModel,nav:NavHostController){
    val unit=vm.state.collectAsStateWithLifecycle().value.settings.unitSystem
    val sea by vm.marine.collectAsStateWithLifecycle()
    val tide by vm.tides.collectAsStateWithLifecycle()
    val place by vm.marinePlace.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){vm.marine();vm.tides()}
    PushedScreen("Tides & sea",onBack={nav.popBackStack()}){
        item{SectionLabel("Coastal outlook")}
        if(place.isNotBlank()) item{Text(place,color=Muted,style=MaterialTheme.typography.bodyMedium)}
        item{SectionLabel("Today's tides")}
        item{when(val t=tide){
            LiveState.Idle,LiveState.Loading->Loading()
            // Not a red error card: "no predictions for your area" is a fact about where you are standing, not a
            // failure, and the answer to it is a WorldTides key rather than a retry.
            is LiveState.Error->HeritageCard{Text(t.message,color=Muted)
                Spacer(Modifier.height(6.dp));Text("NOAA covers the contiguous US at no cost. Elsewhere, add a WorldTides key in Settings.",color=Muted,style=MaterialTheme.typography.bodyMedium)}
            is LiveState.Data->{val r=t.value
                HeritageCard{
                    val next=r.next()
                    if(next!=null){Text("Next ${if(next.kind==TideKind.HIGH)"high" else "low"} · ${next.time.hm()}",style=MaterialTheme.typography.titleLarge)
                        Text("%s · %s%s".format(next.heightMetres.metres(unit),r.source.label,if(r.cached)" · last known" else ""),color=Muted)}
                    else Text("Today's tides have passed",style=MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(10.dp))
                    r.events.forEach{e->Row{Text(if(e.kind==TideKind.HIGH)"High" else "Low",Modifier.weight(1f),color=if(e.kind==TideKind.HIGH)Brass else Muted);Text("${e.time.hm()}  ·  ${e.heightMetres.metres(unit)}",color=Ink)}}}}}}
        item{SectionLabel("Sea state")}
        item{when(val x=sea){
            LiveState.Idle,LiveState.Loading->Loading()
            is LiveState.Error->ErrorCard(x.message){vm.marine()}
            is LiveState.Data->{val h=x.value.hourly
                if(h==null||h.waveHeight.isEmpty())Empty("No coastal data","You may be inland or outside forecast coverage.")
                else HeritageCard{
                    // Wave direction and sea temperature come back now too; iOS has always asked for them.
                    h.seaTemperature.firstOrNull{it!=null}?.let{t->Text("Sea temperature · ${t.celsius(unit)}",color=Muted);Spacer(Modifier.height(8.dp))}
                    // The hourly series starts at midnight, so the first eight entries were this morning's by the
                    // afternoon. Show the next eight hours from now; a reading the service left out prints "—".
                    val from=MarineHours.firstFromNow(h.time)
                    (from until minOf(from+8,h.time.size)).forEach{i->Row{
                        Text(h.time[i].takeLast(5),Modifier.weight(1f))
                        val dir=h.waveDirection.getOrNull(i)?.let{d->" "+compassPoint(d)}.orEmpty()
                        Text("${h.waveHeight.getOrNull(i)?.metres(unit)?:"—"}$dir · ${h.wavePeriod.getOrNull(i)?.let{"%.0f".format(it)}?:"—"} s",color=Teal)}}}}}}
    }
}

/** A height in the angler's units: "1.2 m" or "3.9 ft". */
fun Double.metres(unit:UnitSystem):String=if(unit==UnitSystem.METRIC)"%.1f m".format(this) else "%.1f ft".format(this/0.3048)
/** A temperature in the angler's units: "14.2 °C" or "57.6 °F". */
fun Double.celsius(unit:UnitSystem):String=if(unit==UnitSystem.METRIC)"%.1f °C".format(this) else "%.1f °F".format(this*9/5+32)
private fun java.time.Instant.hm():String=
    java.time.LocalDateTime.ofInstant(this,java.time.ZoneId.systemDefault()).toLocalTime().hm()
/**
 * River gauges. This printed bare numbers with no station name, units or trend, and had no US coverage; iOS has
 * shown named Environment Agency stations with a rising/falling reading, and USGS, since it shipped.
 */
@Composable fun Rivers(vm:MainViewModel,nav:NavHostController){
    val live by vm.river.collectAsStateWithLifecycle()
    val place by vm.riverPlace.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){vm.river()}
    PushedScreen("River conditions",onBack={nav.popBackStack()}){
        item{SectionLabel("Live water")}
        if(place.isNotBlank()) item{Text(place,color=Muted,style=MaterialTheme.typography.bodyMedium)}
        when(val x=live){
            LiveState.Idle,LiveState.Loading->item{Loading()}
            // "Not available for your area" and "no gauges nearby" are facts about where you are, not failures,
            // so they are stated rather than offered with a retry that would say the same thing again.
            is LiveState.Error->item{HeritageCard{Text(x.message,color=Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedButton({vm.river()},Modifier.fillMaxWidth()){Text("Try again")}}}
            is LiveState.Data->{
                val r=x.value
                item{SectionLabel(if(r.cached)"Nearby gauges · last known" else "Nearby gauges")}
                items(r.gauges){g->HeritageCard{
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(g.name,style=MaterialTheme.typography.titleLarge)
                            Text(listOfNotNull(g.river,"%.1f km away".format(g.distanceKm)).joinToString(" · "),color=Muted,style=MaterialTheme.typography.bodyMedium)}
                        TrendChip(g.trend)}
                    Spacer(Modifier.height(8.dp))
                    g.latestLevel?.let{Text("Level · %.2f %s".format(it.value,it.unit).trim(),color=Ink)}
                    g.latestFlow?.let{Text("Flow · %.2f %s".format(it.value,it.unit).trim(),color=Ink)}
                    Text(listOfNotNull(g.sourceLabel,g.updatedAt?.let{"updated ${it.hm()}"}).joinToString(" · "),color=Muted.copy(alpha=.7f),style=MaterialTheme.typography.bodyMedium)}}}
        }
    }
}

/** Rising, falling or steady — the part of a gauge reading an angler actually acts on. */
@Composable private fun TrendChip(trend:RiverTrend){
    val (label,colour)=when(trend){
        RiverTrend.RISING->"Rising" to Brass
        RiverTrend.FALLING->"Falling" to Teal
        RiverTrend.STEADY->"Steady" to Muted
        RiverTrend.UNKNOWN->"—" to Muted.copy(alpha=.5f)}
    Text(label.uppercase(),color=Background,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,
        modifier=Modifier.background(colour,RoundedCornerShape(12.dp)).padding(horizontal=9.dp,vertical=4.dp))
}
@Composable fun Empty(title:String,body:String,icon:androidx.compose.ui.graphics.vector.ImageVector?=null,onClick:(()->Unit)?=null){HeritageCard(onClick=onClick){Column(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
    Box(Modifier.size(52.dp).background(Inset,CircleShape),contentAlignment=Alignment.Center){if(icon!=null)Icon(icon,null,tint=Teal,modifier=Modifier.size(28.dp)) else FishGlyph(Teal,Modifier.size(34.dp,18.dp))}
    Text(title,style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center);Text(body,color=Muted,textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium)}}}
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
                CatchThumbnail(c,58.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(c.species?.name?:"Unknown species",fontWeight=FontWeight.SemiBold,maxLines=1)
                        if(bests[c.species?.name]?.item?.id==c.item.id){Spacer(Modifier.width(8.dp));PBPill(small=true)}}
                    Text(c.item.weightGrams?.weight(s.settings.unitSystem)?:"—",color=BrassSoft,style=MaterialTheme.typography.titleLarge,maxLines=1)
                    Text(listOfNotNull(c.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalDate().pretty(),s.resolvedWater(c)?.name,c.item.bait?.ifBlank{null}).joinToString(" · "),
                        color=Muted,style=MaterialTheme.typography.bodyMedium,maxLines=1)}
                Icon(Icons.Default.ChevronRight,null,tint=Dim,modifier=Modifier.size(18.dp))}}}
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
                TextButton({onChange(CatchFilter())}){Text("Clear",color=BrassSoft)}
                Text("Filter",Modifier.weight(1f),fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
                TextButton(onDismiss,Modifier.testTag("filterDone")){Text("Done",color=BrassSoft)}}
            Text("WHEN",color=Brass,style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(CatchFilter.Period.entries.toList()){p->
                FilterChip(filter.period==p,{onChange(filter.copy(period=p))},{Text(p.title)},colors=brassChipColours(),shape=CircleShape)}}
            Text("SPECIES",color=Brass,style=MaterialTheme.typography.labelLarge)
            // Every species with a catch, whatever its discipline: a fish of a switched-off discipline must still be findable.
            val caught=s.catches.mapNotNull{it.species?.id}.toSet()
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(s.species.filter{it.id in caught}){sp->
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


