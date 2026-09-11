/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.SessionRow
import uk.co.tacklebox.app.ui.*
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a season adds up to — the same picks as the iOS `YearOnTheWaterView`. */
object SeasonRules {
    fun year(at:java.time.Instant, zone:ZoneId=ZoneId.systemDefault())=at.atZone(zone).year
    /** Hours inside the year, exactly, as iOS sums them; "6.5" under ten hours, whole above. */
    fun hours(sessions:List<SessionRow>, year:Int, zone:ZoneId=ZoneId.systemDefault()):Double {
        val first=LocalDate.of(year,1,1).atStartOfDay(zone).toInstant(); val last=LocalDate.of(year+1,1,1).atStartOfDay(zone).toInstant()
        return sessions.sumOf { row -> val end=row.item.endAt ?: return@sumOf 0.0
            val start=maxOf(row.item.startAt,first); val finish=minOf(end,last)
            if (finish<=start) 0.0 else java.time.Duration.between(start,finish).seconds/3600.0 }
    }
    fun formattedHours(hours:Double):String = if (hours<10) "%.1f".format(hours) else "%.0f".format(hours)
    /** All-time personal bests set during the year, by species name. */
    fun newPersonalBests(all:List<CatchRow>, year:Int):List<CatchRow> =
        CatchFilter.personalBests(all).values.filter { year(it.item.caughtAt)==year }.sortedBy { it.species?.name.orEmpty() }
}

/**
 * Year on the Water: the hero, the Season Book link, VIEW SEASON (more than one year), then THE YEAR IN NUMBERS,
 * BIGGEST FISH, NEW PERSONAL BESTS, YOUR GO-TOs, BEST PATTERN and "Share your season" — or the empty card.
 */
