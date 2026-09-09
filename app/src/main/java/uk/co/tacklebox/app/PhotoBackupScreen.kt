/*
 * Copyright (c) 2026 Stuart Myatt. All rights reserved.
 * Proprietary — source is public for reference only. See LICENSE at the repository root.
 */
package uk.co.tacklebox.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import uk.co.tacklebox.app.ui.*

@Composable fun PhotoBackupScreen(s: AppState, vm: MainViewModel, nav: NavHostController) {
    val payload by vm.photoBackupPlan.collectAsStateWithLifecycle()
    val busy by vm.photoBackupBusy.collectAsStateWithLifecycle()
    val exported by vm.photoBackupExport.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var replace by remember { mutableStateOf(false) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { replace=false;vm.previewPhotoBackup(it) }
    }
    LaunchedEffect(exported) { exported?.let {
        ShareSheet.file(context,it,"application/octet-stream","Tacklebox photo backup")
        vm.photoBackupExport.value=null
    } }
    PushedScreen("Photo backup",onBack={nav.popBackStack()}) {
        item { HeritageCard {
            Text("Every catch. Every photo.",style=MaterialTheme.typography.headlineMedium)
            Text("A portable copy for either Tacklebox app, including photos, notes, waters, sessions, gear and presets.")
            Text("The file is not encrypted. Store it privately. Service credentials and purchases are not included. Maximum file size: 256 MB.",color=Muted,style=MaterialTheme.typography.bodyMedium)
            Button({vm.createPhotoBackup()},enabled=!busy) { Text("Create photo backup") }
            OutlinedButton({picker.launch(arrayOf("*/*"))},enabled=!busy) { Text("Restore a photo backup") }
        } }
        if(busy)item { Loading() }
        payload?.let { p ->
            val preview=PhotoBackupFormat.preview(p,PhotoBackup.existing(s,p))
            item { HeritageCard {
                Text("Restore preview",style=MaterialTheme.typography.headlineMedium)
                Text(preview.additions.toString()+" new records · "+preview.matching+" already present · "+preview.photoCount+" photos")
                Text("Units and discipline preferences will follow this backup. Existing records may have different edits.",color=Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Checkbox(replace,{replace=it},enabled=!busy,modifier=Modifier.semantics { contentDescription="Replace matching records" });Text("Replace matching records")
                }
                Text(if(replace)"Matching records and their photos will be replaced. Create a backup first if you want to keep their current versions."
                    else "Existing records are kept. Only new records are added.",color=Muted)
                Button({vm.restorePhotoBackup(replace)},enabled=!busy) { Text(if(replace)"Replace and restore" else "Add new records") }
                TextButton({vm.photoBackupPlan.value=null},enabled=!busy) { Text("Cancel") }
            } }
        }
    }
}
