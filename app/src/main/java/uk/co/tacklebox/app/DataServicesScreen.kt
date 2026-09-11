/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.ui.*

/**
 * "Connect data services" — the iOS sub-screen with a provider card per service: a status capsule, numbered
 * steps, an Open link, a masked field and Save / Test / Clear, with the iOS messages. Secrets live in the
 * Keystore-backed store, never in the database.
 */
@Composable fun DataServicesScreen(vm:MainViewModel,nav:NavHostController){
    val secrets by vm.secrets.state.collectAsStateWithLifecycle()
    val testing by vm.testingService.collectAsStateWithLifecycle()
    val messages by vm.serviceMessages.collectAsStateWithLifecycle()
    PushedScreen("Connect data services",onBack={nav.popBackStack()}){
        item{Text("Connect optional services to unlock more of Tacklebox. Your credentials stay securely on this device.",color=Muted,style=MaterialTheme.typography.bodyMedium)}
        item{ProviderCard("WorldTides","Tide predictions outside the US",secrets.worldTidesStatus,
            listOf("Register at worldtides.info (free tier available).","Copy your API key from the dashboard.","Paste it below and tap Test."),
            "Open WorldTides","https://www.worldtides.info/register","WorldTides API key",secrets.worldTidesKey,testing==Secrets.WORLD_TIDES,messages[Secrets.WORLD_TIDES],
            save={vm.saveSecret(Secrets.WORLD_TIDES,it)},test={vm.testWorldTides()},clear={vm.clearSecret(Secrets.WORLD_TIDES)})}
        // Browser sign-in needs a registered client; without one the card would only ever say "not available".
        if(BuildConfig.INATURALIST_CLIENT_ID.isNotBlank())item{SpeciesConnectionCard(vm)}
        item{ProviderCard("iNaturalist","Identify species from a catch photo",secrets.iNaturalistStatus,
            listOf("Sign in at inaturalist.org.","Open inaturalist.org/users/api_token and copy the “api_token” value.","Paste it below and tap Test."),
            "Open iNaturalist","https://www.inaturalist.org/users/api_token","iNaturalist API token",secrets.speciesIdToken,testing==Secrets.SPECIES_ID,messages[Secrets.SPECIES_ID],
            save={vm.saveSecret(Secrets.SPECIES_ID,it)},test={vm.testINaturalist()},clear={vm.clearSecret(Secrets.SPECIES_ID)},
            note="Heads up: iNaturalist tokens last about 24 hours. When photo ID stops working, just tap Open iNaturalist, copy a fresh token and paste it here again — it only takes a moment.")}
    }
}

@Composable private fun ProviderCard(title:String,unlocks:String,status:DataServiceStatus?,steps:List<String>,openTitle:String,url:String,placeholder:String,
    stored:String,testing:Boolean,message:String?,save:(String)->Unit,test:()->Unit,clear:()->Unit,note:String?=null){
    val context=LocalContext.current
    var secret by rememberSaveable(stored){mutableStateOf(stored)}
    val colour=when(status){DataServiceStatus.CONNECTED->Teal;null->Muted;else->BrassSoft}
    HeritageCard{Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(verticalAlignment=Alignment.Top){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,style=MaterialTheme.typography.titleLarge);Text(unlocks,color=Muted,style=MaterialTheme.typography.bodyMedium)}
            Text(status?.title?:"Not connected",color=colour,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.background((if(status==null)Dim else colour).copy(alpha=.14f),CircleShape).padding(horizontal=10.dp,vertical=8.dp).testTag("status_$title"))}
        Column(verticalArrangement=Arrangement.spacedBy(9.dp)){steps.forEachIndexed{i,step->Row(verticalAlignment=Alignment.Top){
            Text("${i+1}",color=Background,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.size(24.dp).background(Brass,CircleShape).wrapContentSize(Alignment.Center));Spacer(Modifier.width(10.dp))
            Text(step,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))}}}
        TextButton({context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))},Modifier.fillMaxWidth().height(44.dp).background(Inset,RoundedCornerShape(12.dp))){Icon(Icons.Default.OpenInNew,null,tint=BrassSoft,modifier=Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(openTitle,color=BrassSoft,fontWeight=FontWeight.Bold)}
        OutlinedTextField(secret,{secret=it},placeholder={Text(placeholder)},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().testTag("secret_$title"))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button({save(secret)},Modifier.weight(1f).height(44.dp),shape=RoundedCornerShape(12.dp)){Text("Save",fontWeight=FontWeight.Bold)}
            TextButton(test,Modifier.weight(1f).height(44.dp).background(Inset,RoundedCornerShape(12.dp)),enabled=!testing){if(testing)CircularProgressIndicator(Modifier.size(16.dp),color=BrassSoft,strokeWidth=2.dp) else Text("Test",color=BrassSoft,fontWeight=FontWeight.Bold)}
            TextButton({secret="";clear()},Modifier.weight(1f).height(44.dp).background(Inset,RoundedCornerShape(12.dp))){Text("Clear",color=Muted,fontWeight=FontWeight.Bold)}}
        message?.let{Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium)}
        note?.let{Rule();Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium)}}}
}
