/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import uk.co.tacklebox.app.ui.*

@Composable fun SpeciesConnectionCard(vm:MainViewModel) {
    val activity=LocalActivity.current
    val connection=vm.connection
    val state by connection.state.collectAsState()
    HeritageCard {
        Text("iNaturalist sign-in",style=MaterialTheme.typography.titleLarge)
        Text("Sign in securely in your browser. Tacklebox never sees your password. Photos are sent only when you request identification.",color=Muted)
        Button({activity?.let(connection::connect)},enabled=!state.busy && activity!=null) { Text(if(state.connected)"Reconnect iNaturalist" else "Connect iNaturalist") }
        if(state.connected)OutlinedButton({connection.disconnect();vm.settings(vm.state.value.settings.copy(speciesIdToken=""))}){Text("Disconnect")}
        if(state.busy)CircularProgressIndicator()
        state.message?.let { Text(it,color=Muted) }
    }
}
