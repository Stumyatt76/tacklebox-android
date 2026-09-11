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
import uk.co.tacklebox.app.ui.*

@Composable fun UnlimitedScreen(s:AppState,vm:MainViewModel,nav:androidx.navigation.NavHostController) {
    val state by vm.store.state.collectAsState()
    val activity=LocalActivity.current
    LaunchedEffect(Unit){vm.store.refresh()}
    PushedScreen("Unlimited",onBack={nav.popBackStack()},eyebrow="Your fishing journal",heading=if(state.unlimited)"Unlimited unlocked" else "Keep every session") {
        item { Text("Two free sessions. All catches and features included. Unlock unlimited sessions with one purchase; no subscription.") }
        item { Text("Your existing catches, photos and exports stay available. Deleting a session does not reset the free allowance.",color=Muted,style=MaterialTheme.typography.bodyMedium) }
        if(state.includedWithPurchase) item { Text("Unlimited sessions are included with your original Tacklebox purchase.",color=Muted,style=MaterialTheme.typography.bodyMedium) }
        item { HeritageCard { Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Inset,CircleShape),contentAlignment=Alignment.Center) { Icon(if(state.unlimited)Icons.Default.Verified else Icons.Default.CalendarMonth,null,tint=Teal) }
            Spacer(Modifier.width(12.dp))
            Text(if(state.unlimited)"Unlimited sessions" else SessionAllowance.label(s.settings.freeSessionsStarted),fontWeight=FontWeight.SemiBold) } } }
        if(!state.unlimited) item { Button({activity?.let(vm.store::purchase)},Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),enabled=state.price!=null && activity!=null && !state.busy && BuildConfig.PLAY_BILLING_PUBLIC_KEY.isNotBlank()) { Text(state.price?.let { "Unlock Unlimited · $it" } ?: "Store unavailable",fontWeight=FontWeight.Bold) } }
        item { TextButton({vm.store.restore()},Modifier.fillMaxWidth().height(44.dp).background(Inset,RoundedCornerShape(12.dp)),enabled=!state.busy) { Text("Restore purchases",color=BrassSoft,fontWeight=FontWeight.SemiBold) } }
        // iOS offers "Refresh store" when StoreKit has not answered; here it re-queries Google Play.
        item { TextButton({vm.store.refresh()},Modifier.fillMaxWidth().height(44.dp).background(Inset,RoundedCornerShape(12.dp)),enabled=!state.busy) { Text("Refresh store",color=BrassSoft,fontWeight=FontWeight.SemiBold) } }
        state.message?.let { m-> item { Text(m,color=Muted,style=MaterialTheme.typography.bodyMedium) } }
        if(state.busy) item { CircularProgressIndicator(color=Brass) }
    }
}
