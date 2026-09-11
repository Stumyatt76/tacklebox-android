/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import uk.co.tacklebox.app.ui.*

@Composable fun UnlimitedScreen(s:AppState,vm:MainViewModel,nav:androidx.navigation.NavHostController) {
    val state by vm.store.state.collectAsState()
    val activity=LocalActivity.current
    LaunchedEffect(Unit){vm.store.refresh()}
    PushedScreen(if(state.unlimited)"Unlimited unlocked" else "Keep every session",onBack={nav.popBackStack()}) {
        item { SectionLabel("Your fishing journal") }
        item { HeritageCard {
            Text("Two free sessions. All catches and features included. Unlock unlimited sessions with one purchase; no subscription.")
            Text("Your existing catches, photos and exports stay available. Deleting a session does not reset the free allowance.",color=Muted)
            if(state.includedWithPurchase) Text("Unlimited sessions are included with your original Tacklebox purchase.",color=Muted)
            if(!state.unlimited) {
                Text(SessionAllowance.label(s.settings.freeSessionsStarted))
                Button({activity?.let(vm.store::purchase)},enabled=state.price!=null && activity!=null && !state.busy && BuildConfig.PLAY_BILLING_PUBLIC_KEY.isNotBlank()) { Text(state.price?.let { "Unlock Unlimited · $it" } ?: "Store unavailable") }
            }
            OutlinedButton({vm.store.restore()},enabled=!state.busy) { Text("Restore purchases") }
            state.message?.let { Text(it,color=Muted) }
            if(state.busy)CircularProgressIndicator()
        } }
    }
}