@Composable fun YearOnWater(s:AppState,nav:NavHostController){
    val unit=s.settings.unitSystem
    val latestCatchYear=s.catches.maxOfOrNull{SeasonRules.year(it.item.caughtAt)}?:Year.now().value
    var year by rememberSaveable{mutableIntStateOf(latestCatchYear)}
    val years=(s.catches.map{SeasonRules.year(it.item.caughtAt)}+s.sessions.map{SeasonRules.year(it.item.startAt)}+Year.now().value).distinct().sortedDescending()
    val catches=s.catches.filter{SeasonRules.year(it.item.caughtAt)==year}
    val sessions=s.sessions.filter{SeasonRules.year(it.item.startAt)==year}
    val biggest=SeasonSummary.biggest(catches)
    val totalWeight=catches.mapNotNull{it.item.weightGrams}.sum()
    val speciesCount=catches.mapNotNull{it.species?.name}.distinct().size
    val waterCount=catches.mapNotNull{s.resolvedWater(it)?.name}.distinct().size
    val pbs=SeasonRules.newPersonalBests(s.catches,year)
    val pattern=InsightRules.bestPattern(catches.mapNotNull{it.conditions},unit)
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var expanded by remember{mutableStateOf(false)}
    var sharing by remember{mutableStateOf(false)}
    PushedScreen("Year on the Water",onBack={nav.popBackStack()}){
        item{Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Inset,Teal.copy(alpha=.32f)))).border(1.dp,Brass.copy(alpha=.45f),RoundedCornerShape(24.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text("TACKLEBOX · YEAR ON THE WATER",color=BrassSoft,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp,modifier=Modifier.weight(1f));Icon(Icons.Default.AutoAwesome,null,tint=BrassSoft,modifier=Modifier.size(16.dp))}
            Text("Your $year\non the Water",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold,lineHeight=40.sp)
            Row(verticalAlignment=Alignment.Bottom){Text("${catches.size}",color=BrassSoft,fontSize=72.sp,lineHeight=72.sp,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.displaySmall);Spacer(Modifier.width(10.dp));Text("fish landed",color=Muted,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=12.dp))}
            Text(if(catches.isEmpty())"The next chapter is waiting on the bank." else "A year of early starts, quiet water and stories worth keeping.",color=Ink.copy(alpha=.86f),style=MaterialTheme.typography.bodyMedium)}}
        item{TextButton({nav.navigate("season-book")},contentPadding=PaddingValues(0.dp)){Text("Create a Season Book",color=BrassSoft)}}
        if(years.size>1)item{Row(verticalAlignment=Alignment.CenterVertically){SectionLabel("View season");Spacer(Modifier.weight(1f))
            Box{Row(Modifier.defaultMinSize(minHeight=44.dp).clickable{expanded=true}.testTag("viewSeason"),verticalAlignment=Alignment.CenterVertically){Text("$year",color=BrassSoft,fontWeight=FontWeight.SemiBold);Icon(Icons.Default.ArrowDropDown,null,tint=BrassSoft)}
                DropdownMenu(expanded,{expanded=false}){years.forEach{y->DropdownMenuItem(text={Text("$y")},onClick={year=y;expanded=false})}}}}}
        if(catches.isEmpty())item{HeritageCard{Column(Modifier.fillMaxWidth().padding(vertical=22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
            FishGlyphOutline(Teal,Modifier.size(112.dp,68.dp));Text("No catches logged for $year yet — get out on the bank!",style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center)}}}
        else{
            item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("The year in numbers")
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("${catches.size}","Fish landed",Modifier.weight(1f));Stat(totalWeight.weight(unit),"Total weight",Modifier.weight(1f))}
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("${sessions.size}","Sessions",Modifier.weight(1f));Stat(SeasonRules.formattedHours(SeasonRules.hours(s.sessions,year)),"Hours on bank",Modifier.weight(1f))}
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("$waterCount","Waters fished",Modifier.weight(1f));Stat("$speciesCount","Species caught",Modifier.weight(1f))}}}
            biggest?.let{fish->item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Biggest fish")
                HeritageCard{
                    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)).background(Inset),contentAlignment=Alignment.Center){
                        if(fish.item.photoUri!=null){AsyncImage(fish.item.photoUri,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to androidx.compose.ui.graphics.Color.Transparent,1f to Background.copy(alpha=.52f))))}
                        else FishGlyph(Teal,Modifier.fillMaxSize().padding(22.dp))}
                    Spacer(Modifier.height(16.dp))
                    Text(fish.species?.name?:"Unknown species",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text(fish.item.weightGrams?.weight(unit).orEmpty(),style=MaterialTheme.typography.displaySmall,color=BrassSoft)
                    Text("${s.resolvedWater(fish)?.name?:"Water not recorded"} · ${fish.item.caughtAt.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))}",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}
            if(pbs.isNotEmpty())item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("New personal bests")
                HeritageCard{Text("${pbs.size} new ${if(pbs.size==1)"best" else "bests"}",style=MaterialTheme.typography.headlineMedium,color=BrassSoft);Spacer(Modifier.height(12.dp))
                    pbs.forEachIndexed{i,fish->Row(Modifier.defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(fish.species?.name?:"Unknown species",fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(fish.item.weightGrams?.weight(unit).orEmpty(),color=Muted,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}
                        if(i<pbs.size-1)Rule()}}}}
            item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Your go-tos")
                HeritageCard{
                    DetailRow(Icons.Default.Hub,"Rig",InsightRules.mode(catches.map{it.item.rig})?:"Not recorded");Rule()
                    DetailRow(Icons.Default.Grain,"Bait",InsightRules.mode(catches.map{it.item.bait})?:"Not recorded");Rule()
                    DetailRow(Icons.Default.SetMeal,"Species",InsightRules.mode(catches.map{it.species?.name})?:"Not recorded");Rule()
                    DetailRow(Icons.Default.WaterDrop,"Best water",InsightRules.mode(catches.map{s.resolvedWater(it)?.name})?:"Not recorded")}}}
            pattern?.let{p->item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Best pattern")
                HeritageCard{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    LabelledHeading(Icons.Default.Air,"WHEN IT CAME TOGETHER",Teal)
                    Text("${p.wind} wind · ${p.pressure} pressure · ${p.temperature}",style=MaterialTheme.typography.headlineMedium)
                    Text("Based on ${catches.count{it.conditions!=null}} catches with saved conditions",color=Dim,style=MaterialTheme.typography.bodySmall)}}}}}
            item{Button({if(!sharing){sharing=true;scope.launch{
                    val season=ShareCards.Season(year,biggest?.species?.name,biggest?.item?.weightGrams?.weight(unit),catches.size,totalWeight.weight(unit),sessions.size,speciesCount)
                    val card=withContext(Dispatchers.IO){runCatching{
                        val photo=biggest?.item?.photoUri?.let{PhotoStore.decodeOriented(context,it,1080)}
                        ShareCards.seasonCard(context,season,photo).also{photo?.recycle()}}.getOrNull()}
                    if(card!=null)ShareCards.share(context,card,"season-$year.jpg",ShareCards.seasonText(season,SeasonRules.hours(s.sessions,year).toLong()),"Share your season")
                    else ShareSheet.season(context,s,year)
                    sharing=false}}},
                Modifier.fillMaxWidth().height(52.dp).testTag("shareSeason"),shape=RoundedCornerShape(16.dp),enabled=!sharing,colors=ButtonDefaults.buttonColors(containerColor=BrassSoft,contentColor=Background)){
                Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Share your season",fontWeight=FontWeight.Bold)}}
        }
    }
}
