/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import uk.co.tacklebox.app.ui.*

@Composable fun UnlimitedScreen(s:AppState,vm:MainViewModel,nav:androidx.navigation.NavHostController) {
    val state by vm.store.state.collectAsState()
    val activity=LocalActivity.current
    val context=LocalContext.current
    var recoveryCode by rememberSaveable{mutableStateOf("")}
    var redeemDialog by rememberSaveable{mutableStateOf(false)}
    var recoveryError by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){vm.store.refresh()}
    PushedScreen("Unlimited",onBack={nav.popBackStack()},eyebrow="Your fishing journal",heading=if(state.unlimited)"Unlimited unlocked" else "Keep every session") {
        item { Text("Two free sessions. All catches and features included. Unlock unlimited sessions with one purchase; no subscription.") }
        item { Text("Your existing catches, photos and exports stay available. Deleting a session does not reset the free allowance.",color=Muted,style=MaterialTheme.typography.bodyMedium) }
        if(state.includedWithPurchase) item { Text("Unlimited sessions are included with your original Tacklebox purchase.",color=Muted,style=MaterialTheme.typography.bodyMedium) }
        item { HeritageCard { Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Inset,CircleShape),contentAlignment=Alignment.Center) { Icon(if(state.unlimited)Icons.Default.Verified else Icons.Default.CalendarMonth,null,tint=Teal) }
            Spacer(Modifier.width(12.dp))
            Text(if(state.unlimited)"Unlimited sessions" else SessionAllowance.label(s.settings.freeSessionsStarted),fontWeight=FontWeight.SemiBold) } } }
        if(!state.unlimited) item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({activity?.let(vm.store::purchase)},Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),enabled=state.price!=null && activity!=null && !state.busy && BuildConfig.PLAY_BILLING_PUBLIC_KEY.isNotBlank()) { Text(state.price?.let { "Unlock Unlimited · $it" } ?: "Store unavailable",fontWeight=FontWeight.Bold) }
            if(state.introActive && state.regularPrice!=null) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("INTRODUCTORY OFFER",color=Brass,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium)
                    Text("normally ${state.regularPrice}",color=Muted,style=MaterialTheme.typography.bodyMedium,textDecoration=androidx.compose.ui.text.style.TextDecoration.LineThrough)
                }
                Text("Limited-time launch price — reverts to ${state.regularPrice} afterwards.",color=Muted,style=MaterialTheme.typography.bodyMedium)
            }
        } }
        item { TextButton({vm.store.restore()},Modifier.fillMaxWidth().height(44.dp).background(Inset,RoundedCornerShape(12.dp)),enabled=!state.busy) { Text("Restore purchases",color=BrassSoft,fontWeight=FontWeight.SemiBold) } }
        item { HeritageCard {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Original paid purchase recovery",fontWeight=FontWeight.SemiBold)
                Text("Bought Tacklebox before it became a free download? You do not need to buy it again. Contact support with your original Google Play order number. After verification, a free recovery code converts your access to Unlimited, which restores with the same Google Play account after reinstalling or changing phones.",style=MaterialTheme.typography.bodyMedium)
                TextButton({
                    recoveryError=null
                    runCatching { context.startActivity(LegacyPurchaseRecovery.supportIntent()) }
                        .onFailure { recoveryError="Email ${LegacyPurchaseRecovery.SUPPORT_EMAIL} with your original Google Play order number." }
                }) { Text("Contact purchase support",color=BrassSoft) }
                TextButton({redeemDialog=true}) { Text("Redeem recovery code",color=BrassSoft) }
                recoveryError?.let { Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium) }
            }
        } }
        // iOS offers "Refresh store" when StoreKit has not answered; here it re-queries Google Play.
        item { TextButton({vm.store.refresh()},Modifier.fillMaxWidth().height(44.dp).background(Inset,RoundedCornerShape(12.dp)),enabled=!state.busy) { Text("Refresh store",color=BrassSoft,fontWeight=FontWeight.SemiBold) } }
        state.message?.let { m-> item { Text(m,color=Muted,style=MaterialTheme.typography.bodyMedium) } }
        if(state.busy) item { CircularProgressIndicator(color=Brass) }
    }
    if(redeemDialog)AlertDialog(
        onDismissRequest={redeemDialog=false},
        title={Text("Redeem recovery code")},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Redeem the code in Google Play using the account you want to keep Unlimited on. Then return here and tap Restore purchases.")
            OutlinedTextField(recoveryCode,{recoveryCode=it},label={Text("Recovery code")},singleLine=true)
        }},
        confirmButton={TextButton({
            runCatching { context.startActivity(LegacyPurchaseRecovery.redeemIntent(recoveryCode)) }
                .onSuccess { redeemDialog=false;recoveryCode="";recoveryError=null }
                .onFailure { redeemDialog=false;recoveryError="Open Google Play, choose Payments & subscriptions, then Redeem code. Return here and tap Restore purchases." }
        },enabled=recoveryCode.isNotBlank()){Text("Open Google Play")}},
        dismissButton={TextButton({redeemDialog=false}){Text("Cancel")}},
    )
}
