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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.data.CatchRow
import uk.co.tacklebox.app.data.ConditionsSnapshot
import uk.co.tacklebox.app.data.UnitSystem
import uk.co.tacklebox.app.ui.*
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/** The numbers behind Insights and Year on the Water, computed exactly as the iOS views compute them. */
object InsightRules {
    data class Month(val start:YearMonth, val count:Int, val isLatest:Boolean)
    data class SpeciesInsight(val name:String, val count:Int, val proportion:Float)
    data class WaterInsight(val name:String, val count:Int)
    data class Pattern(val wind:String, val pressure:String, val temperature:String)

    /** The last twelve months that hold a catch, oldest first, the latest flagged so its bar draws in brass. */
    fun monthly(catches:List<CatchRow>, zone:ZoneId=ZoneId.systemDefault()):List<Month> {
        val months=catches.groupingBy { YearMonth.from(it.item.caughtAt.atZone(zone)) }.eachCount().entries.sortedBy { it.key }.takeLast(12)
        val latest=months.lastOrNull()?.key
        return months.map { Month(it.key, it.value, it.key==latest) }
    }

    /** Species by catch count, ties by name, each with its share of the biggest count. */
    fun species(catches:List<CatchRow>):List<SpeciesInsight> {
        val ranked=catches.mapNotNull { it.species?.name }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String,Int>> { it.value }.thenBy { it.key })
        val maximum=(ranked.firstOrNull()?.value ?: 1).coerceAtLeast(1)
        return ranked.map { SpeciesInsight(it.key, it.value, it.value.toFloat()/maximum) }
    }

    /** The five most-fished waters, resolved through the session where the catch has none of its own. */
    fun waters(names:List<String?>):List<WaterInsight> =
        names.filterNotNull().groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String,Int>> { it.value }.thenBy { it.key }).take(5).map { WaterInsight(it.key, it.value) }

    /** The most common non-blank value; a tie goes to the first alphabetically, as iOS resolves it. */
    fun mode(values:List<String?>):String? =
        values.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String,Int>> { it.value }.thenBy { it.key.lowercase() }).firstOrNull()?.key

    /**
     * "SW wind · Steady pressure · 12–16°C": the modal wind direction, the modal pressure trend and the busiest
     * four-degree temperature band, from at least three catches with saved conditions.
     */
    fun bestPattern(conditions:List<ConditionsSnapshot>, unit:UnitSystem):Pattern? {
        if (conditions.size<3) return null
        val wind=mode(conditions.map { it.windDirection }) ?: return null
        val pressure=mode(conditions.map { it.pressureTrend }) ?: return null
        val band=conditions.mapNotNull { it.airTempC }.groupingBy { (floor(it/4)*4).toInt() }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int,Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key ?: return null
        return Pattern(wind, pressure, temperatureBand(band, unit))
    }

    fun temperatureBand(lowerC:Int, unit:UnitSystem):String {
        val upperC=lowerC+4
        return if (unit==UnitSystem.METRIC) "$lowerC–$upperC°C"
        else "${(lowerC*9.0/5+32).roundToInt()}–${(upperC*9.0/5+32).roundToInt()}°F"
    }
}

/**
 * Insights, in the iOS order: the Year on the Water entry card, the early-days card (under three catches),
 * YOUR SEASON, CATCHES OVER TIME, SPECIES BREAKDOWN, WHERE YOU FISH and YOUR CONDITIONS.
 */
