/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.tacklebox.app.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * `DatePicker("Caught at", …, displayedComponents: [.date, .hourAndMinute])`, bounded to now.
 *
 * iOS shows the label with a date pill and a time pill; each opens its own picker. Android had a date-only dialog
 * that committed UTC midnight, so choosing any date threw the time of day away and — because the catch was then
 * more than fifteen minutes old — silently switched off the auto-stamped conditions. This is a date pill and a time
 * pill: the date picker keeps the time the angler already had, the time picker keeps the date, and the result is
 * clamped to now so a catch can never be logged in the future.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateTimeField(label:String,millis:Long,onChange:(Long)->Unit,modifier:Modifier=Modifier,tag:String="caughtAt"){
    var pickingDate by rememberSaveable{mutableStateOf(false)}
    var pickingTime by rememberSaveable{mutableStateOf(false)}
    val zone=ZoneId.systemDefault()
    val local=Instant.ofEpochMilli(millis).atZone(zone)
    HeritageCard(modifier){
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight=44.dp),verticalAlignment=Alignment.CenterVertically){
            Text(label,Modifier.weight(1f))
            Pill(local.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),"${tag}Date"){pickingDate=true}
            Spacer(Modifier.width(8.dp))
            Pill(local.toLocalTime().hm(),"${tag}Time"){pickingTime=true}}
    }
    if(pickingDate){
        val today=Instant.now().atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state=rememberDatePickerState(initialSelectedDateMillis=CatchTiming.utcMidnightOf(millis,zone),
            selectableDates=object:SelectableDates{override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis<=today})
        DatePickerDialog(onDismissRequest={pickingDate=false},
            confirmButton={TextButton({state.selectedDateMillis?.let{onChange(CatchTiming.clampToNow(CatchTiming.combine(it,millis,zone)))};pickingDate=false}){Text("Set")}},
            dismissButton={TextButton({pickingDate=false}){Text("Cancel")}}){DatePicker(state)}
    }
    if(pickingTime){
        val state=rememberTimePickerState(initialHour=local.hour,initialMinute=local.minute)
        AlertDialog(onDismissRequest={pickingTime=false},
            text={TimePicker(state)},
            confirmButton={TextButton({onChange(CatchTiming.clampToNow(CatchTiming.withTime(millis,state.hour,state.minute,zone)));pickingTime=false}){Text("Set")}},
            dismissButton={TextButton({pickingTime=false}){Text("Cancel")}})
    }
}

@Composable private fun Pill(text:String,tag:String,onClick:()->Unit){
    Text(text,color=Ink,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium,
        modifier=Modifier.background(Inset,RoundedCornerShape(8.dp)).clickable(onClick=onClick).padding(horizontal=12.dp,vertical=8.dp).testTag(tag))
}