@Composable fun Insights(s:AppState,nav:NavHostController){
    val unit=s.settings.unitSystem
    val catches=s.catches
    val bests=remember(catches){CatchFilter.personalBests(catches)}
    val reviewYear=catches.maxByOrNull{it.item.caughtAt}?.item?.caughtAt?.atZone(ZoneId.systemDefault())?.year?:java.time.Year.now().value
    Screen("Patterns from the bank","Insights"){
        if(catches.isNotEmpty())item{
            Column(Modifier.fillMaxWidth().clip18().background(Brush.linearGradient(listOf(Inset,Teal.copy(alpha=.25f)))).border(1.dp,Brass.copy(alpha=.45f),RoundedCornerShape(20.dp))
                .clickable{nav.navigate("year")}.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.AutoAwesome,null,tint=BrassSoft,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp))
                    Text("YEAR ON THE WATER",color=BrassSoft,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp,modifier=Modifier.weight(1f))
                    Icon(Icons.Default.NorthEast,null,tint=BrassSoft,modifier=Modifier.size(16.dp))}
                Text("Your $reviewYear on the water is ready",style=MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text("See your year",color=Background,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.background(BrassSoft,CircleShape).padding(horizontal=16.dp,vertical=12.dp))
                    Spacer(Modifier.weight(1f));FishGlyph(Teal,Modifier.size(84.dp,50.dp))}}}
        if(catches.size<3)item{HeritageCard{Row(verticalAlignment=Alignment.CenterVertically){
            FishGlyph(Teal.copy(alpha=.8f),Modifier.size(58.dp,36.dp));Spacer(Modifier.width(14.dp))
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(if(catches.isEmpty())"Log a few catches and your patterns appear here" else "Your patterns are beginning to take shape",style=MaterialTheme.typography.titleLarge)
                Text("Every catch makes these insights more useful.",color=Muted,style=MaterialTheme.typography.bodyMedium)}}}}
        item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Your season")
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("${catches.size}","Fish landed",Modifier.weight(1f));Stat(catches.mapNotNull{it.item.weightGrams}.sum().weight(unit),"Total weight",Modifier.weight(1f))}
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("${s.sessions.size}","Sessions",Modifier.weight(1f));Stat("${catches.mapNotNull{it.species?.name}.distinct().size}","Species",Modifier.weight(1f))}}}
        if(catches.isNotEmpty()){
            item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Catches over time")
                HeritageCard{val months=InsightRules.monthly(catches)
                    if(months.size>=2)MonthlyBarChart(months)
                    else Text(if(catches.isEmpty())"Your monthly rhythm will appear after you log catches." else "Keep logging across the season to reveal your monthly rhythm.",color=Muted)}}}
            item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Species breakdown")
                HeritageCard{val rows=InsightRules.species(catches)
                    if(rows.isEmpty())Text("Species totals will appear here.",color=Muted)
                    else rows.forEachIndexed{i,row->
                        Column(Modifier.padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){
                                FishGlyph(Teal.copy(alpha=.75f),Modifier.size(42.dp,26.dp));Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                                    Text(row.name,fontWeight=FontWeight.SemiBold)
                                    Text("Best ${bests[row.name]?.item?.weightGrams?.weight(unit)?:"—"}",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                                Text("${row.count}",style=MaterialTheme.typography.titleLarge,color=BrassSoft)}
                            Box(Modifier.fillMaxWidth().height(4.dp).background(Inset,CircleShape)){Box(Modifier.fillMaxWidth(row.proportion).height(4.dp).background(Teal,CircleShape))}}
                        if(i<rows.size-1)Rule()}}}}
            item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Where you fish")
                HeritageCard{val rows=InsightRules.waters(catches.map{s.resolvedWater(it)?.name})
                    if(rows.isEmpty())Text("Assign catches to a session and water to see your favourite places.",color=Muted)
                    else rows.forEachIndexed{i,row->
                        Row(Modifier.defaultMinSize(minHeight=48.dp),verticalAlignment=Alignment.CenterVertically){
                            Text("${i+1}",color=Brass,style=MaterialTheme.typography.titleMedium,modifier=Modifier.width(24.dp))
                            Text(row.name,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                            Text("${row.count} fish",color=Muted,style=MaterialTheme.typography.bodyMedium)}
                        if(i<rows.size-1)Rule()}}}}
            item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SectionLabel("Your conditions")
                val conditioned=catches.mapNotNull{it.conditions}
                val pattern=InsightRules.bestPattern(conditioned,unit)
                if(pattern!=null)HeritageCard{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    LabelledHeading(Icons.Default.Air,"YOUR BEST PATTERN",Teal)
                    Text("You land most fish on:",color=Muted,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
                    Text("${pattern.wind} wind · ${pattern.pressure} pressure · ${pattern.temperature}",style=MaterialTheme.typography.titleLarge)
                    Text("Based on ${conditioned.size} catches with saved conditions",color=Dim,style=MaterialTheme.typography.bodySmall)}}
                else Text("Log a few more catches to reveal your best conditions.",color=Dim,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=4.dp))}}
        }
    }
}

private fun Modifier.clip18()=this.clip(RoundedCornerShape(20.dp))

/** Monthly bars — teal, the latest month in brass — with abbreviated month labels beneath. */
@Composable fun MonthlyBarChart(months:List<InsightRules.Month>){
    val density=LocalDensity.current
    val labelPx=with(density){11.sp.toPx()}
    val labelColour=Muted.toArgb()
    val top=months.maxOf{it.count}.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(170.dp).semantics{contentDescription=months.joinToString{ "${it.start.month.getDisplayName(TextStyle.SHORT,Locale.getDefault())}: ${it.count}" }}){
        val labelHeight=labelPx*1.6f
        val axisLeft=labelPx*2
        val chartHeight=size.height-labelHeight-8f
        val slot=(size.width-axisLeft)/months.size
        val bar=slot*0.6f
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textSize=labelPx;color=labelColour;textAlign=android.graphics.Paint.Align.CENTER}
        val axisPaint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{textSize=labelPx;color=labelColour;textAlign=android.graphics.Paint.Align.RIGHT}
        drawIntoCanvas{c->
            c.nativeCanvas.drawText("$top",axisLeft-6f,labelPx,axisPaint)
            c.nativeCanvas.drawText("0",axisLeft-6f,chartHeight+4f,axisPaint)}
        drawLine(Muted.copy(alpha=.2f),Offset(axisLeft,chartHeight),Offset(size.width,chartHeight),1f)
        months.forEachIndexed{i,m->
            val h=chartHeight*m.count/top
            val x=axisLeft+slot*i+(slot-bar)/2
            drawRoundRect(if(m.isLatest)Brass else Teal,Offset(x,chartHeight-h),Size(bar,h),CornerRadius(4.dp.toPx()))
            drawIntoCanvas{c->c.nativeCanvas.drawText(m.start.month.getDisplayName(TextStyle.SHORT,Locale.getDefault()),x+bar/2,size.height-4f,paint)}}
    }
}

